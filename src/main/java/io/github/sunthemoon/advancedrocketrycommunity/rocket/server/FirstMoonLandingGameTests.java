package io.github.sunthemoon.advancedrocketrycommunity.rocket.server;

import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.CelestialIds;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.service.SafeCelestialTravel;
import io.github.sunthemoon.advancedrocketrycommunity.gametest.DetachedFirstEventRoots;
import io.github.sunthemoon.advancedrocketrycommunity.gametest.GameTestTickDriver;
import io.github.sunthemoon.advancedrocketrycommunity.progression.classic.ClassicMilestoneAdvancements.Milestone;
import io.github.sunthemoon.advancedrocketrycommunity.progression.classic.firstevent.FirstEvent;
import io.github.sunthemoon.advancedrocketrycommunity.progression.classic.firstevent.FirstEventKind;
import io.github.sunthemoon.advancedrocketrycommunity.progression.classic.firstevent.FirstEventOutbox;
import io.github.sunthemoon.advancedrocketrycommunity.registry.ModEntities;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.entity.RocketEntity;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFlightData;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFlightLimits;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFlightPlan;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFlightPlanner;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFlightState;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFuelState;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketTransferPhase;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketTransferRecord;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketBlock;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketBlockState;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketPosition;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketPropulsion;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketStructureSnapshot;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.stats.RocketStats;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/**
 * W44 A1: the isolated world-first Moon landing. A real destination rocket descends in the Moon Level with a real
 * connected passenger, and the real descent-complete step elects in a detached journal (W44 section 2.3): no LANDED and
 * no award before the exact writer confirmation, LANDED after it, the world-first only for the aboard participant after
 * the durable receipt, never for a watcher or a later joiner, and DELIVERED with the same event.
 */
@GameTestHolder(ModIdentity.MOD_ID)
@PrefixGameTestTemplate(false)
public final class FirstMoonLandingGameTests {
    private FirstMoonLandingGameTests() {
    }

    @GameTest(template = "empty", batch = "classic_first_event", timeoutTicks = 200)
    public static void theFirstMoonLandingElectsTheAboardPassengerAndLandsOnlyAfterTheConfirmation(GameTestHelper helper) {
        new Landing(helper).start();
    }

    private static final class Landing {
        final GameTestHelper helper;
        final GameTestTickDriver driver;
        final MinecraftServer server;
        final ServerLevel moon;
        final List<ServerPlayer> joined = new ArrayList<>();
        final RocketTransferRecoveryService recovery = new RocketTransferRecoveryService(new HashSet<>(), new HashSet<>());
        DetachedFirstEventRoots roots;
        RocketDestinationCompletion completion;
        RocketTransferRecord record;
        RocketEntity destination;
        ServerPlayer passenger;
        ServerPlayer watcher;
        BlockPos origin;
        net.minecraft.world.level.ChunkPos ticket;
        int waited;

        Landing(GameTestHelper helper) {
            this.helper = helper;
            this.driver = new GameTestTickDriver(helper);
            this.server = helper.getLevel().getServer();
            this.moon = server.getLevel(CelestialIds.MOON_LEVEL);
        }

        void start() {
            helper.assertTrue(moon != null, "Moon unavailable");
            origin = SafeCelestialTravel.FIXED_FEET_POSITION.offset(320, 20, 320);
            // Fixture setup only; production completion never loads chunks.
            ticket = new net.minecraft.world.level.ChunkPos(origin);
            moon.getChunkSource().addRegionTicket(net.minecraft.server.level.TicketType.FORCED, ticket, 2, ticket);
            driver.after(1, () -> guard(this::setup));
        }

        void setup() {
            if (!moon.isPositionEntityTicking(origin)) {
                if (++waited > 100) { throw new IllegalStateException("The fixture origin never became entity ticking"); }
                driver.after(1, () -> guard(this::setup));
                return;
            }
            long now = moon.getGameTime();
            var blocks = List.of(new RocketBlock(new RocketPosition(0, 0, 0),
                    new RocketBlockState(ResourceLocation.tryParse("minecraft:iron_block"), Map.of())),
                    new RocketBlock(new RocketPosition(0, 1, 0),
                            new RocketBlockState(ResourceLocation.tryParse("minecraft:iron_block"), Map.of())));
            var stats = new RocketStats(2, 100, 1_000, 1_000, 1, 1, 1, 0);
            var propulsion = new RocketPropulsion(RocketPropulsion.Mode.ABSTRACT, 1_000, 0, 1, 1,
                    RocketPropulsion.Settings.DEFAULTS);
            var sourceSnapshot = RocketStructureSnapshot.create(UUID.randomUUID(), RocketFlightPlanner.EARTH.dimensionId(),
                    new RocketPosition(0, 90, 0), blocks, List.of(new RocketPosition(0, 1, 0)), stats, now, propulsion);
            var destinationSnapshot = sourceSnapshot.relocated(UUID.randomUUID(), RocketFlightPlanner.MOON.dimensionId(),
                    new RocketPosition(origin.getX(), origin.getY(), origin.getZ()), now);
            UUID logical = UUID.randomUUID();
            UUID transfer = UUID.randomUUID();
            passenger = join(UUID.randomUUID(), "firstMoonPassenger", origin.above(2));
            watcher = join(UUID.randomUUID(), "firstMoonWatcher", origin.east(6));
            var fuel = RocketFuelState.empty(propulsion).fill(1_000).state();
            var plan = new RocketFlightPlan(3, transfer, RocketFlightPlanner.EARTH.bodyId(), RocketFlightPlanner.MOON.bodyId(),
                    RocketFlightPlanner.EARTH.dimensionId(), RocketFlightPlanner.MOON.dimensionId(), 500, now);
            var initial = RocketFlightData.initial(logical, 1_000, 1, plan.sourceBody(), plan.sourceDimension(),
                    sourceSnapshot.sourceOrigin(), now).withFuel(fuel, now);
            var flight = initial.withPassengers(initial.passengers().assign(passenger.getUUID()).orElseThrow())
                    .withPlan(plan).startCountdown(now).completeCountdown(now).beginTransit(transfer, now);
            // The descent began DESCENT_TICKS ago, so the next destination tick is the descent-complete step.
            var arrival = flight.arriveAtDestination(fuel.debit(plan).state(), plan.destinationBody(),
                    plan.destinationDimension(), destinationSnapshot.sourceOrigin(), now - RocketFlightLimits.DESCENT_TICKS);
            destination = ModEntities.ROCKET.get().create(moon);
            helper.assertTrue(destination != null, "No destination entity");
            UUID owner = UUID.randomUUID();
            record = RocketTransferRecord.create(transfer, logical, owner, UUID.randomUUID(), sourceSnapshot,
                    destinationSnapshot, flight, arrival, 500, now).destinationSpawned(destination.getUUID())
                    .advance(RocketTransferPhase.PASSENGERS_TRANSFERRED).advance(RocketTransferPhase.SOURCE_REMOVED)
                    .advance(RocketTransferPhase.COMMITTED);
            destination.initializeTransferred(destinationSnapshot, logical, owner, arrival);
            RocketTransferEntities.positionAtAltitude(destination, destinationSnapshot.sourceOrigin());
            helper.assertTrue(moon.addFreshEntity(destination), "The destination was not published");
            helper.assertTrue(passenger.startRiding(destination, true), "The passenger could not board");
            roots = DetachedFirstEventRoots.fromWorld(helper);
            roots.journal.put(record);
            roots.confirmJournal();
            completion = new RocketDestinationCompletion(new HashSet<>(), new HashSet<>(), recovery.bays(),
                    roots.dispositions());

            completion.tickDestination(server, roots.journal, record);
            FirstEventOutbox ram = roots.journal.firstMoonLandingRam();
            helper.assertTrue(ram.state() == FirstEventOutbox.State.ELECTED, "The completion did not elect: " + ram);
            FirstEvent.MoonLanding event = (FirstEvent.MoonLanding) ram.event();
            helper.assertTrue(event.transferId().equals(record.transferId()) && event.participants()
                    .equals(List.of(passenger.getUUID())) && event.terminalRocketId().equals(destination.getUUID()),
                    "The event differs from the completion: " + event);
            helper.assertTrue(state() == RocketFlightState.DESCENT, "LANDED before the exact confirmation");
            helper.assertTrue(!done(passenger, Milestone.MOON_LANDING) && !done(passenger, Milestone.ONE_SMALL_STEP),
                    "A landing award came before the confirmation");
            completion.tickDestination(server, roots.journal, record);
            helper.assertTrue(roots.journal.firstMoonLandingRam().equals(ram), "A repeated completion elected again");

            roots.confirmJournal();
            helper.assertTrue(state() == RocketFlightState.LANDED, "The confirmed election did not land the rocket");
            helper.assertTrue(done(passenger, Milestone.MOON_LANDING), "The landed passenger missed moon_landing");
            helper.assertTrue(!done(passenger, Milestone.ONE_SMALL_STEP), "The world-first preceded its durable receipt");

            roots.delivery.tick();
            helper.assertTrue(roots.receiptOnDisk(FirstEventKind.FIRST_MOON_LANDING).filter(event::equals).isPresent(),
                    "The receipt of the source event is not on disk");
            helper.assertTrue(done(passenger, Milestone.ONE_SMALL_STEP), "The aboard participant missed the world-first");
            helper.assertTrue(!done(watcher, Milestone.ONE_SMALL_STEP), "A watcher earned the world-first");
            ServerPlayer late = join(UUID.randomUUID(), "firstMoonLate", origin.east(3));
            roots.delivery.onLogin(late);
            roots.delivery.tick();
            helper.assertTrue(!done(late, Milestone.ONE_SMALL_STEP), "A later joiner earned the world-first");
            helper.assertTrue(roots.journal.firstMoonLandingRam().equals(FirstEventOutbox.elected(event).delivered())
                    && roots.journal.durableFirstMoonLanding().outbox().state() == FirstEventOutbox.State.DELIVERED,
                    "The journal source was not acknowledged with the same event");
            cleanup();
            helper.succeed();
        }

        RocketFlightState state() {
            return destination.flightData().orElseThrow().state();
        }

        ServerPlayer join(UUID id, String name, BlockPos position) {
            ServerPlayer player = io.github.sunthemoon.advancedrocketrycommunity.gametest.FirstEventGameTestPlayers
                    .join(server, id, name, moon, position);
            joined.add(player);
            return player;
        }

        boolean done(ServerPlayer player, Milestone milestone) {
            var advancement = server.getAdvancements().getAdvancement(ResourceLocation.tryParse(milestone.id()));
            return advancement != null && player.getAdvancements().getOrStartProgress(advancement).isDone();
        }

        void guard(Runnable step) {
            try {
                step.run();
            } catch (RuntimeException failure) {
                cleanup();
                throw failure;
            }
        }

        void cleanup() {
            joined.forEach(player -> {
                player.stopRiding();
                server.getPlayerList().remove(player);
            });
            joined.clear();
            if (destination != null) {
                destination.discard();
            }
            if (roots != null) {
                roots.close();
            }
            if (ticket != null) {
                moon.getChunkSource().removeRegionTicket(net.minecraft.server.level.TicketType.FORCED, ticket, 2, ticket);
                ticket = null;
            }
        }
    }
}
