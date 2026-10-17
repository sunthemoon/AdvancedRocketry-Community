package io.github.sunthemoon.advancedrocketrycommunity.rocket.server;

import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import io.github.sunthemoon.advancedrocketrycommunity.gametest.GameTestTickPacer;
import io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.CheckedSavedDataFile;
import io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.ManagedSavedDataType;
import io.github.sunthemoon.advancedrocketrycommunity.registry.ModEntities;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.entity.RocketEntity;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.*;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.persistence.RocketJournalGameTestSupport;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.persistence.RocketJournalSchedulingGameTestSupport;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.persistence.RocketTransferSavedData;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.*;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.stats.RocketStats;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/** Real ordinary-flight service visits against one detached checked writer, at the production queue bound. */
@GameTestHolder(ModIdentity.MOD_ID)
@PrefixGameTestTemplate(false)
public final class RocketConcurrentFlightGameTests {
    private RocketConcurrentFlightGameTests() { }

    @GameTest(template = "rocket_test", batch = "journal_concurrent_flight", timeoutTicks = 600)
    public static void pendingSpawnDoesNotPauseAnotherCountdownAscentOrTransit(GameTestHelper helper) {
        new Scenario(helper, true).start();
    }

    @GameTest(template = "rocket_test", batch = "journal_concurrent_flight", timeoutTicks = 600)
    public static void twoActionableFlightsAdvanceAndPublishInTheSameTick(GameTestHelper helper) {
        new Scenario(helper, false).start();
    }

    private static final class Scenario {
        final GameTestHelper helper;
        final boolean delayed;
        final RocketTransferService service = new RocketTransferService();
        final RocketTransferSavedData journal = new RocketTransferSavedData();
        final GameTestTickPacer pacer;
        final RocketEntity[] sources = new RocketEntity[2];
        final RocketTransferRecord[] initial = new RocketTransferRecord[2];
        final int[] journalSteps = new int[2];
        final boolean[] landed = new boolean[2];
        RocketJournalSchedulingGameTestSupport.HeldWrite held;
        Path directory, file;
        int stage, waits, risingTicks, simultaneousSteps;
        boolean released, closed, sawCountdown, sawAscent, sawTransit, awaitSpawnPair;

        Scenario(GameTestHelper helper, boolean delayed) {
            this.helper = helper; this.delayed = delayed; pacer = new GameTestTickPacer(helper.getLevel().getServer());
        }

        void start() { helper.onEachTick(this::tick); }

        void tick() {
            if (closed) { return; }
            try {
                helper.assertTrue(++waits < 550, "Concurrent-flight fixture exceeded its bounded wait");
                if (stage == 0) { setup(); }
                else if (stage == 1) {
                    if (held.entered()) {
                        service.spawnDestination(helper.getLevel().getServer(), journal, initial[0], sources[0]);
                        helper.assertTrue(journal.find(initial[0].transferId()).orElseThrow().phase()
                                == RocketTransferPhase.DESTINATION_SPAWNED, "First flight did not queue its spawn");
                        journalSteps[0]++; stage = 2;
                    }
                } else { fly(); }
                if (!closed) { pacer.pace(); }
            } catch (IOException failure) { close(); throw new java.io.UncheckedIOException(failure); }
            catch (RuntimeException | Error failure) { close(); throw failure; }
        }

        void setup() throws IOException {
            var level = helper.getLevel();
            for (int i = 0; i < 2; i++) {
                for (var pos : List.of(origin(i, false), origin(i, true))) {
                    if (!level.isPositionEntityTicking(pos)) { return; }
                }
            }
            directory = Files.createTempDirectory("arce-concurrent-flight-");
            file = directory.resolve(ManagedSavedDataType.ROCKET_TRANSFERS.fileName());
            long now = level.getGameTime();
            for (int i = 0; i < 2; i++) {
                var source = ModEntities.ROCKET.get().create(level); helper.assertTrue(source != null, "No source entity");
                sources[i] = source;
                var propulsion = new RocketPropulsion(RocketPropulsion.Mode.ABSTRACT, 1_000, 0, 1, 1,
                        RocketPropulsion.Settings.DEFAULTS);
                var snapshot = RocketStructureSnapshot.create(UUID.randomUUID(), level.dimension().location(), position(origin(i, false)),
                        List.of(new RocketBlock(new RocketPosition(0, 0, 0), new RocketBlockState(
                                ResourceLocation.tryParse("minecraft:iron_block"), Map.of()))),
                        List.of(new RocketPosition(0, 0, 0)), new RocketStats(1, 100, 1_000, 1_000, 1, 1, 1, 0), now, propulsion);
                var destination = snapshot.relocated(UUID.randomUUID(), level.dimension().location(), position(origin(i, true)), now);
                UUID logical = UUID.randomUUID(), transfer = UUID.randomUUID(), owner = UUID.randomUUID();
                var plan = new RocketFlightPlan(3, transfer, RocketFlightPlanner.EARTH.bodyId(), RocketFlightPlanner.MOON.bodyId(),
                        level.dimension().location(), level.dimension().location(), 500, now);
                var fuel = RocketFuelState.empty(propulsion).fill(1_000).state();
                var countdown = RocketFlightData.initial(logical, 1_000, 1, plan.sourceBody(), plan.sourceDimension(),
                        snapshot.sourceOrigin(), now).withFuel(fuel, now).withPlan(plan).startCountdown(now);
                var transit = countdown.completeCountdown(now).beginTransit(transfer, now);
                var arrival = transit.arriveAtDestination(fuel.debit(plan).state(), plan.destinationBody(),
                        plan.destinationDimension(), destination.sourceOrigin(), now);
                initial[i] = RocketTransferRecord.create(transfer, logical, owner, source.getUUID(), snapshot, destination,
                        transit, arrival, 500, now);
                source.initializeTransferred(snapshot, logical, owner, delayed && i == 0 ? transit : countdown);
                RocketTransferEntities.positionAtOrigin(source, snapshot.sourceOrigin());
                helper.assertTrue(level.addFreshEntity(source), "Source publication failed");
                journal.put(initial[i]); RocketFailBackAdmissionGameTests.live(service).add(transfer);
            }
            RocketJournalGameTestSupport.confirmDetached(helper, journal, file);
            if (delayed) {
                // Hold the active checked snapshot; A's real spawn queues behind it, then B coalesces behind A.
                journal.setDirty(); held = RocketJournalSchedulingGameTestSupport.hold(helper, journal, file); stage = 1;
            } else { released = true; stage = 2; }
        }

        void fly() {
            var server = helper.getLevel().getServer(); journal.pollWriter(server.getTickCount());
            if (awaitSpawnPair) {
                // Establish both acknowledged spawn prerequisites before the explicit same-tick service visit.
                journal.retryChecked(server);
                if (java.util.Arrays.stream(initial).anyMatch(record -> !journal.actionable(record.logicalRocketId()))) { return; }
                awaitSpawnPair = false;
            }
            int stepped = 0;
            for (int i = 0; i < 2; i++) {
                var before = journal.find(initial[i].transferId()).orElseThrow();
                var sourceFlight = sources[i].flightData().orElseThrow(); double y = sources[i].getY();
                if (!landed[i]) { service.tickLive(server, journal, before); }
                var after = journal.find(before.transferId()).orElseThrow();
                if (after.phase() != before.phase()) { journalSteps[i]++; stepped++; }
                if (i == 1 && !released) {
                    helper.assertTrue(journal.logicalAcknowledged(initial[1].logicalRocketId())
                                    || after.phase() == RocketTransferPhase.DESTINATION_SPAWNED,
                            "Disjoint held write revoked B's acknowledged source");
                    var state = sources[i].flightData().orElseThrow().state();
                    sawCountdown |= state == RocketFlightState.COUNTDOWN;
                    sawAscent |= state == RocketFlightState.ASCENT; sawTransit |= state == RocketFlightState.TRANSIT;
                    if (sourceFlight.state() == RocketFlightState.ASCENT
                            && sourceFlight.stateStartedGameTime() < helper.getLevel().getGameTime()) {
                        helper.assertTrue(sources[i].getY() > y, "Actionable ascent did not rise in this tick"); risingTicks++;
                    }
                }
                if (after.phase() == RocketTransferPhase.COMMITTED) {
                    var matches = RocketTransferEntities.loadedMatches(server, after, true);
                    helper.assertTrue(matches.size() == 1, "Committed flight lost or duplicated its destination");
                    landed[i] = matches.get(0).flightData().orElseThrow().state() == RocketFlightState.LANDED;
                }
            }
            if (stepped == 2) { simultaneousSteps++; }
            if (!released) {
                for (var record : journal.entries()) {
                    helper.assertTrue(record.destinationEntityId().map(helper.getLevel()::getEntity).isEmpty(),
                            "Unacknowledged spawn published an entity");
                }
                if (journal.find(initial[1].transferId()).orElseThrow().phase() == RocketTransferPhase.DESTINATION_SPAWNED) {
                    helper.assertTrue(sawCountdown && sawAscent && sawTransit && risingTicks >= RocketFlightLimits.ASCENT_TICKS - 1,
                            "Held writer did not cover B's entire countdown/ascent/transit");
                    helper.assertTrue(journal.outstandingEffectCount() == 2, "Both spawn continuations were not retained");
                    held.close(); released = true; awaitSpawnPair = true;
                }
            }
            if (landed[0] && landed[1]) { verifyAndFinish(); }
            // The bounded writer coalesces B after A's successor; request the newest RAM snapshot as the real tick does.
            if (released) { journal.retryChecked(server); }
        }

        void verifyAndFinish() {
            helper.assertTrue(simultaneousSteps > 0, "No tick advanced both actionable records");
            helper.assertTrue(journalSteps[0] == 4 && journalSteps[1] == 4, "A flight skipped or repeated a journal phase");
            var loaded = RocketTransferSavedData.load(CheckedSavedDataFile.readPayload(file,
                    ManagedSavedDataType.ROCKET_TRANSFERS).orElseThrow());
            try {
                helper.assertTrue(loaded.entries().size() == 2, "Checked reload did not retain both flights");
                for (var original : initial) {
                    var record = loaded.find(original.transferId()).orElseThrow();
                    var matches = RocketTransferEntities.loadedMatches(helper.getLevel().getServer(), record, true);
                    helper.assertTrue(record.phase() == RocketTransferPhase.COMMITTED && matches.size() == 1
                            && RocketTransferEntities.loadedMatches(helper.getLevel().getServer(), record, false).isEmpty()
                            && matches.get(0).flightData().orElseThrow().fuel().amount() == 500,
                            "Checked reload changed committed identity, entity count or fuel debit");
                }
            } finally { loaded.abort(); }
            close(); helper.succeed();
        }

        BlockPos origin(int index, boolean destination) {
            return helper.absolutePos(new BlockPos(destination ? 14 : 3, 2, 3 + index * 5));
        }
        static RocketPosition position(BlockPos pos) { return new RocketPosition(pos.getX(), pos.getY(), pos.getZ()); }
        void close() {
            if (closed) { return; } closed = true; if (held != null) { held.close(); }
            journal.abort(); service.clear();
            for (var record : journal.entries()) {
                record.destinationEntityId().map(helper.getLevel()::getEntity).ifPresent(net.minecraft.world.entity.Entity::discard);
                RocketTransferEntities.releaseFlightTickets(helper.getLevel().getServer(), record);
            }
            for (var source : sources) { if (source != null) { source.discard(); } }
            if (directory != null) {
                try (var paths = Files.walk(directory)) {
                    for (var path : paths.sorted(java.util.Comparator.reverseOrder()).toList()) { Files.delete(path); }
                } catch (IOException failure) { throw new java.io.UncheckedIOException(failure); }
            }
        }
    }
}
