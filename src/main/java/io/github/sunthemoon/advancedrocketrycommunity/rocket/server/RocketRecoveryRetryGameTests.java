package io.github.sunthemoon.advancedrocketrycommunity.rocket.server;

import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import io.github.sunthemoon.advancedrocketrycommunity.gametest.GameTestTickPacer;
import io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.CheckedSavedDataFile;
import io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.ManagedSavedDataType;
import io.github.sunthemoon.advancedrocketrycommunity.registry.ModEntities;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.entity.RocketEntity;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.*;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.persistence.RocketJournalGameTestSupport;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.persistence.RocketTransferSavedData;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.*;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.stats.RocketStats;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.ChunkPos;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/** Real spawn vetoes against isolated checked journal files; no replacement of the world's journal. */
@GameTestHolder(ModIdentity.MOD_ID)
@PrefixGameTestTemplate(false)
public final class RocketRecoveryRetryGameTests {
    private RocketRecoveryRetryGameTests() { }

    @GameTest(template = "rocket_test", batch = "transfer_spawn_retry", timeoutTicks = 80)
    public static void rejectedSpawnsReuseAcknowledgedIdentityAcrossReloads(GameTestHelper helper) {
        whenLoaded(helper, () -> {
            for (var phase : RocketTransferPhase.values()) {
                if (phase.destinationAuthoritative()) { rejectedSpawns(helper, phase); }
            }
        });
    }

    private static void rejectedSpawns(GameTestHelper helper, RocketTransferPhase phase) {
        var prepared = record(helper);
        UUID identity = UUID.randomUUID();
        var record = prepared.destinationSpawned(identity);
        while (record.phase() != phase) {
            record = record.advance(RocketTransferPhase.values()[record.phase().ordinal() + 1]);
        }
        var expected = record;
        var rejecting = new AtomicBoolean(true);
        var rejected = new AtomicInteger();
        var accepted = new AtomicInteger();
        Consumer<EntityJoinLevelEvent> veto = event -> {
            if (event.getEntity() instanceof RocketEntity rocket
                    && rocket.assemblyTransactionId().filter(expected.logicalRocketId()::equals).isPresent()) {
                helper.assertTrue(rocket.getUUID().equals(identity), "Recovery generated a different UUID");
                if (rejecting.get()) { rejected.incrementAndGet(); event.setCanceled(true); }
                else { accepted.incrementAndGet(); }
            }
        };
        MinecraftForge.EVENT_BUS.addListener(EventPriority.LOWEST, false, EntityJoinLevelEvent.class, veto);
        try (var file = new JournalFile(helper, record)) {
            // Fresh services and a checked file reload remove every session-only retry hint.
            for (int session = 0; session < 2; session++) {
                var recovery = recovery();
                long sequence = file.journal.mutationSequence();
                long writes = file.writes();
                for (int attempt = 0; attempt < 5; attempt++) {
                    var result = recovery.recoverNext(helper.getLevel().getServer(), file.journal);
                    helper.assertTrue(result.status() == RocketTransferRecoveryService.Status.RETRY_LATER,
                            "Rejected spawn did not remain retryable");
                    helper.assertTrue(recovery.takeCompleted(record.transferId()) == null,
                            "Rejected spawn reported completion");
                    file.assertUnchanged(record, sequence, writes);
                    helper.assertTrue(RocketTransferEntities.loadedMatches(helper.getLevel().getServer(), record, true).isEmpty(),
                            "Rejected spawn published a destination");
                }
                if (session == 0) { file.reload(); }
            }
            helper.assertTrue(rejected.get() == 10 && accepted.get() == 0,
                    "The ten real spawn rejections were not reached");
            rejecting.set(false);
            var recovery = recovery();
            long sequence = file.journal.mutationSequence();
            long writes = file.writes();
            recovery.recoverNext(helper.getLevel().getServer(), file.journal);
            var completed = recovery.takeCompleted(record.transferId());
            helper.assertTrue(completed != null && completed.status() == RocketTransferRecoveryService.Status.RECOVERED,
                    "Later spawn success did not complete");
            helper.assertTrue(completed.phase() == phase && accepted.get() == 1,
                    "Successful retry changed phase or spawned more than once");
            file.assertUnchanged(record, sequence, writes);
            for (int attempt = 0; attempt < 3; attempt++) {
                recovery.recoverNext(helper.getLevel().getServer(), file.journal);
                helper.assertTrue(recovery.takeCompleted(record.transferId()) == null,
                        "A completed session repeated the completion");
            }
            var destination = onlyDestination(helper, record, identity);
            helper.assertTrue(destination.flightData().orElseThrow().fuel().equals(record.destinationFlightData().fuel()),
                    "Retry debited fuel again");

            // Reconstruct both native entity NBT and the checked journal, then elect the saved identity.
            var entityTag = new CompoundTag();
            helper.assertTrue(destination.save(entityTag), "Destination did not serialize");
            destination.discard();
            file.reload();
            var restored = ModEntities.ROCKET.get().create(helper.getLevel());
            helper.assertTrue(restored != null, "Destination type unavailable");
            restored.load(entityTag);
            helper.assertTrue(helper.getLevel().addFreshEntity(restored), "Saved destination did not reload");
            int joinsBeforeRecovery = accepted.get();
            sequence = file.journal.mutationSequence();
            writes = file.writes();
            recovery = recovery();
            recovery.recoverNext(helper.getLevel().getServer(), file.journal);
            helper.assertTrue(recovery.takeCompleted(record.transferId()) != null,
                    "Reloaded destination was not recovered");
            file.assertUnchanged(record, sequence, writes);
            helper.assertTrue(onlyDestination(helper, record, identity) == restored
                    && accepted.get() == joinsBeforeRecovery, "Reload recovery duplicated the destination");
        } finally {
            MinecraftForge.EVENT_BUS.unregister(veto);
            RocketTransferEntities.loadedMatches(helper.getLevel().getServer(), record, true).forEach(RocketEntity::discard);
        }
    }

    @GameTest(template = "rocket_test", batch = "transfer_spawn_retry", timeoutTicks = 80)
    public static void changedBindingStillWaitsForCheckedAcknowledgement(GameTestHelper helper) {
        whenLoaded(helper, () -> {
            for (boolean prepared : List.of(true, false)) {
                var record = record(helper);
                if (!prepared) { record = record.destinationSpawned(UUID.randomUUID()); }
                try (var file = new JournalFile(helper, record)) {
                    var destination = RocketTransferEntities.createDestination(helper.getLevel().getServer(), record);
                    helper.assertTrue(destination != null && helper.getLevel().addFreshEntity(destination), "No adopted destination");
                    try {
                        var recovery = recovery();
                        long sequence = file.journal.mutationSequence();
                        recovery.recoverNext(helper.getLevel().getServer(), file.journal);
                        helper.assertTrue(!file.journal.acknowledgedCurrent()
                                && recovery.takeCompleted(record.transferId()) == null,
                                "Changed binding completed before acknowledgement");
                        helper.assertTrue(file.journal.mutationSequence() == sequence + 1,
                                "Changed binding did not queue exactly one mutation");
                        helper.assertTrue(file.writes() == 2, "Changed binding did not write exactly once after baseline");
                        helper.assertTrue(recovery.takeCompleted(record.transferId()) != null,
                                "Acknowledged adoption did not complete");
                        var rebound = file.journal.find(record.transferId()).orElseThrow();
                        helper.assertTrue(rebound.destinationEntityId().orElseThrow().equals(destination.getUUID())
                                && rebound.phase() == RocketTransferPhase.DESTINATION_SPAWNED,
                                "Adoption bound the wrong phase or identity");
                    } finally { destination.discard(); }
                }
            }
        });
    }

    private static RocketTransferRecoveryService recovery() {
        return new RocketTransferRecoveryService(new HashSet<>(), new HashSet<>());
    }

    private static RocketEntity onlyDestination(GameTestHelper helper, RocketTransferRecord record, UUID identity) {
        var matches = RocketTransferEntities.loadedMatches(helper.getLevel().getServer(), record, true);
        helper.assertTrue(matches.size() == 1 && matches.get(0).getUUID().equals(identity), "Destination is not unique");
        return matches.get(0);
    }

    private static void whenLoaded(GameTestHelper helper, Runnable body) {
        var pacer = new GameTestTickPacer(helper.getLevel().getServer());
        helper.startSequence().thenWaitUntil(() -> {
            for (var relative : List.of(new BlockPos(3, 2, 3), new BlockPos(11, 2, 3))) {
                var at = helper.absolutePos(relative);
                boolean loaded = helper.getLevel().areEntitiesLoaded(ChunkPos.asLong(at.getX() >> 4, at.getZ() >> 4));
                if (!loaded) { pacer.pace(); }
                helper.assertTrue(loaded, "Fixture entity section is not loaded");
            }
        }).thenExecute(body).thenSucceed();
    }

    private static RocketTransferRecord record(GameTestHelper helper) {
        var from = helper.absolutePos(new BlockPos(3, 2, 3));
        var to = helper.absolutePos(new BlockPos(11, 2, 3));
        var origin = new RocketPosition(from.getX(), from.getY(), from.getZ());
        var target = new RocketPosition(to.getX(), to.getY(), to.getZ());
        var dimension = helper.getLevel().dimension().location();
        var blocks = List.of(new RocketBlock(new RocketPosition(0, 0, 0),
                new RocketBlockState(net.minecraft.resources.ResourceLocation.tryParse("minecraft:iron_block"), Map.of())));
        var stats = new RocketStats(1, 100, 1_000, 1_000, 1, 1, 1, 0);
        var source = RocketStructureSnapshot.create(UUID.randomUUID(), dimension, origin, blocks,
                List.of(new RocketPosition(0, 0, 0)), stats, 0);
        var destination = source.relocated(UUID.randomUUID(), dimension, target, 160);
        UUID transfer = UUID.randomUUID(), logical = UUID.randomUUID();
        var fuel = RocketFuelState.empty(1_000).fill(1_000).state();
        var plan = new RocketFlightPlan(3, transfer, RocketFlightPlanner.EARTH.bodyId(),
                RocketFlightPlanner.MOON.bodyId(), dimension, dimension, 500, 0);
        var flight = RocketFlightData.initial(logical, 1_000, 1, RocketFlightPlanner.EARTH.bodyId(), dimension, origin, 0)
                .withFuel(fuel, 0).withPlan(plan).startCountdown(0).completeCountdown(60).beginTransit(transfer, 140);
        var arrival = flight.arriveAtDestination(fuel.debit(plan).state(), RocketFlightPlanner.MOON.bodyId(), dimension, target, 160);
        return RocketTransferRecord.create(transfer, logical, UUID.randomUUID(), UUID.randomUUID(),
                source, destination, flight, arrival, 500, 0);
    }

    private static final class JournalFile implements AutoCloseable {
        final GameTestHelper helper;
        final Path directory;
        final Path file;
        RocketTransferSavedData journal;

        JournalFile(GameTestHelper helper, RocketTransferRecord record) {
            this.helper = helper;
            try { directory = Files.createTempDirectory("arce-w77-retry-"); }
            catch (IOException failure) { throw new java.io.UncheckedIOException(failure); }
            file = directory.resolve(ManagedSavedDataType.ROCKET_TRANSFERS.fileName());
            journal = new RocketTransferSavedData(); journal.put(record);
            RocketJournalGameTestSupport.confirmDetached(helper, journal, file);
        }

        long writes() { return RocketJournalGameTestSupport.drainDetachedWriteCount(helper, journal); }

        void assertUnchanged(RocketTransferRecord record, long sequence, long writes) {
            helper.assertTrue(journal.mutationSequence() == sequence && journal.acknowledgedCurrent(),
                    "Spawn retry dirtied or advanced the journal");
            helper.assertTrue(writes() == writes, "Spawn retry performed another checked write");
            var saved = journal.find(record.transferId()).orElseThrow();
            helper.assertTrue(saved.phase() == record.phase() && saved.destinationEntityId().equals(record.destinationEntityId()),
                    "Spawn retry changed the acknowledged phase or destination UUID");
            var disk = RocketTransferSavedData.load(CheckedSavedDataFile.readPayload(file,
                    ManagedSavedDataType.ROCKET_TRANSFERS).orElseThrow()).find(record.transferId()).orElseThrow();
            helper.assertTrue(disk.phase() == record.phase() && disk.destinationEntityId().equals(record.destinationEntityId()),
                    "Disk authority changed during rejected spawn");
        }

        void reload() {
            journal.stop();
            journal = RocketTransferSavedData.load(CheckedSavedDataFile.readPayload(file,
                    ManagedSavedDataType.ROCKET_TRANSFERS).orElseThrow());
            RocketJournalGameTestSupport.confirmDetached(helper, journal, file);
            helper.assertTrue(writes() == 1, "Reload did not perform exactly one startup baseline write");
        }

        @Override public void close() {
            journal.stop();
            try { Files.deleteIfExists(file); Files.delete(directory); }
            catch (IOException failure) { throw new java.io.UncheckedIOException(failure); }
        }
    }
}
