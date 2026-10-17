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
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketLegacyTransferFixture;
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
import io.github.sunthemoon.advancedrocketrycommunity.rocket.server.RocketLandedDisassemblyRules.Stage;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.stats.RocketStats;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.transaction.RocketDisassemblyTransaction;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.transaction.RocketFailureInjector;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.transaction.RocketFailurePoint;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.transaction.RocketOperationLedger;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.transaction.RocketRegion;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.transaction.RocketRegionLockManager;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.transaction.RocketTransactionAbortException;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.transaction.RocketTransactionJournal;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.transaction.RocketTransactionResult;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.transaction.RocketTransactionWorld;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.transaction.RocketWorldBlock;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.validation.RocketValidationCode;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.GovernedTransferFixture;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
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
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

/**
 * W85 section 7 A0: P's stages and the removal and write-back continuations through a detached journal, with the
 * real v0.5 transaction on a fake world. The latch is unset before {@code execute} and set from then on.
 */
@Timeout(60)
class RocketLandedDisassembliesTest {
    private static final ManagedSavedDataType TYPE = ManagedSavedDataType.ROCKET_TRANSFERS;
    private static final RocketPosition EARTH_ORIGIN = new RocketPosition(12, 72, 12);
    @TempDir Path directory;
    private long tick;

    @BeforeAll static void bootstrap() { MinecraftBootstrap.initialize(); }

    private Path file() { return directory.resolve(TYPE.fileName()); }

    @Test void successRestoresOnceAfterTheRemovalIsAcknowledgedAndNeverWritesBack() {
        var f = new Fixture(new RocketPosition(64, 80, 64));
        assertEquals(RocketValidationCode.DISASSEMBLY_PENDING, f.release());
        assertTrue(f.journal.find(f.landed.transferId()).isEmpty() && f.landed.equals(f.held()));
        assertEquals(1, f.disassemblies.heldSlots(f.journal));
        assertEquals(0, f.request.restores, "No block before the removal's acknowledgement");
        f.acknowledge();
        assertEquals(1, f.request.restores);
        assertEquals(List.of("SUCCESS"), f.request.reports);
        assertEquals(List.of("released"), f.port.events);
        assertFalse(f.disassemblies.pending(f.logical()));
        assertTrue(f.journal.find(f.landed.transferId()).isEmpty());
        assertTrue(f.world.rockets.isEmpty() && f.world.blocks.size() == f.snapshot().blocks().size());
        assertTrue(f.diskRecords().isEmpty());
        f.close();
    }

    @Test void cleanFailuresWriteTheIdenticalRecordBackAndHoldEverythingUntilItIsAcknowledged() {
        for (int mode = 0; mode < 3; mode++) {
            var f = new Fixture(new RocketPosition(64 + mode * 64, 80, 64));
            if (mode == 0) {
                f.world.blocks.put(f.absolute(1), new RocketWorldBlock(state("minecraft:stone"), null)); // 0 of 0
            } else if (mode == 1) {
                f.request.injector = (point, progress) -> {
                    if (point == RocketFailurePoint.DURING_RESTORATION && progress == 2) {
                        throw new RocketTransactionAbortException(RocketValidationCode.EXTRACTION_FAILED, "n of n");
                    }
                };
            } else {
                f.port.now = 10_000; // M2 runs after the confirmation lifetime: no restoration at all.
            }
            assertEquals(RocketValidationCode.DISASSEMBLY_PENDING, f.release());
            if (mode == 2) { f.port.now = 10_000 + RocketDisassemblyConfirmations.LIFETIME_TICKS + 1; }
            f.acknowledge();
            var pending = f.disassemblies.find(f.logical());
            assertNotNull(pending, "P keeps the rocket locked until the write-back is acknowledged");
            assertEquals(Stage.WRITE_BACK, pending.stage());
            assertSame(f.landed, f.journal.find(f.landed.transferId()).orElseThrow(), "The identical record");
            assertEquals(0, f.disassemblies.heldSlots(f.journal), "Not counted a second time in WRITE_BACK");
            assertTrue(f.disassemblies.reservations(f.journal).isEmpty());
            assertEquals(List.of(mode == 0 ? "TARGET_OCCUPIED" : mode == 1 ? "EXTRACTION_FAILED" : "REGION_BUSY"),
                    f.request.reports);
            assertEquals(mode == 2 ? 0 : 1, f.request.restores);
            assertTrue(f.world.rockets.containsKey(f.entity), "E survives a clean failure");
            assertTrue(f.journal.resourceMutationUnproved(f.logical()));
            f.acknowledge();
            assertFalse(f.disassemblies.pending(f.logical()));
            assertEquals(List.of("released", "restored settle=true"), f.port.events);
            assertEquals(List.of(f.landed.transferId()), f.diskRecords());
            assertFalse(f.journal.resourceMutationUnproved(f.logical()));
            f.close();
        }
    }

    @Test void aPendingTransactionRecoveryAtTheAcknowledgementWritesBackWithoutRestoring() {
        // W92-R2: a v0.5 record overlapping E (or a v0.5 journal that is not operational) appears between M0 and M2.
        for (int mode = 0; mode < 2; mode++) {
            var f = new Fixture(new RocketPosition(64 + mode * 64, 80, 64));
            assertEquals(RocketValidationCode.DISASSEMBLY_PENDING, f.release());
            if (mode == 0) { f.request.restorable = false; }
            if (mode == 1) { f.request.restorableThrows = new IllegalStateException("v0.5 journal unavailable"); }
            f.acknowledge();
            assertEquals(0, f.request.restores, "M2 restored over a pending v0.5 recovery");
            assertEquals(List.of("REGION_BUSY"), f.request.reports);
            assertEquals(Stage.WRITE_BACK, f.disassemblies.find(f.logical()).stage());
            assertSame(f.landed, f.journal.find(f.landed.transferId()).orElseThrow(), "The identical record");
            assertTrue(f.world.rockets.containsKey(f.entity) && f.world.blocks.isEmpty());
            f.acknowledge();
            assertFalse(f.disassemblies.pending(f.logical()));
            assertEquals(List.of("released", "restored settle=true"), f.port.events);
            assertEquals(List.of(f.landed.transferId()), f.diskRecords());
            f.close();
        }
    }

    @Test void aSettledSchemaOneRecordIsDisassembledInPlaceAndWrittenBackUnchanged() throws IOException {
        // W92-R4: the clause admits the schema-1 record; the governed record 3 stays refused.
        assertFalse(RocketLandedDisassemblyRules.landedRecordAdmitted(GovernedTransferFixture.create().record()
                .schemaVersion(), true));
        var clean = new Fixture(new RocketPosition(64, 80, 64), RocketLegacyTransferFixture::schemaOne);
        assertEquals(1, clean.landed.schemaVersion());
        assertTrue(RocketLandedDisassemblyRules.landedRecordAdmitted(clean.landed.schemaVersion(),
                clean.landed.nativeTransfer().isPresent()));
        CompoundTag before = clean.diskRecord();
        clean.world.blocks.put(clean.absolute(1), new RocketWorldBlock(state("minecraft:stone"), null)); // 0 of 0
        assertEquals(RocketValidationCode.DISASSEMBLY_PENDING, clean.release());
        clean.acknowledge();
        assertSame(clean.landed, clean.journal.find(clean.landed.transferId()).orElseThrow(), "The identical record");
        clean.acknowledge();
        assertFalse(clean.disassemblies.pending(clean.logical()));
        assertEquals(before, clean.diskRecord(), "The write-back is the byte-identical schema-1 record");
        assertEquals(1, before.getInt("schema_version"));
        clean.close();

        var success = new Fixture(new RocketPosition(128, 80, 64), RocketLegacyTransferFixture::schemaOne);
        assertEquals(RocketValidationCode.DISASSEMBLY_PENDING, success.release());
        success.acknowledge();
        assertEquals(List.of("SUCCESS"), success.request.reports);
        assertTrue(success.world.rockets.isEmpty() && success.diskRecords().isEmpty());
        success.close();
    }

    @Test void uncertainMutationNeverWritesTheRecordBack() {
        for (int mode = 0; mode < 3; mode++) {
            var f = new Fixture(new RocketPosition(64 + mode * 64, 80, 64));
            if (mode == 0) {
                f.world.failRemoveAt = f.absolute(0); // ROLLBACK_FAILED with unequal counts
                f.request.injector = (point, progress) -> {
                    if (point == RocketFailurePoint.DURING_RESTORATION && progress == 2) {
                        throw new RocketTransactionAbortException(RocketValidationCode.EXTRACTION_FAILED, "partial");
                    }
                };
            } else if (mode == 1) {
                f.request.restoreThrows = new IllegalStateException("thrown after the latch");
            } else {
                f.request.notifierThrows = true; // SUCCESS, then the notification throws
            }
            f.release();
            f.acknowledge();
            assertFalse(f.disassemblies.pending(f.logical()));
            assertTrue(f.journal.find(f.landed.transferId()).isEmpty(), "T never reappears after the latch");
            assertTrue(f.diskRecords().isEmpty());
            assertEquals(List.of(mode == 0 ? "ROLLBACK_FAILED" : mode == 1 ? "ROLLBACK_FAILED" : "SUCCESS"),
                    f.request.reports);
            f.close();
        }
    }

    @Test void theRemovalContinuationsFailureModesWithTheLatchUnsetAndSet() {
        // Unset: a refused action, a RuntimeException and an Error before execute all write T back.
        for (int mode = 0; mode < 3; mode++) {
            var f = new Fixture(new RocketPosition(64 + mode * 64, 80, 64));
            if (mode == 0) { f.port.wrap = action -> () -> false; }
            if (mode == 1) { f.request.matchesThrows = new IllegalStateException("before the latch"); }
            if (mode == 2) { f.request.matchesError = new AssertionError("Error before the latch"); }
            f.release();
            if (mode == 2) { assertThrows(AssertionError.class, f::acknowledge); } else { f.acknowledge(); }
            assertEquals(0, f.request.restores);
            assertSame(f.landed, f.journal.find(f.landed.transferId()).orElseThrow());
            assertEquals(Stage.WRITE_BACK, f.disassemblies.find(f.logical()).stage());
            f.request.matchesError = null;
            f.acknowledge();
            assertFalse(f.disassemblies.pending(f.logical()));
            assertEquals(List.of(f.landed.transferId()), f.diskRecords());
            f.close();
        }
        // Set: a refused action after a success, and an Error after the first placement, never write T back.
        for (int mode = 0; mode < 2; mode++) {
            var f = new Fixture(new RocketPosition(320 + mode * 64, 80, 64));
            if (mode == 0) { f.port.wrap = action -> () -> { action.getAsBoolean(); return false; }; }
            if (mode == 1) { f.world.errorAfterFirstPlacement = new AssertionError("Error after a placement"); }
            f.release();
            if (mode == 1) { assertThrows(AssertionError.class, f::acknowledge); } else { f.acknowledge(); }
            assertEquals(1, f.request.restores);
            assertFalse(f.disassemblies.pending(f.logical()));
            assertTrue(f.journal.find(f.landed.transferId()).isEmpty(), "T never reappears in RAM");
            f.world.errorAfterFirstPlacement = null;
            f.flushAndPoll();
            assertTrue(f.diskRecords().isEmpty(), "T never reappears in a capture");
            if (mode == 1) { assertEquals(1, f.world.blocks.size(), "The Error left a placed block behind"); }
            f.close();
        }
    }

    @Test void aLaterAcknowledgedGenerationDiscardsTheRemovalContinuationIntoAWriteBack() {
        var f = new Fixture(new RocketPosition(64, 80, 64));
        f.release();
        var failing = new CheckedSavedDataPolicy(TYPE, (staged, target) -> { throw new IOException("Injected removal failure"); });
        RocketJournalTestAccess.write(f.journal, file(), failing, tick += 1_000);
        assertEquals(RocketTransferSavedData.WriteOutcome.NOT_COMMITTED, f.journal.checkedWriteResult().outcome());
        f.disassemblies.reportUnacknowledged();
        f.disassemblies.reportUnacknowledged();
        var other = landed(new RocketPosition(2_000, 80, 2_000));
        f.journal.put(other); // A disjoint successor generation.
        f.acknowledge();
        assertEquals(0, f.request.restores, "The discarded removal never restores");
        assertSame(f.landed, f.journal.find(f.landed.transferId()).orElseThrow());
        assertEquals(List.of("REGION_BUSY"), f.request.reports);
        f.acknowledge();
        assertFalse(f.disassemblies.pending(f.logical()));
        assertEquals(2, f.diskRecords().size());
        f.close();
    }

    @Test void stopAndAbortCarryTheRecordBackInRamAndCleanupIsIdempotent() throws IOException {
        for (boolean abort : List.of(false, true)) {
            var f = new Fixture(new RocketPosition(64, 80, 64));
            f.release(); // The removal's generation is captured by neither a write nor a poll before the stop.
            if (abort) { f.journal.abort(); } else { f.journal.stop(); }
            assertFalse(f.disassemblies.pending(f.logical()));
            assertSame(f.landed, f.journal.find(f.landed.transferId()).orElseThrow());
            if (!abort) { assertEquals(List.of(f.landed.transferId()), f.diskRecords(), "T rides the final capture"); }
            f.journal.stop();
            f.disassemblies.clear();
            assertEquals(List.of(), f.port.events);
            Files.deleteIfExists(file());
        }
    }

    @Test void refusedInsertsEndPendingWithoutTheRecord() {
        for (int mode = 0; mode < 3; mode++) {
            var f = new Fixture(new RocketPosition(64 + mode * 64, 80, 64));
            f.world.blocks.put(f.absolute(1), new RocketWorldBlock(state("minecraft:stone"), null));
            f.release();
            if (mode == 0) { f.request.presence = Presence.CHANGED; }
            if (mode == 1) { f.journal.put(landed(f.landed.destinationSnapshot().sourceOrigin())); }
            if (mode == 2) { f.request.presence = Presence.UNLOADED; }
            f.acknowledge();
            if (mode == 2) {
                assertSame(f.landed, f.journal.find(f.landed.transferId()).orElseThrow(), "An unloaded E gets T back");
            } else {
                assertFalse(f.disassemblies.pending(f.logical()));
                assertTrue(f.journal.find(f.landed.transferId()).isEmpty(), "mismatch and overlap are refused");
            }
            f.close();
        }
    }

    @Test void pendingIsBoundedAndCountsOnlyWhileTheRecordIsAbsent() {
        var journal = new RocketTransferSavedData();
        var disassemblies = new RocketLandedDisassemblies();
        var port = new FakePort(journal);
        port.detached = true; // Continuations are not run here; only P's bound and its inputs are.
        var records = new ArrayList<RocketTransferRecord>();
        for (int index = 0; index < RocketLandedDisassemblies.MAX_PENDING; index++) {
            var record = landed(new RocketPosition(100 + 40 * index, 80, 8));
            journal.put(record); records.add(record);
        }
        for (var record : records) {
            assertEquals(RocketValidationCode.DISASSEMBLY_PENDING, disassemblies.release(journal, record,
                    record.destinationEntityId().orElseThrow(), new FakeRequest(new FakeWorld(), record), port));
            if (disassemblies.size() == 1) {
                assertEquals(RocketValidationCode.REGION_BUSY, disassemblies.release(journal, record,
                        record.destinationEntityId().orElseThrow(), new FakeRequest(new FakeWorld(), record), port),
                        "One P per logical rocket");
            }
        }
        assertTrue(journal.entries().isEmpty());
        assertFalse(disassemblies.hasCapacity());
        assertEquals(RocketLandedDisassemblies.MAX_PENDING, disassemblies.size());
        assertEquals(RocketLandedDisassemblies.MAX_PENDING, disassemblies.heldSlots(journal));
        assertEquals(records.stream().map(record -> RocketRegion.fromSnapshot(record.destinationSnapshot())).toList(),
                disassemblies.reservations(journal));
        var extra = landed(new RocketPosition(9_000, 80, 8));
        assertEquals(RocketValidationCode.REGION_BUSY, disassemblies.release(journal, extra,
                extra.destinationEntityId().orElseThrow(), new FakeRequest(new FakeWorld(), extra), port));
        disassemblies.clear();
        assertEquals(0, disassemblies.size());
        assertEquals(0, disassemblies.heldSlots(journal));
        journal.stop();
    }

    private final class Fixture {
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
        RocketValidationCode release() { return disassemblies.release(journal, landed, entity, request, port); }

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

    private static final class FakePort implements RocketLandedDisassemblies.Port {
        final RocketTransferSavedData journal;
        final List<String> events = new ArrayList<>();
        long now = 0;
        boolean bay = true;
        boolean detached;
        UnaryOperator<BooleanSupplier> wrap = UnaryOperator.identity();

        FakePort(RocketTransferSavedData journal) { this.journal = journal; }
        @Override public long now() { return now; }
        @Override public boolean bayAllowsRemoval(RocketTransferRecord record) { return bay; }
        @Override public boolean queue(UUID logical, BooleanSupplier action, Runnable discarded) {
            return detached || RocketJournalTestAccess.queue(journal, logical, wrap.apply(action), discarded);
        }
        @Override public void released(RocketTransferRecord record, UUID entity) { events.add("released"); }
        @Override public void restored(RocketTransferRecord record, UUID entity, boolean settle) {
            events.add("restored settle=" + settle);
        }
        @Override public void probe(String boundary, RocketTransferRecord record) { }
    }

    private static final class FakeRequest implements RocketLandedDisassemblies.Request {
        final FakeWorld world;
        final RocketTransferRecord record;
        final List<String> reports = new ArrayList<>();
        Presence presence = Presence.MATCHES;
        RocketFailureInjector injector = RocketFailureInjector.NONE;
        RuntimeException matchesThrows;
        Error matchesError;
        RuntimeException restoreThrows;
        boolean notifierThrows;
        boolean restorable = true;
        RuntimeException restorableThrows;
        int restores;

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
        @Override public void finished(RocketValidationCode code, RocketTransactionResult result) {
            reports.add(code.name());
            if (notifierThrows && code == RocketValidationCode.SUCCESS) {
                throw new IllegalStateException("Injected notifier failure after SUCCESS");
            }
        }
    }

    private static final class FakeWorld implements RocketTransactionWorld {
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

    private static RocketBlockState state(String id) { return new RocketBlockState(ResourceLocation.tryParse(id), Map.of()); }

    /** A COMMITTED schema-2 landed record T for a three-block rocket at {@code arrival} on the Moon. */
    private static RocketTransferRecord landed(RocketPosition arrival) {
        UUID transfer = UUID.randomUUID();
        UUID logical = UUID.randomUUID();
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
