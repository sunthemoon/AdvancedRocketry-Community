package io.github.sunthemoon.advancedrocketrycommunity.rocket.server;

import static org.junit.jupiter.api.Assertions.*;

import io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.CheckedSavedDataFile;
import io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.CheckedSavedDataPolicy;
import io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.ManagedSavedDataType;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFlightData;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFlightLimits;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFlightPlan;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFlightPlanner;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFuelState;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketPassengerManifest;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketTransferPhase;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketTransferRecord;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.persistence.RocketJournalTestAccess;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.persistence.RocketTransferSavedData;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketBlock;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketBlockState;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketPosition;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketStructureSnapshot;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.server.RocketLandedDisassemblyRules.Presence;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.stats.RocketStats;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.sealed.SealedDisassembly;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.transaction.RocketDisassemblyTransaction;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.transaction.RocketFailureInjector;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.transaction.RocketOperationLedger;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.transaction.RocketRegion;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.transaction.RocketRegionLockManager;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.transaction.RocketTransactionJournal;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.transaction.RocketTransactionResult;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.transaction.RocketTransactionWorld;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.transaction.RocketWorldBlock;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.validation.RocketValidationCode;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import java.util.function.UnaryOperator;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.io.TempDir;

/** Per-test detached journal and fake transaction world shared by landed-disassembly tests. */
abstract class RocketLandedDisassemblyTestSupport {
    static final ManagedSavedDataType TYPE = ManagedSavedDataType.ROCKET_TRANSFERS;
    private static final RocketPosition EARTH_ORIGIN = new RocketPosition(12, 72, 12);
    @TempDir Path directory;
    long tick;

    Path file() { return directory.resolve(TYPE.fileName()); }

    final class Fixture {
        final RocketTransferSavedData journal = new RocketTransferSavedData();
        final RocketLandedDisassemblies disassemblies = new RocketLandedDisassemblies();
        final FakePort port = new FakePort(journal);
        final RocketTransferRecord landed;
        final UUID entity;
        final FakeWorld world = new FakeWorld();
        final FakeRequest request;

        Fixture(RocketPosition arrival) {
            this(arrival, UnaryOperator.identity());
        }

        Fixture(RocketPosition arrival, UnaryOperator<RocketTransferRecord> form) {
            landed = form.apply(landed(arrival));
            entity = landed.destinationEntityId().orElseThrow();
            world.rockets.put(entity, snapshot().snapshotId());
            world.hash = snapshot().contentHash();
            request = new FakeRequest(world, landed);
            journal.put(landed);
            RocketJournalTestAccess.write(journal, file(), CheckedSavedDataPolicy.atomic(TYPE), tick += 1);
            assertTrue(journal.acknowledgedCurrent());
        }

        RocketStructureSnapshot snapshot() { return landed.destinationSnapshot(); }
        UUID logical() { return landed.logicalRocketId(); }
        RocketPosition absolute(int index) { return snapshot().sourceOrigin().add(snapshot().blocks().get(index).position()); }
        RocketValidationCode release() { return RocketLandedDisassemblyTestSupport.release(disassemblies, journal, landed, entity, request, port); }

        RocketTransferRecord held() {
            return disassemblies.reservations(journal).isEmpty() ? null : landed;
        }

        void acknowledge() {
            RocketJournalTestAccess.write(journal, file(), CheckedSavedDataPolicy.atomic(TYPE), tick += 1_000);
        }

        void flushAndPoll() {
            if (!journal.acknowledgedCurrent()) { journal.setDirty(); acknowledge(); }
        }

        List<UUID> diskRecords() {
            var payload = CheckedSavedDataFile.readPayload(file(), TYPE).orElseThrow();
            return RocketTransferSavedData.load(payload).entries().stream().map(RocketTransferRecord::transferId).toList();
        }

        /** T's encoded record on disk, or an empty tag. */
        CompoundTag diskRecord() {
            for (var raw : CheckedSavedDataFile.readPayload(file(), TYPE).orElseThrow().getList("transfers", 10)) {
                var tag = (CompoundTag) raw;
                if (tag.getUUID("transfer_id").equals(landed.transferId())) { return tag.copy(); }
            }
            return new CompoundTag();
        }

        void close() {
            journal.stop();
            try { Files.deleteIfExists(file()); } catch (IOException exception) { throw new IllegalStateException(exception); }
        }
    }

    static final class FakePort implements RocketLandedDisassemblies.Port {
        final RocketTransferSavedData journal;
        final List<String> events = new ArrayList<>();
        long now = 0;
        boolean bay = true;
        boolean detached;
        UnaryOperator<BooleanSupplier> wrap = UnaryOperator.identity();

        FakePort(RocketTransferSavedData journal) { this.journal = journal; }
        @Override public long now() { return now; }
        @Override public boolean bayAllowsRemoval(RocketTransferRecord record) { return bay; }
        @Override public boolean queue(RocketTransferSavedData.Reservation reservation, BooleanSupplier action,
                                       Runnable discarded) {
            return detached || RocketJournalTestAccess.queue(journal, reservation, wrap.apply(action), discarded);
        }
        @Override public boolean queueRemoval(RocketTransferSavedData.Reservation reservation, BooleanSupplier action,
                                              Runnable discarded) {
            return detached || RocketJournalTestAccess.queue(journal, reservation, true, wrap.apply(action), discarded);
        }
        @Override public void released(RocketTransferRecord record, UUID entity) { events.add("released"); }
        @Override public void restored(RocketTransferRecord record, UUID entity, boolean settle) {
            events.add("restored settle=" + settle);
        }
        @Override public void probe(String boundary, RocketTransferRecord record) { }
    }

    static final class FakeRequest implements RocketLandedDisassemblies.Request {
        final FakeWorld world;
        final RocketTransferRecord record;
        final List<String> reports = new ArrayList<>();
        Presence presence = Presence.MATCHES;
        RocketFailureInjector injector = RocketFailureInjector.NONE;
        RuntimeException matchesThrows;
        Error matchesError;
        RuntimeException restoreThrows;
        boolean notifierThrows;
        RuntimeException notifierFailure;
        boolean restorable = true;
        RuntimeException restorableThrows;
        int restores;
        boolean deferred, certified = true;
        long requestedAt = Long.MIN_VALUE;
        java.util.function.Consumer<RocketTransactionResult> completion;
        boolean retained, acceptRetained = true;
        java.util.function.Consumer<SealedDisassembly.Result> retainedCompletion;
        public boolean retainedExecutor() { return retained; }
        public boolean beginRetained(java.util.function.Consumer<SealedDisassembly.Result> completion) {
            if (!acceptRetained) { return false; }
            retainedCompletion = completion; return true;
        }
        public void finishedRetained(SealedDisassembly.Result result) { reports.add(result.name()); }

        FakeRequest(FakeWorld world, RocketTransferRecord record) { this.world = world; this.record = record; }

        @Override public boolean matches() {
            if (matchesError != null) { throw matchesError; }
            if (matchesThrows != null) { throw matchesThrows; }
            var snapshot = record.destinationSnapshot();
            return world.rocketMatches(record.destinationEntityId().orElseThrow(), snapshot.snapshotId(), snapshot.contentHash());
        }
        @Override public boolean restorable() {
            if (restorableThrows != null) { throw restorableThrows; }
            return restorable;
        }
        @Override public Presence presence(RocketTransferRecord ignored) { return presence; }
        @Override public RocketTransactionResult restore() {
            restores++;
            if (restoreThrows != null) { throw restoreThrows; }
            return new RocketDisassemblyTransaction(world, new RocketRegionLockManager(), new RocketOperationLedger(),
                    RocketTransactionJournal.NO_OP, injector).execute(UUID.randomUUID(),
                    record.destinationEntityId().orElseThrow(), record.destinationSnapshot());
        }
        // This overload intentionally compiles against the old synchronous contract for the red run.
        public void restore(java.util.function.Consumer<RocketTransactionResult> completion) {
            if (deferred) { this.completion = completion; } else { completion.accept(restore()); }
        }
        public boolean rollbackCertified() { return certified; }
        public boolean requiresNativeRollbackProof() { return true; }
        public long requestedAt() { return requestedAt; }
        @Override public void finished(RocketValidationCode code, RocketTransactionResult result) {
            reports.add(code.name());
            if (notifierThrows && code == RocketValidationCode.SUCCESS) {
                throw new IllegalStateException("Injected notifier failure after SUCCESS");
            }
            if (notifierFailure != null) { throw notifierFailure; }
        }
    }

    static final class FakeWorld implements RocketTransactionWorld {
        final Map<RocketPosition, RocketWorldBlock> blocks = new HashMap<>();
        final Map<UUID, UUID> rockets = new HashMap<>();
        String hash;
        RocketPosition failRemoveAt;
        Error errorAfterFirstPlacement;

        @Override public ResourceLocation dimension() { return RocketFlightPlanner.MOON.dimensionId(); }
        @Override public boolean isRegionLoaded(RocketRegion region) { return true; }
        @Override public Optional<RocketWorldBlock> readBlock(RocketPosition position) { return Optional.ofNullable(blocks.get(position)); }
        @Override public boolean removeBlockNoDrops(RocketPosition position, RocketWorldBlock expected) {
            if (position.equals(failRemoveAt)) { return false; }
            return blocks.remove(position) != null;
        }
        @Override public boolean placeBlockIfEmpty(RocketPosition position, RocketWorldBlock block) {
            if (errorAfterFirstPlacement != null && blocks.size() == 1) { throw errorAfterFirstPlacement; }
            if (blocks.containsKey(position)) { return false; }
            blocks.put(position, block);
            return true;
        }
        @Override public Optional<UUID> spawnRocket(RocketStructureSnapshot snapshot, UUID transactionId) { return Optional.empty(); }
        @Override public boolean rocketMatches(UUID rocketId, UUID snapshotId, String contentHash) {
            return snapshotId.equals(rockets.get(rocketId)) && contentHash.equals(hash);
        }
        @Override public boolean removeRocket(UUID rocketId, UUID snapshotId) { return rockets.remove(rocketId, snapshotId); }
    }

    /** M1 as {@code begin} calls it, with L's own {@code canQueueEffect(L)} reservation held through the call (R23). */
    static RocketValidationCode release(RocketLandedDisassemblies disassemblies, RocketTransferSavedData journal,
                                                RocketTransferRecord record, UUID entity,
                                                RocketLandedDisassemblies.Request request, RocketLandedDisassemblies.Port port) {
        try (var reservation = journal.canQueueEffect(record.logicalRocketId()).orElse(null)) {
            return disassemblies.release(journal, record, entity, request, port, reservation);
        }
    }

    static RocketBlockState state(String id) { return new RocketBlockState(ResourceLocation.tryParse(id), Map.of()); }

    /** A COMMITTED schema-2 landed record T for a three-block rocket at {@code arrival} on the Moon. */
    static RocketTransferRecord landed(RocketPosition arrival) {
        return landed(arrival, UUID.randomUUID());
    }

    /** {@link #landed(RocketPosition)} for the given logical rocket. */
    static RocketTransferRecord landed(RocketPosition arrival, UUID logical) {
        UUID transfer = UUID.randomUUID();
        List<RocketBlock> blocks = List.of(new RocketBlock(new RocketPosition(0, 0, 0), state("minecraft:iron_block")),
                new RocketBlock(new RocketPosition(0, 1, 0), state("minecraft:iron_block")),
                new RocketBlock(new RocketPosition(0, 2, 0), state("minecraft:iron_block")));
        RocketStats stats = new RocketStats(blocks.size(), 200L, 1_000L, 1_000L, 1, 1, 1, 0);
        RocketStructureSnapshot source = RocketStructureSnapshot.create(UUID.randomUUID(),
                RocketFlightPlanner.EARTH.dimensionId(), EARTH_ORIGIN, blocks, List.of(new RocketPosition(0, 1, 0)),
                stats, 0L);
        RocketStructureSnapshot destination = source.relocated(UUID.randomUUID(), RocketFlightPlanner.MOON.dimensionId(),
                arrival, 160L);
        RocketFuelState fuel = RocketFuelState.empty(1_000L).fill(1_000L).state();
        RocketFlightPlan plan = RocketFlightPlanner.plan(stats, fuel, RocketFlightPlanner.EARTH, RocketFlightPlanner.MOON,
                transfer, 0L).plan();
        RocketFlightData sourceFlight = RocketFlightData.initial(logical, stats.fuelCapacity(), stats.seatCount(),
                        RocketFlightPlanner.EARTH.bodyId(), RocketFlightPlanner.EARTH.dimensionId(), EARTH_ORIGIN, 0L)
                .withFuel(fuel, 0L).withPassengers(RocketPassengerManifest.empty(1)).withPlan(plan).startCountdown(0L)
                .completeCountdown(RocketFlightLimits.COUNTDOWN_TICKS)
                .beginTransit(transfer, RocketFlightLimits.COUNTDOWN_TICKS + RocketFlightLimits.ASCENT_TICKS);
        RocketFlightData destinationFlight = sourceFlight.arriveAtDestination(
                sourceFlight.fuel().debit(transfer, plan.requiredFuel()).state(), RocketFlightPlanner.MOON.bodyId(),
                RocketFlightPlanner.MOON.dimensionId(), arrival, RocketFlightLimits.COUNTDOWN_TICKS
                        + RocketFlightLimits.ASCENT_TICKS + RocketFlightLimits.TRANSIT_TICKS);
        return RocketTransferRecord.create(transfer, logical, UUID.randomUUID(), UUID.randomUUID(), source,
                        destination, sourceFlight, destinationFlight, plan.requiredFuel(), 0L)
                .destinationSpawned(UUID.randomUUID()).advance(RocketTransferPhase.PASSENGERS_TRANSFERRED)
                .advance(RocketTransferPhase.SOURCE_REMOVED).advance(RocketTransferPhase.COMMITTED);
    }
}
