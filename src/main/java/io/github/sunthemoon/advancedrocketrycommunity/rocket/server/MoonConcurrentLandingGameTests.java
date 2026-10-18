package io.github.sunthemoon.advancedrocketrycommunity.rocket.server;

import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.CelestialIds;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.service.SafeCelestialTravel;
import io.github.sunthemoon.advancedrocketrycommunity.gametest.DetachedFirstEventRoots;
import io.github.sunthemoon.advancedrocketrycommunity.gametest.FirstEventGameTestPlayers;
import io.github.sunthemoon.advancedrocketrycommunity.gametest.GameTestTickPacer;
import io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.ManagedSavedDataType;
import io.github.sunthemoon.advancedrocketrycommunity.progression.classic.ClassicMilestoneAdvancements.Milestone;
import io.github.sunthemoon.advancedrocketrycommunity.progression.classic.firstevent.FirstEvent;
import io.github.sunthemoon.advancedrocketrycommunity.registry.ModEntities;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.entity.RocketEntity;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.*;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.persistence.RocketJournalSchedulingGameTestSupport;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.*;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.stats.RocketStats;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.level.ChunkPos;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/** W86 G5: RAM election is not authority for a second lander's NOT_FIRST publication. */
@GameTestHolder(ModIdentity.MOD_ID)
@PrefixGameTestTemplate(false)
public final class MoonConcurrentLandingGameTests {
    private MoonConcurrentLandingGameTests() { }

    @GameTest(template = "empty", batch = "journal_concurrent_flight", timeoutTicks = 250)
    public static void aSecondMoonLanderWaitsForTheFirstElectionAcknowledgement(GameTestHelper helper) {
        new Scenario(helper).start();
    }

    @GameTest(template = "empty", batch = "journal_concurrent_flight", timeoutTicks = 450)
    public static void definiteFailureRefusesMoonElectionUntilRetry(GameTestHelper helper) {
        new Scenario(helper, true).start();
    }

    private static final class Scenario {
        final GameTestHelper helper;
        final net.minecraft.server.MinecraftServer server;
        final net.minecraft.server.level.ServerLevel moon;
        final GameTestTickPacer pacer;
        final BlockPos origin;
        final ChunkPos ticket;
        final boolean definiteFailure;
        final RocketEntity[] rockets = new RocketEntity[2];
        final RocketTransferRecord[] records = new RocketTransferRecord[2];
        final ServerPlayer[] players = new ServerPlayer[2];
        DetachedFirstEventRoots roots;
        RocketDestinationCompletion completion;
        RocketJournalSchedulingGameTestSupport.HeldWrite held;
        RocketJournalSchedulingGameTestSupport.FailedWrite fault;
        FirstEvent.MoonLanding event;
        int step, waits, heldVisits;
        boolean closed;

        Scenario(GameTestHelper helper) { this(helper, false); }
        Scenario(GameTestHelper helper, boolean definiteFailure) {
            this.helper = helper; server = helper.getLevel().getServer(); moon = server.getLevel(CelestialIds.MOON_LEVEL);
            this.definiteFailure = definiteFailure;
            origin = SafeCelestialTravel.FIXED_FEET_POSITION.offset(definiteFailure ? 640 : 512, 20, 512);
            ticket = new ChunkPos(origin);
            pacer = new GameTestTickPacer(server);
        }

        void start() {
            helper.assertTrue(moon != null, "Moon Level is unavailable");
            moon.getChunkSource().addRegionTicket(TicketType.FORCED, ticket, 2, ticket);
            helper.onEachTick(this::tick);
        }

        void tick() {
            if (closed) { return; }
            try {
                helper.assertTrue(++waits < (definiteFailure ? 420 : 220), "Moon concurrency fixture exceeded its bounded wait");
                if (step == 0) {
                    if (moon.getGameTime() >= RocketFlightLimits.DESCENT_TICKS
                            && moon.isPositionEntityTicking(origin) && moon.isPositionEntityTicking(origin.south(5))) { setup(); }
                } else if (step == 4) {
                    if (fault.entered()) { fault.releaseFailure(); }
                    roots.journal.pollWriter(server.getTickCount());
                    if (roots.journal.checkedWriteResult().outcome()
                            != io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.persistence.RocketTransferSavedData.WriteOutcome.NOT_COMMITTED) { return; }
                    completion.tickDestination(server, roots.journal, records[0]);
                    helper.assertTrue(roots.journal.actionable(records[0].logicalRocketId())
                            && roots.journal.canQueueEffect(records[0].logicalRocketId()).isEmpty()
                            && roots.journal.firstMoonLandingRam().isEmpty()
                            && rockets[0].flightData().orElseThrow().state() == RocketFlightState.DESCENT
                            && !done(players[0], Milestone.MOON_LANDING), "Definite failure admitted a Moon election or landing");
                    fault.recover(); step = 5;
                } else if (step == 5) {
                    roots.journal.pollWriter(server.getTickCount());
                    if (!roots.journal.clean()) { pacer.pace(); return; }
                    completion.tickDestination(server, roots.journal, records[0]);
                    roots.confirmJournal();
                    helper.assertTrue(rockets[0].flightData().orElseThrow().state() == RocketFlightState.LANDED
                            && done(players[0], Milestone.MOON_LANDING), "Retried Moon election did not publish its landing");
                    close(); helper.succeed();
                } else if (step == 1) {
                    if (held.entered()) {
                        completion.tickDestination(server, roots.journal, records[0]);
                        helper.assertTrue(roots.journal.firstMoonLandingRam().event() instanceof FirstEvent.MoonLanding,
                                "First landing did not elect");
                        event = (FirstEvent.MoonLanding) roots.journal.firstMoonLandingRam().event();
                        helper.assertTrue(event.transferId().equals(records[0].transferId())
                                && event.participants().equals(List.of(players[0].getUUID())), "Election named the wrong arrival");
                        step = 2;
                    }
                } else if (step == 2) {
                    roots.journal.pollWriter(server.getTickCount());
                    helper.assertTrue(roots.journal.actionable(records[1].logicalRocketId()),
                            "Second lander's record is not independently actionable");
                    completion.tickDestination(server, roots.journal, records[1]);
                    for (int i = 0; i < 2; i++) {
                        helper.assertTrue(rockets[i].flightData().orElseThrow().state() == RocketFlightState.DESCENT
                                        && !done(players[i], Milestone.MOON_LANDING),
                                "Landing or advancement preceded the election acknowledgement");
                        helper.assertTrue(rockets[i].getY() == records[i].destinationSnapshot().sourceOrigin().y(),
                                "Waiting lander did not retain its terminal pose");
                    }
                    helper.assertTrue(roots.journal.durableFirstMoonLanding().knownEvent().isEmpty()
                            && roots.journal.firstMoonLandingRam().event().equals(event), "Held election became durable or changed");
                    if (++heldVisits == 5) { held.close(); step = 3; }
                } else {
                    roots.journal.pollWriter(server.getTickCount());
                    if (!roots.journal.clean()) { pacer.pace(); return; }
                    helper.assertTrue(rockets[0].flightData().orElseThrow().state() == RocketFlightState.LANDED
                            && rockets[1].flightData().orElseThrow().state() == RocketFlightState.DESCENT,
                            "The election continuation published the wrong set of landings");
                    completion.tickDestination(server, roots.journal, records[1]);
                    helper.assertTrue(rockets[1].flightData().orElseThrow().state() == RocketFlightState.LANDED
                            && roots.journal.firstMoonLandingRam().event().equals(event), "Second landing re-elected or failed to land");
                    roots.delivery.tick();
                    helper.assertTrue(done(players[0], Milestone.MOON_LANDING) && done(players[1], Milestone.MOON_LANDING)
                            && done(players[0], Milestone.ONE_SMALL_STEP) && !done(players[1], Milestone.ONE_SMALL_STEP),
                            "First and later landing awards were not kept separate");
                    close(); helper.succeed();
                }
                if (!closed) { pacer.pace(); }
            } catch (RuntimeException | Error failure) { close(); throw failure; }
        }

        void setup() {
            long now = moon.getGameTime(); roots = DetachedFirstEventRoots.fromWorld(helper);
            for (int i = 0; i < 2; i++) {
                var pos = origin.south(i * 5);
                players[i] = FirstEventGameTestPlayers.join(server, UUID.randomUUID(), "MoonWait" + i, moon, pos.above(2));
                var propulsion = new RocketPropulsion(RocketPropulsion.Mode.ABSTRACT, 1_000, 0, 1, 1, RocketPropulsion.Settings.DEFAULTS);
                var snapshot = RocketStructureSnapshot.create(UUID.randomUUID(), RocketFlightPlanner.EARTH.dimensionId(),
                        new RocketPosition(i * 64, 90, 0), List.of(new RocketBlock(new RocketPosition(0, 0, 0),
                                new RocketBlockState(ResourceLocation.tryParse("minecraft:iron_block"), Map.of()))),
                        List.of(new RocketPosition(0, 0, 0)), new RocketStats(1, 100, 1_000, 1_000, 1, 1, 1, 0), now, propulsion);
                var destination = snapshot.relocated(UUID.randomUUID(), RocketFlightPlanner.MOON.dimensionId(),
                        new RocketPosition(pos.getX(), pos.getY(), pos.getZ()), now);
                UUID logical = UUID.randomUUID(), transfer = UUID.randomUUID(), owner = UUID.randomUUID();
                var plan = new RocketFlightPlan(3, transfer, RocketFlightPlanner.EARTH.bodyId(), RocketFlightPlanner.MOON.bodyId(),
                        RocketFlightPlanner.EARTH.dimensionId(), RocketFlightPlanner.MOON.dimensionId(), 500, now);
                var fuel = RocketFuelState.empty(propulsion).fill(1_000).state();
                var flight = RocketFlightData.initial(logical, 1_000, 1, plan.sourceBody(), plan.sourceDimension(), snapshot.sourceOrigin(), now)
                        .withFuel(fuel, now);
                flight = flight.withPassengers(flight.passengers().assign(players[i].getUUID()).orElseThrow()).withPlan(plan)
                        .startCountdown(now).completeCountdown(now).beginTransit(transfer, now);
                var arrival = flight.arriveAtDestination(fuel.debit(plan).state(), plan.destinationBody(), plan.destinationDimension(),
                        destination.sourceOrigin(), now - RocketFlightLimits.DESCENT_TICKS);
                rockets[i] = ModEntities.ROCKET.get().create(moon); helper.assertTrue(rockets[i] != null, "No Moon arrival");
                records[i] = RocketTransferRecord.create(transfer, logical, owner, UUID.randomUUID(), snapshot, destination,
                        flight, arrival, 500, now).destinationSpawned(rockets[i].getUUID()).advance(RocketTransferPhase.PASSENGERS_TRANSFERRED)
                        .advance(RocketTransferPhase.SOURCE_REMOVED).advance(RocketTransferPhase.COMMITTED);
                rockets[i].initializeTransferred(destination, logical, owner, arrival);
                RocketTransferEntities.positionAtAltitude(rockets[i], destination.sourceOrigin());
                helper.assertTrue(moon.addFreshEntity(rockets[i]) && players[i].startRiding(rockets[i], true), "Moon boarding failed");
                roots.journal.put(records[i]);
            }
            roots.confirmJournal();
            var recovery = new RocketTransferRecoveryService(new HashSet<>(), new HashSet<>());
            completion = new RocketDestinationCompletion(new HashSet<>(), new HashSet<>(), recovery.bays(), roots.dispositions());
            roots.journal.setDirty();
            if (definiteFailure) {
                roots.journal.put(RocketFailBackAdmissionGameTests.record(helper, UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                        null, helper.absolutePos(new BlockPos(3, 2, 3)), 256));
                fault = RocketJournalSchedulingGameTestSupport.fail(helper, roots.journal, roots.file(ManagedSavedDataType.ROCKET_TRANSFERS), false);
                step = 4;
            } else {
                held = RocketJournalSchedulingGameTestSupport.hold(helper, roots.journal, roots.file(ManagedSavedDataType.ROCKET_TRANSFERS)); step = 1;
            }
        }

        boolean done(ServerPlayer player, Milestone milestone) {
            var advancement = server.getAdvancements().getAdvancement(ResourceLocation.tryParse(milestone.id()));
            return advancement != null && player.getAdvancements().getOrStartProgress(advancement).isDone();
        }
        void close() {
            if (closed) { return; } closed = true; if (held != null) { held.close(); }
            if (fault != null) { fault.close(); }
            for (var player : players) { if (player != null) { player.stopRiding(); server.getPlayerList().remove(player); } }
            for (var rocket : rockets) { if (rocket != null) { rocket.discard(); } }
            if (roots != null) { roots.close(); }
            moon.getChunkSource().removeRegionTicket(TicketType.FORCED, ticket, 2, ticket);
        }
    }
}
