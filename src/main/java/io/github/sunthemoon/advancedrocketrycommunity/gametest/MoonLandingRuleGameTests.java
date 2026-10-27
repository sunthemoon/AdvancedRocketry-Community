package io.github.sunthemoon.advancedrocketrycommunity.gametest;

import static io.github.sunthemoon.advancedrocketrycommunity.gametest.RocketFlightGameTestFixtures.findLogicalRocket;

import io.github.sunthemoon.advancedrocketrycommunity.AdvancedRocketryCommunity;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.service.SafeCelestialTravel;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.surface.SurfaceContent;
import io.github.sunthemoon.advancedrocketrycommunity.registry.ModBlocks;
import io.github.sunthemoon.advancedrocketrycommunity.registry.ModEntities;
import io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.RocketEscrowGate;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.entity.RocketEntity;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketDestination;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFlightRequestCode;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFlightState;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketTransferRecord;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.persistence.RocketTransferSavedData;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketBlock;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketBlockState;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketPosition;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketPropulsion;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketStructureSnapshot;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.server.RocketRuntime;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.stats.RocketStats;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.transfer.RocketLandingPadSelector;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/**
 * ADR-075's new cases 1 to 9 and 11: Moon arrivals land on the highest column of their footprint under ADR-033's support
 * rule; recorded destinations keep their origin. Every case shapes the pads it uses first and restores each block it
 * changes. The cases share one batch and take turns on the fixed pads ({@link MoonPadArea#turn}); case 12, which needs
 * the governed fixtures, joins the same batch from {@code resourceescrow}.
 */
@GameTestHolder(AdvancedRocketryCommunity.MOD_ID)
@PrefixGameTestTemplate(false)
public final class MoonLandingRuleGameTests {
    /** The one batch of every ADR-075 case. */
    public static final String BATCH = "moon_landing_rule";
    /** A case may wait for the turns of all the others first. */
    public static final int TIMEOUT = 9_000;
    /** Flat ground for the shaped pads, below the old floor. */
    static final int GROUND = 30;
    static final int REACH = 4;

    private MoonLandingRuleGameTests() {
    }

    /** Case 1: one lowest-layer column one block higher than the rest carries the rocket; the others overhang. */
    @GameTest(template = "rocket_test", batch = BATCH, timeoutTicks = TIMEOUT)
    public static void aMixedFootprintRestsOnItsHighestColumnAndOverhangsTheOthers(GameTestHelper helper) {
        var c = new MoonLandingCase(helper);
        c.start(() -> c.assemble(rocket -> {
            var snapshot = rocket.snapshot().orElseThrow();
            var lowestLayer = MoonPadArea.columns(0, snapshot, true);
            helper.assertTrue(lowestLayer.size() >= 2, "The fixture rocket's lowest layer covers one column only");
            c.changes.shape(0, REACH, GROUND);
            BlockPos raised = lowestLayer.get(0);
            c.changes.set(raised.atY(GROUND + 1), turf());
            c.launch(rocket, landed -> {
                var placed = landed.snapshot().orElseThrow();
                c.assertAtCandidate(placed, 0);
                c.assertOnHighestColumn(placed, GROUND + 2);
                for (BlockPos below : MoonPadArea.below(placed)) {
                    boolean carried = below.getX() == raised.getX() && below.getZ() == raised.getZ();
                    BlockState state = c.moon.getBlockState(below);
                    helper.assertTrue(carried ? state.isFaceSturdy(c.moon, below, Direction.UP) : state.isAir(),
                            "The block below " + below + " is " + state + "; the raised column must carry the rocket"
                                    + " and the other lowest-layer blocks must overhang air");
                }
                helper.succeed();
            });
        }));
    }

    /** Case 2: candidate 0's footprint columns emptied to the build minimum; the rocket lands, supported, at candidate 1. */
    @GameTest(template = "rocket_test", batch = BATCH, timeoutTicks = TIMEOUT)
    public static void anEmptiedFirstPadFallsThroughToTheSecond(GameTestHelper helper) {
        var c = new MoonLandingCase(helper);
        c.start(() -> c.assemble(rocket -> {
            var snapshot = rocket.snapshot().orElseThrow();
            c.changes.empty(MoonPadArea.columns(0, snapshot, false));
            c.changes.shape(1, REACH, GROUND);
            c.launch(rocket, landed -> {
                var placed = landed.snapshot().orElseThrow();
                c.assertAtCandidate(placed, 1);
                c.assertOnHighestColumn(placed, GROUND + 1);
                helper.succeed();
            });
        }));
    }

    /** Case 3: no ground at any pad refuses the launch, as a refused launch: no journal entry, no fuel change, idle. */
    @GameTest(template = "rocket_test", batch = BATCH, timeoutTicks = TIMEOUT)
    public static void noGroundAtAnyPadRefusesTheLaunchWithoutChangingTheRocket(GameTestHelper helper) {
        var c = new MoonLandingCase(helper);
        c.start(() -> c.assemble(rocket -> {
            var snapshot = rocket.snapshot().orElseThrow();
            for (int index = 0; index < MoonPadArea.CANDIDATES; index++) {
                c.changes.empty(MoonPadArea.columns(index, snapshot, false));
            }
            var before = rocket.flightData().orElseThrow();
            var logical = rocket.assemblyTransactionId().orElseThrow();
            UUID request = UUID.randomUUID();
            c.logicals.add(logical);
            var result = RocketRuntime.requestAdminFlight(rocket, RocketDestination.MOON, request);
            helper.assertTrue(result.code() == RocketFlightRequestCode.LANDING_PAD_UNAVAILABLE && result.detailKey() == null,
                    "Expected LANDING_PAD_UNAVAILABLE without a translated detail, got " + result.code() + " "
                            + result.detailKey());
            var journal = RocketTransferSavedData.get(c.server);
            helper.assertTrue(rocket.flightData().orElseThrow().equals(before)
                    && before.state() == RocketFlightState.FUELED && rocket.isAlive(),
                    "The refused launch changed the rocket's flight data or fuel");
            helper.assertTrue(journal.find(request).isEmpty() && journal.findByLogicalRocket(logical).isEmpty(),
                    "The refused launch wrote a journal entry");
            helper.assertTrue(RocketEscrowGate.idle(rocket), "The refused launch left the rocket's escrow gate busy");
            helper.succeed();
        }));
    }

    /** Case 4: the developer platform on the shaped pad carries an arrival at y 80. */
    @GameTest(template = "rocket_test", batch = BATCH, timeoutTicks = TIMEOUT)
    public static void theDeveloperPlatformCarriesAnArrivalAtY80(GameTestHelper helper) {
        var c = new MoonLandingCase(helper);
        c.start(() -> c.assemble(rocket -> {
            c.changes.shape(0, REACH, GROUND);
            BlockPos pad = SafeCelestialTravel.FIXED_FEET_POSITION;
            int radius = SafeCelestialTravel.PLATFORM_RADIUS;
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    for (int dy = -1; dy < SafeCelestialTravel.CLEARANCE; dy++) {
                        c.changes.record(pad.offset(dx, dy, dz));
                    }
                }
            }
            new SafeCelestialTravel().prepareFixedPlatform(c.moon);
            c.launch(rocket, landed -> {
                var placed = landed.snapshot().orElseThrow();
                c.assertAtCandidate(placed, 0);
                c.assertOnHighestColumn(placed, pad.getY());
                helper.succeed();
            });
        }));
    }

    /** Case 5: a rocket parked on the ground at candidate 0 sends the new arrival to candidate 1. */
    @GameTest(template = "rocket_test", batch = BATCH, timeoutTicks = TIMEOUT)
    public static void aRocketParkedOnTheFirstPadSendsTheArrivalToTheSecond(GameTestHelper helper) {
        var c = new MoonLandingCase(helper);
        c.start(() -> c.assemble(rocket -> {
            var snapshot = rocket.snapshot().orElseThrow();
            c.changes.shape(0, REACH, GROUND);
            c.changes.shape(1, REACH, GROUND);
            var at = MoonPadArea.origin(0, snapshot);
            var parkedSnapshot = snapshot.relocated(UUID.randomUUID(), c.moon.dimension().location(),
                    new RocketPosition(at.x(), GROUND + 1 - snapshot.bounds().minimum().y(), at.z()), c.moon.getGameTime());
            helper.assertTrue(MoonPadArea.supported(c.moon, parkedSnapshot), "The parked fixture is not on the ground");
            RocketEntity parked = ModEntities.ROCKET.get().create(c.moon);
            helper.assertTrue(parked != null, "Rocket type unavailable");
            parked.initialize(parkedSnapshot, UUID.randomUUID(), UUID.randomUUID());
            helper.assertTrue(c.moon.addFreshEntity(parked), "The parked rocket was not added");
            c.extras.add(parked);
            var parkedAt = parked.position();
            c.launch(rocket, landed -> {
                var placed = landed.snapshot().orElseThrow();
                c.assertAtCandidate(placed, 1);
                c.assertOnHighestColumn(placed, GROUND + 1);
                helper.assertTrue(parked.isAlive() && parked.position().equals(parkedAt), "The parked rocket moved");
                helper.succeed();
            });
        }));
    }

    /** Case 6: support removed after selection returns the rocket to its source with its fuel, one logical rocket. */
    @GameTest(template = "rocket_test", batch = BATCH, timeoutTicks = TIMEOUT)
    public static void removedMoonSupportReturnsOneRocketWithItsOriginalFuel(GameTestHelper helper) {
        changedSupport(helper, Blocks.AIR.defaultBlockState());
    }

    /** Case 6: water placed below the recorded destination fails its recheck. */
    @GameTest(template = "rocket_test", batch = BATCH, timeoutTicks = TIMEOUT)
    public static void floodedMoonSupportFailsTheRecheck(GameTestHelper helper) {
        changedSupport(helper, Blocks.WATER.defaultBlockState());
    }

    private static void changedSupport(GameTestHelper helper, BlockState replacement) {
        var c = new MoonLandingCase(helper);
        c.start(() -> c.assemble(rocket -> {
            c.changes.shape(0, REACH, GROUND);
            var logical = rocket.assemblyTransactionId().orElseThrow();
            var before = rocket.flightData().orElseThrow();
            var snapshot = rocket.snapshot().orElseThrow();
            UUID transfer = c.request(rocket);
            var journal = RocketTransferSavedData.get(c.server);
            var destination = journal.find(transfer).orElseThrow().destinationSnapshot();
            var selector = new RocketLandingPadSelector();
            helper.assertTrue(MoonPadArea.lowest(destination) == GROUND + 1, "The fixture destination is not on the ground");
            helper.assertTrue(selector.available(c.moon, destination, null, false), "The reserved pad was not available");
            // Water is checked and restored synchronously below, before scheduled fluid ticks.
            MoonPadArea.below(destination).forEach(below -> c.changes.set(below, replacement));
            helper.assertTrue(!selector.available(c.moon, destination, null, false), "The changed support remained available");
            if (!replacement.isAir()) {
                c.changes.restore();
                helper.succeed();
                return;
            }
            RocketFlightGameTestFixtures.awaitSourceReturned(helper, c.driver, c.earth, logical, 190, () -> {
                var state = rocket.flightData().orElseThrow();
                helper.assertTrue(rocket.isAlive() && state.state() == RocketFlightState.FUELED,
                        "The changed pad did not return the live source");
                helper.assertTrue(state.currentTarget().equals(before.currentTarget())
                        && state.currentOrigin().equals(before.currentOrigin())
                        && rocket.snapshot().orElseThrow().equals(snapshot), "The return changed source authority");
                helper.assertTrue(state.fuel().equals(before.fuel()), "The refused spawn changed the source fuel");
                helper.assertTrue(journal.find(transfer).isEmpty(), "The returned transfer kept its reservation");
                helper.assertTrue(c.count(logical) == 1, "The changed pad duplicated or lost the logical rocket");
                helper.succeed();
            });
        }));
    }

    /** Case 7: a seated passenger lands on flat ground and dismounts onto it. */
    @GameTest(template = "rocket_test", batch = BATCH, timeoutTicks = TIMEOUT)
    public static void aSeatedPassengerDismountsOntoTheGround(GameTestHelper helper) {
        var c = new MoonLandingCase(helper);
        c.start(() -> c.assemble(rocket -> {
            c.changes.shape(0, REACH, GROUND);
            // Survival, so that fall and suffocation damage would apply; creative players are exempt from both.
            ServerPlayer passenger = ConnectedTestPlayers.join(c.server, UUID.randomUUID(), "moonRulePassenger", c.earth,
                    helper.absolutePos(new BlockPos(2, 3, 2)), new ArrayList<>(), false, new ArrayList<>(), false);
            c.players.add(passenger);
            List<DamageSource> damage = new ArrayList<>();
            Consumer<LivingAttackEvent> listener = event -> {
                if (event.getEntity() == passenger) {
                    damage.add(event.getSource());
                }
            };
            MinecraftForge.EVENT_BUS.addListener(EventPriority.LOWEST, false, LivingAttackEvent.class, listener);
            c.listeners.add(listener);
            var initial = rocket.flightData().orElseThrow();
            rocket.updateFlightData(initial.withPassengers(initial.passengers().assign(passenger.getUUID()).orElseThrow()));
            helper.assertTrue(passenger.startRiding(rocket, true), "The passenger could not board");
            c.launch(rocket, landed -> {
                var placed = landed.snapshot().orElseThrow();
                c.assertAtCandidate(placed, 0);
                c.assertOnHighestColumn(placed, GROUND + 1);
                helper.assertTrue(passenger.getVehicle() == landed && passenger.level() == c.moon,
                        "The passenger did not land seated");
                passenger.stopRiding();
                c.driver.after(20, () -> {
                    BlockPos feet = passenger.blockPosition();
                    BlockState ground = c.moon.getBlockState(feet.below());
                    helper.assertTrue(passenger.getVehicle() == null && passenger.level() == c.moon, "The passenger did not dismount");
                    helper.assertTrue(passenger.getY() == MoonPadArea.lowest(placed) && ground.getFluidState().isEmpty()
                                    && ground.isFaceSturdy(c.moon, feet.below(), Direction.UP),
                            "The passenger stands at y " + passenger.getY() + " over " + ground + ", not on the ground");
                    helper.assertTrue(c.moon.noCollision(passenger), "The passenger was placed inside a block");
                    helper.assertTrue(passenger.fallDistance == 0.0F, "The passenger is falling: " + passenger.fallDistance);
                    helper.assertTrue(damage.stream().noneMatch(source -> source.is(DamageTypes.FALL)
                                    || source.is(DamageTypes.IN_WALL) || source.is(DamageTypes.FLY_INTO_WALL)),
                            "The passenger took landing damage: " + damage.stream().map(DamageSource::getMsgId).toList());
                    helper.succeed();
                });
            });
        }));
    }

    /**
     * Case 8: an ordinary PREPARED record whose destination's lowest block is at y 80 over lower ground, built with
     * {@code RocketTransferRecord.create} so that snapshot, arrival origin and checksum agree, lands at its recorded
     * origin under the legacy recheck, with exact fuel and one logical rocket.
     */
    @GameTest(template = "rocket_test", batch = BATCH, timeoutTicks = TIMEOUT)
    public static void aRecordedY80DestinationLandsAtItsRecordedOrigin(GameTestHelper helper) {
        var c = new MoonLandingCase(helper);
        c.start(() -> c.assemble(rocket -> {
            c.changes.shape(0, REACH, GROUND);
            var logical = rocket.assemblyTransactionId().orElseThrow();
            UUID transfer = c.request(rocket);
            var record = c.relocate(transfer, SafeCelestialTravel.FIXED_FEET_POSITION.getY());
            var origin = record.destinationSnapshot().sourceOrigin();
            helper.assertTrue(!MoonPadArea.supported(c.moon, record.destinationSnapshot()),
                    "The recorded y 80 destination has ground under it");
            RocketFlightGameTestFixtures.awaitFlightState(helper, c.driver, c.moon, logical, RocketFlightState.LANDED,
                    MoonLandingCase.FLIGHT_TICKS, () -> {
                RocketEntity landed = findLogicalRocket(c.moon, logical);
                helper.assertTrue(landed != null && landed.flightData().orElseThrow().state() == RocketFlightState.LANDED,
                        "The recorded y 80 destination did not land");
                var placed = landed.snapshot().orElseThrow();
                helper.assertTrue(placed.sourceOrigin().equals(origin) && landed.blockPosition().getY() == origin.y()
                        && MoonPadArea.lowest(placed) == SafeCelestialTravel.FIXED_FEET_POSITION.getY(),
                        "The rocket landed at " + placed.sourceOrigin() + ", not at its recorded origin " + origin);
                helper.assertTrue(landed.flightData().orElseThrow().fuel().equals(record.destinationFlightData().fuel()),
                        "The landing changed the recorded arrival fuel");
                helper.assertTrue(c.count(logical) == 1, "The landing duplicated or lost the logical rocket");
                helper.succeed();
            });
        }));
    }

    /**
     * Case 9: recovery rebuilds a recorded Moon destination at its recorded origin, at y 80 and below it, without the
     * selector.
     */
    @GameTest(template = "rocket_test", batch = BATCH, timeoutTicks = TIMEOUT)
    public static void recoveryRebuildsRecordedMoonDestinationsAtTheirOrigins(GameTestHelper helper) {
        var c = new MoonLandingCase(helper);
        c.start(() -> c.assemble(below -> {
            c.changes.shape(0, REACH, GROUND);
            c.rebuild(below, c.request(below), GROUND + 1, () -> c.assemble(new BlockPos(6, 2, 3),
                    legacy -> c.rebuild(legacy, c.request(legacy), SafeCelestialTravel.FIXED_FEET_POSITION.getY(),
                            helper::succeed)));
        }));
    }

    /**
     * Case 11: a rocket whose highest footprint column lies only under an upper block is refused at candidate 0 and
     * lands, supported, at candidate 1. First directly on the selector with a hand-built snapshot, then in flight with
     * a scanned rocket whose tank sits one layer above the engine.
     */
    @GameTest(template = "rocket_test", batch = BATCH, timeoutTicks = TIMEOUT)
    public static void aHighestColumnUnderAnUpperBlockRefusesThePad(GameTestHelper helper) {
        var c = new MoonLandingCase(helper);
        c.start(() -> {
            var iron = new RocketBlockState(ResourceLocation.tryParse("minecraft:iron_block"), Map.of());
            var built = RocketStructureSnapshot.create(UUID.randomUUID(), c.earth.dimension().location(),
                    new RocketPosition(0, 100, 0), List.of(new RocketBlock(new RocketPosition(0, 0, 0), iron),
                            new RocketBlock(new RocketPosition(0, 1, 0), iron), new RocketBlock(new RocketPosition(1, 1, 0), iron)),
                    List.of(new RocketPosition(0, 1, 0)), new RocketStats(3, 100, 1_000, 2_000, 1, 1, 1, 0),
                    c.moon.getGameTime(), new RocketPropulsion(RocketPropulsion.Mode.MONO, 2_000, 0, 1, 1,
                            RocketPropulsion.Settings.DEFAULTS));
            raiseUpperColumn(c, built);
            var selected = new RocketLandingPadSelector().select(c.moon, built, UUID.randomUUID(), List.of(),
                    c.moon.getGameTime());
            helper.assertTrue(selected.success(), "No pad admitted the hand-built rocket: " + selected.detail());
            var placed = selected.snapshot().orElseThrow();
            c.assertAtCandidate(placed, 1);
            c.assertOnHighestColumn(placed, GROUND + 1);
            c.assemble(MoonLandingCase.ORIGIN, at -> {
                helper.setBlock(at, ModBlocks.ROCKET_MOTOR.get());
                helper.setBlock(at.above(), ModBlocks.ROCKET_SEAT.get());
                helper.setBlock(at.above(2), ModBlocks.GUIDANCE_COMPUTER.get());
                helper.setBlock(at.above().east(), ModBlocks.ROCKET_FUEL_TANK.get());
            }, rocket -> {
                var scanned = rocket.snapshot().orElseThrow();
                helper.assertTrue(MoonPadArea.columns(0, scanned, true).size() == 1
                        && MoonPadArea.columns(0, scanned, false).size() == 2, "The scanned rocket lost its upper tank column");
                raiseUpperColumn(c, scanned);
                c.launch(rocket, landed -> {
                    var flown = landed.snapshot().orElseThrow();
                    c.assertAtCandidate(flown, 1);
                    c.assertOnHighestColumn(flown, GROUND + 1);
                    helper.succeed();
                });
            });
        });
    }

    /** Shapes candidates 0 and 1 and raises, at candidate 0, the column that only an upper block covers. */
    private static void raiseUpperColumn(MoonLandingCase c, RocketStructureSnapshot snapshot) {
        c.changes.shape(0, REACH, GROUND);
        c.changes.shape(1, REACH, GROUND);
        var lowestLayer = MoonPadArea.columns(0, snapshot, true);
        for (BlockPos column : MoonPadArea.columns(0, snapshot, false)) {
            if (!lowestLayer.contains(column)) {
                for (int y = GROUND + 1; y <= GROUND + 3; y++) {
                    c.changes.set(column.atY(y), turf());
                }
            }
        }
    }

    private static BlockState turf() {
        return SurfaceContent.MOON_TURF.get().defaultBlockState();
    }
}
