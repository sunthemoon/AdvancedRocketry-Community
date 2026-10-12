package io.github.sunthemoon.advancedrocketrycommunity.gametest;

import io.github.sunthemoon.advancedrocketrycommunity.AdvancedRocketryCommunity;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.CelestialIds;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.network.StationSkyContextPacket;
import io.github.sunthemoon.advancedrocketrycommunity.registry.ModBlocks;
import io.github.sunthemoon.advancedrocketrycommunity.station.controller.AltitudeControllerMenu;
import io.github.sunthemoon.advancedrocketrycommunity.station.controller.GravityControllerMenu;
import io.github.sunthemoon.advancedrocketrycommunity.station.controller.OrientationControllerMenu;
import io.github.sunthemoon.advancedrocketrycommunity.station.controller.StationControllerKind;
import io.github.sunthemoon.advancedrocketrycommunity.station.controller.StationControllerMenu;
import io.github.sunthemoon.advancedrocketrycommunity.station.controller.WarpControllerMenu;
import io.github.sunthemoon.advancedrocketrycommunity.station.elevator.ElevatorStationGuard;
import io.github.sunthemoon.advancedrocketrycommunity.station.model.StationOrbitalControls;
import io.github.sunthemoon.advancedrocketrycommunity.station.model.StationState;
import io.github.sunthemoon.advancedrocketrycommunity.station.orbit.StationSkyContextService;
import io.github.sunthemoon.advancedrocketrycommunity.station.persistence.StationRegistrySavedData;
import io.github.sunthemoon.advancedrocketrycommunity.station.service.StationActorContext;
import io.github.sunthemoon.advancedrocketrycommunity.station.service.StationManagementCode;
import io.github.sunthemoon.advancedrocketrycommunity.station.service.StationManager;
import io.github.sunthemoon.advancedrocketrycommunity.station.service.StationRuntime;
import io.github.sunthemoon.advancedrocketrycommunity.station.warp.StationRocketAuthority;
import io.github.sunthemoon.advancedrocketrycommunity.station.warp.StationWarpRuntime;
import io.netty.buffer.Unpooled;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/**
 * ADR-065 C17b on the real server: the four controller screens through the shared context validator (actor and
 * core reach), the warp controller against the command, persistence of each setting, the elevator guard and the
 * rotating sky heartbeat. Each test has its own batch, so the global rocket port and elevator guard it may replace
 * never overlap another test.
 */
@GameTestHolder(AdvancedRocketryCommunity.MOD_ID)
@PrefixGameTestTemplate(false)
public final class StationControlsGameTests {
    private static final int RATE_TICKS = 11;

    private StationControlsGameTests() {
    }

    @GameTest(template = "empty", batch = "station_controls_actor_reach", timeoutTicks = 100)
    public static void controllersRefuseActorsOutsideTheirReachAndRegion(GameTestHelper helper) {
        StationWarpGameTests.Fixture fixture = new StationWarpGameTests.Fixture(helper, "Controller reach",
                CelestialIds.EARTH_ID, 0);
        List<BlockPos> placed = new ArrayList<>();
        try {
            fixture.keepLoaded();
            fixture.join(fixture.ownerId, "reachOwner");
            ServerPlayer owner = fixture.server.getPlayerList().getPlayer(fixture.ownerId);
            UUID memberId = UUID.randomUUID();
            fixture.data.invite(fixture.id(), memberId);
            fixture.data.acceptInvitation(fixture.id(), memberId);
            fixture.join(memberId, "reachMember");
            ServerPlayer member = fixture.server.getPlayerList().getPlayer(memberId);
            StationManager stations = StationRuntime.manager().orElseThrow();
            BlockPos gravityAt = place(fixture, placed, StationControllerKind.GRAVITY, fixture.pad.offset(3, 0, 3));
            BlockPos altitudeAt = place(fixture, placed, StationControllerKind.ALTITUDE, fixture.pad.offset(4, 0, 3));
            BlockPos orientationAt = place(fixture, placed, StationControllerKind.ORIENTATION,
                    fixture.pad.offset(5, 0, 3));
            StationActorContext gravity = context(fixture, StationControllerKind.GRAVITY, gravityAt);
            StationActorContext altitude = context(fixture, StationControllerKind.ALTITUDE, altitudeAt);
            StationActorContext orientation = context(fixture, StationControllerKind.ORIENTATION, orientationAt);

            expect(helper, stations.setGravity(member, gravity, Optional.empty(), 50).code(),
                    StationManagementCode.UNAUTHORIZED, "a member at the gravity controller");
            // In the region, but more than 8 blocks from each controller.
            owner.teleportTo(fixture.space, fixture.pad.getX() + 15.5D, fixture.pad.getY(), fixture.pad.getZ() + 3.5D,
                    0.0F, 0.0F);
            expect(helper, stations.setGravity(owner, gravity, Optional.empty(), 50).code(),
                    StationManagementCode.CONTROLLER_OUT_OF_REACH, "gravity from 12 blocks");
            expect(helper, stations.orbitalControls().setAltitude(owner, altitude, Optional.empty(), 20).code(),
                    StationManagementCode.CONTROLLER_OUT_OF_REACH, "altitude from 11 blocks");
            expect(helper, stations.orbitalControls().setRates(owner, orientation, Optional.empty(), 1, 0, 0).code(),
                    StationManagementCode.CONTROLLER_OUT_OF_REACH, "orientation from 10 blocks");
            StationControllerMenu menu = menu(fixture, owner, StationControllerKind.GRAVITY, gravityAt);
            helper.assertTrue(!menu.stillValid(owner), "A menu 12 blocks away is still valid");
            owner.teleportTo(fixture.space, fixture.pad.getX() + 0.5D, fixture.pad.getY(), fixture.pad.getZ() + 0.5D,
                    0.0F, 0.0F);
            helper.assertTrue(menu.stillValid(owner), "A menu within reach is not valid");
            // The exact block: a removed controller is no longer a context.
            fixture.space.setBlockAndUpdate(altitudeAt, Blocks.AIR.defaultBlockState());
            expect(helper, stations.orbitalControls().setAltitude(owner, altitude, Optional.empty(), 20).code(),
                    StationManagementCode.CONTROLLER_OUT_OF_REACH, "a removed controller");
            // A controller at the region edge with the actor just outside the region.
            BlockPos edge = new BlockPos(fixture.created.region().maximumX(), fixture.pad.getY(), fixture.pad.getZ());
            fixture.space.getChunkAt(edge); // Test setup only.
            place(fixture, placed, StationControllerKind.ALTITUDE, edge);
            owner.teleportTo(fixture.space, edge.getX() + 3.5D, edge.getY(), edge.getZ() + 0.5D, 0.0F, 0.0F);
            expect(helper, stations.orbitalControls().setAltitude(owner,
                            context(fixture, StationControllerKind.ALTITUDE, edge), Optional.empty(), 20).code(),
                    StationManagementCode.CONTROLLER_OUT_OF_REACH, "an actor outside the controller's region");
            owner.teleportTo(fixture.space, fixture.pad.getX() + 0.5D, fixture.pad.getY(), fixture.pad.getZ() + 0.5D,
                    0.0F, 0.0F);
            StationState before = fixture.station();
            helper.assertTrue(before.environment().gravityMilli() == 0
                    && before.orbitalControls().equals(fixture.created.orbitalControls()), "A refusal changed the station");
            // Within reach the same services commit, and gravity and orbit share one per-station cooldown.
            expect(helper, stations.setGravity(owner, gravity, Optional.empty(), 50).code(),
                    StationManagementCode.GRAVITY_SET, "gravity within reach");
            expect(helper, stations.orbitalControls().setRates(owner, orientation, Optional.empty(), 1, 0, 0).code(),
                    StationManagementCode.ORBITAL_COOLDOWN, "orientation right after a gravity write");
        } finally {
            clear(fixture, placed);
            fixture.close();
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "station_controls_core_reach", timeoutTicks = 100)
    public static void theWarpControllerNeedsALoadedCoreWithinFiveBlocks(GameTestHelper helper) {
        StationWarpGameTests.Fixture fixture = new StationWarpGameTests.Fixture(helper, "Core reach",
                CelestialIds.EARTH_ID, 2_500_000);
        StationRocketAuthority previous = fixture.warp.installRocketAuthority(StationWarpGameTests.NOTHING_IN_MOTION);
        List<BlockPos> placed = new ArrayList<>();
        try {
            fixture.keepLoaded();
            fixture.join(fixture.ownerId, "coreOwner");
            ServerPlayer owner = fixture.server.getPlayerList().getPlayer(fixture.ownerId);
            BlockPos controller = place(fixture, placed, StationControllerKind.WARP, fixture.pad.offset(3, 0, 2));
            StationActorContext warp = context(fixture, StationControllerKind.WARP, controller);
            expect(helper, fixture.warp.request(owner, warp, StationWarpGameTests.MOON).code(),
                    StationManagementCode.NO_WARP_CORE_NEAR_CONTROLLER, "no core");
            fixture.core(controller.offset(0, 0, 6));
            expect(helper, fixture.warp.request(owner, warp, StationWarpGameTests.MOON).code(),
                    StationManagementCode.NO_WARP_CORE_NEAR_CONTROLLER, "a core 6 blocks away");
            // The command's own rule is unchanged: looking away from every core is refused.
            fixture.look(fixture.ownerId, fixture.pad.above(5));
            expect(helper, fixture.warp.request(owner, true, StationWarpGameTests.MOON).code(),
                    StationManagementCode.NO_WARP_CORE, "the command without a core in sight");
            fixture.core(controller.offset(0, 0, 4));
            expect(helper, fixture.warp.request(owner, warp, StationWarpGameTests.MOON).code(),
                    StationManagementCode.WARP_ISSUED, "a core 4 blocks away");
            helper.assertTrue(fixture.station().orbitBody().equals(CelestialIds.EARTH_ID)
                    && fixture.data.warpEnergy(fixture.id()) == 2_500_000, "A request spent energy or moved the orbit");
        } finally {
            fixture.warp.installRocketAuthority(previous);
            clear(fixture, placed);
            fixture.close();
        }
        helper.succeed();
    }

    /**
     * W07 review R2: after a successful screen quote, a confirmation from a controller whose core was removed or
     * moved out of its 5-block reach is refused before it is consumed, with no countdown and no energy spent. (A
     * core in an unloaded chunk cannot be staged within 13 blocks of a connected player; {@code WarpCoreReachTest}
     * covers it on the same scan.) The command confirmation keeps its rule without a ray pick.
     */
    @GameTest(template = "empty", batch = "station_controls_confirm_core", timeoutTicks = 100)
    public static void aScreenConfirmationRechecksTheControllersCoreReach(GameTestHelper helper) {
        StationWarpGameTests.Fixture fixture = new StationWarpGameTests.Fixture(helper, "Confirm core",
                CelestialIds.EARTH_ID, 2_500_000);
        StationRocketAuthority previous = fixture.warp.installRocketAuthority(StationWarpGameTests.NOTHING_IN_MOTION);
        List<BlockPos> placed = new ArrayList<>();
        try {
            ServerPlayer owner = owner(fixture, "confirmCore");
            BlockPos controller = place(fixture, placed, StationControllerKind.WARP, fixture.pad.offset(3, 0, 2));
            StationActorContext warp = context(fixture, StationControllerKind.WARP, controller);
            BlockPos near = controller.offset(0, 0, 3);
            fixture.core(near);
            expect(helper, fixture.warp.request(owner, warp, StationWarpGameTests.MOON).code(),
                    StationManagementCode.WARP_ISSUED, "the quote with a core 3 blocks away");
            fixture.space.setBlockAndUpdate(near, Blocks.AIR.defaultBlockState());
            expect(helper, fixture.warp.confirm(owner, warp, fixture.id()).code(),
                    StationManagementCode.NO_WARP_CORE_NEAR_CONTROLLER, "a confirmation after the core was removed");
            unspent(helper, fixture, "a removed core");
            fixture.core(controller.offset(0, 0, 6));
            expect(helper, fixture.warp.confirm(owner, warp, fixture.id()).code(),
                    StationManagementCode.NO_WARP_CORE_NEAR_CONTROLLER, "a confirmation with the core 6 blocks away");
            unspent(helper, fixture, "a distant core");
            // The refusals consumed nothing: with a core back in reach the same quote starts, and is cancelled.
            fixture.core(controller.offset(0, 0, 4));
            expect(helper, fixture.warp.confirm(owner, warp, fixture.id()).code(),
                    StationManagementCode.WARP_STARTED, "the same quote with a core 4 blocks away");
            expect(helper, fixture.warp.cancel(owner, warp).code(), StationManagementCode.WARP_CANCELLED,
                    "cancel through the screen context");
            // The command path: its quote needs the ray pick, its confirmation does not.
            owner.teleportTo(fixture.space, controller.getX() + 0.5D, controller.getY(), controller.getZ() + 1.5D,
                    0.0F, 0.0F);
            fixture.look(fixture.ownerId, controller.offset(0, 0, 4));
            expect(helper, fixture.warp.request(owner, true, StationWarpGameTests.MOON).code(),
                    StationManagementCode.WARP_ISSUED, "the command quote looking at the core");
            fixture.look(fixture.ownerId, fixture.pad.above(5));
            expect(helper, fixture.warp.confirm(owner, true, fixture.id()).code(), StationManagementCode.WARP_STARTED,
                    "the command confirmation looking away");
            expect(helper, fixture.warp.cancel(owner, true).code(), StationManagementCode.WARP_CANCELLED,
                    "the command cancel");
            unspent(helper, fixture, "after both cancels");
        } finally {
            fixture.warp.installRocketAuthority(previous);
            clear(fixture, placed);
            fixture.close();
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "station_controls_warp_parity", timeoutTicks = 400)
    public static void theWarpControllerReachesTheSameOutcomeAsTheCommand(GameTestHelper helper) {
        StationWarpGameTests.Fixture command = new StationWarpGameTests.Fixture(helper, "Command warp",
                CelestialIds.EARTH_ID, 2_500_000);
        StationWarpGameTests.Fixture screen = new StationWarpGameTests.Fixture(helper, "Screen warp",
                CelestialIds.EARTH_ID, 2_500_000);
        StationRocketAuthority previous = command.warp.installRocketAuthority(StationWarpGameTests.NOTHING_IN_MOTION);
        List<BlockPos> placed = new ArrayList<>();
        Runnable cleanup = () -> {
            command.warp.installRocketAuthority(previous);
            clear(screen, placed);
            command.close();
            screen.close();
        };
        boolean scheduled = false;
        try {
            command.keepLoaded();
            screen.keepLoaded();
            List<String> commandReplies = command.join(command.ownerId, "parityCommand");
            command.core(command.pad.east(2));
            command.look(command.ownerId, command.pad.east(2));
            command.run(command.ownerId, "arce station warp " + StationWarpGameTests.MOON);
            command.run(command.ownerId, "arce station warp confirm " + command.id());
            helper.assertTrue(commandReplies.stream().anyMatch(line -> line.startsWith("Warp countdown started")),
                    "The command did not start: " + commandReplies);

            screen.join(screen.ownerId, "parityScreen");
            ServerPlayer owner = screen.server.getPlayerList().getPlayer(screen.ownerId);
            BlockPos controller = place(screen, placed, StationControllerKind.WARP, screen.pad.offset(3, 0, 2));
            screen.core(controller.offset(0, 0, 3));
            StationControllerMenu menu = menu(screen, owner, StationControllerKind.WARP, controller);
            List<?> targets = menu.names().subList(1, menu.names().size());
            int moon = targets.indexOf(StationWarpGameTests.MOON);
            helper.assertTrue(moon >= 0, "The warp screen does not offer the Moon: " + menu.names());
            for (int step = 0; step < moon; step++) {
                helper.assertTrue(menu.clickMenuButton(owner, WarpControllerMenu.BUTTON_NEXT), "Next refused");
            }
            helper.assertTrue(menu.clickMenuButton(owner, WarpControllerMenu.BUTTON_REQUEST), "Request refused");
            helper.assertTrue(menu.status().equals(Optional.of(StationManagementCode.WARP_ISSUED)),
                    "The screen request differs: " + menu.status());
            helper.assertTrue(screen.station().orbitBody().equals(CelestialIds.EARTH_ID), "A request moved the orbit");
            StationState commandBefore = command.station();
            StationState screenBefore = screen.station();
            scheduled = true;
            helper.runAfterDelay(RATE_TICKS, () -> {
                try {
                    menu.broadcastChanges();
                    helper.assertTrue(menu.clickMenuButton(owner, WarpControllerMenu.BUTTON_CONFIRM), "Confirm refused");
                    helper.assertTrue(menu.status().equals(Optional.of(StationManagementCode.WARP_STARTED)),
                            "The screen confirmation differs: " + menu.status());
                } catch (RuntimeException | Error failure) {
                    cleanup.run();
                    throw failure;
                }
                helper.runAfterDelay(StationWarpGameTests.COMMIT_DELAY, () -> {
                    try {
                        helper.assertTrue(command.station().equals(commandBefore.withOrbitBody(StationWarpGameTests.MOON)),
                                "The command warp did not relocate only the orbit");
                        helper.assertTrue(screen.station().equals(screenBefore.withOrbitBody(StationWarpGameTests.MOON)),
                                "The screen warp did not relocate only the orbit");
                        int commandBalance = command.data.warpEnergy(command.id());
                        helper.assertTrue(commandBalance == 500_000
                                        && screen.data.warpEnergy(screen.id()) == commandBalance,
                                "The two paths charged differently: " + commandBalance + " / "
                                        + screen.data.warpEnergy(screen.id()));
                        StationRegistrySavedData onDisk = StationRegistrySavedData.load(screen.stationFile());
                        helper.assertTrue(onDisk.find(screen.id()).filter(screen.station()::equals).isPresent()
                                && onDisk.find(command.id()).filter(command.station()::equals).isPresent(),
                                "The checked writes are not on disk");
                    } finally {
                        cleanup.run();
                    }
                    helper.succeed();
                });
            });
        } finally {
            if (!scheduled) {
                cleanup.run();
            }
        }
    }

    @GameTest(template = "empty", batch = "station_controls_persistence", timeoutTicks = 100)
    public static void gravityAltitudeAndOrientationWritesPersistAcrossAReload(GameTestHelper helper) {
        StationWarpGameTests.Fixture gravityStation = new StationWarpGameTests.Fixture(helper, "Gravity screen",
                CelestialIds.EARTH_ID, 0);
        StationWarpGameTests.Fixture altitudeStation = new StationWarpGameTests.Fixture(helper, "Altitude screen",
                CelestialIds.EARTH_ID, 0);
        StationWarpGameTests.Fixture orientationStation = new StationWarpGameTests.Fixture(helper,
                "Orientation screen", CelestialIds.EARTH_ID, 0);
        List<StationWarpGameTests.Fixture> fixtures = List.of(gravityStation, altitudeStation, orientationStation);
        List<BlockPos> placed = new ArrayList<>();
        try {
            ServerPlayer gravityOwner = owner(gravityStation, "persistGravity");
            ServerPlayer altitudeOwner = owner(altitudeStation, "persistAltitude");
            orientationStation.keepLoaded();
            List<String> replies = orientationStation.join(orientationStation.ownerId, "persistTurning");
            ServerPlayer orientationOwner = orientationStation.server.getPlayerList().getPlayer(orientationStation.ownerId);
            StationControllerMenu gravity = menu(gravityStation, gravityOwner, StationControllerKind.GRAVITY,
                    place(gravityStation, placed, StationControllerKind.GRAVITY, gravityStation.pad.offset(2, 0, 2)));
            StationControllerMenu altitude = menu(altitudeStation, altitudeOwner, StationControllerKind.ALTITUDE,
                    place(altitudeStation, placed, StationControllerKind.ALTITUDE, altitudeStation.pad.offset(2, 0, 2)));
            StationControllerMenu orientation = menu(orientationStation, orientationOwner,
                    StationControllerKind.ORIENTATION, place(orientationStation, placed,
                            StationControllerKind.ORIENTATION, orientationStation.pad.offset(2, 0, 2)));
            click(helper, gravity, gravityOwner, 3, StationManagementCode.GRAVITY_SET);
            click(helper, altitude, altitudeOwner, AltitudeControllerMenu.BUTTON_GEO, StationManagementCode.ALTITUDE_SET);
            click(helper, orientation, orientationOwner, 3, StationManagementCode.ORIENTATION_SET);
            helper.assertTrue(gravity.lines().size() == 1 && altitude.lines().size() == 3 && orientation.lines().size() == 3,
                    "The screens have no display lines");
            StationRegistrySavedData reloaded = StationRegistrySavedData.load(gravityStation.stationFile());
            helper.assertTrue(reloaded.operational(), "The station file does not reload");
            StationState gravityState = reloaded.find(gravityStation.id()).orElseThrow();
            StationState altitudeState = reloaded.find(altitudeStation.id()).orElseThrow();
            StationState orientationState = reloaded.find(orientationStation.id()).orElseThrow();
            helper.assertTrue(gravityState.equals(gravityStation.station())
                    && gravityState.environment().gravityMilli() == 100, "Gravity did not persist: " + gravityState);
            helper.assertTrue(altitudeState.equals(altitudeStation.station())
                    && altitudeState.orbitalControls().distance() == AltitudeControllerMenu.GEO_DISTANCE
                    && altitudeState.orbitalControls().geostationary(), "Altitude did not persist: " + altitudeState);
            StationOrbitalControls controls = orientationState.orbitalControls();
            helper.assertTrue(orientationState.equals(orientationStation.station()) && controls.eastRate() == 10
                    && controls.upRate() == 0 && controls.northRate() == 0 && controls.epoch() > 0
                    && controls.epoch() <= reloaded.orbitalTick(), "Orientation did not persist: " + controls);
            // The orbit and region stay; only the named field changed.
            helper.assertTrue(orientationState.withOrbitalControls(orientationStation.created.orbitalControls())
                    .equals(orientationStation.created), "Orientation changed another field");
            // The command reaches the same service: a STOP right after the screen's write waits for the cooldown.
            orientationStation.run(orientationStation.ownerId, "arce station orientation stop");
            helper.assertTrue(replies.stream().anyMatch(line -> line.contains(
                    StationManagementCode.ORBITAL_COOLDOWN.description())), "STOP was not refused by the cooldown: "
                    + replies);
            orientationStation.run(orientationStation.ownerId, "arce station environment");
            helper.assertTrue(replies.get(replies.size() - 1).contains("sun_angle=270.0 distance=4 altitude=900km"
                    + " geo=false rates=10,0,0"), "The environment command does not show the controls: " + replies);
        } finally {
            fixtures.forEach(fixture -> clear(fixture, placed));
            fixtures.forEach(StationWarpGameTests.Fixture::close);
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "station_controls_menu_checks", timeoutTicks = 100)
    public static void screensAreRateLimitedAndCompareTheObservedStation(GameTestHelper helper) {
        StationWarpGameTests.Fixture fixture = new StationWarpGameTests.Fixture(helper, "Menu checks",
                CelestialIds.EARTH_ID, 0);
        List<BlockPos> placed = new ArrayList<>();
        boolean scheduled = false;
        try {
            ServerPlayer owner = owner(fixture, "menuChecks");
            StationControllerMenu menu = menu(fixture, owner, StationControllerKind.ORIENTATION,
                    place(fixture, placed, StationControllerKind.ORIENTATION, fixture.pad.offset(2, 0, 2)));
            // The station changes after the screen last showed it: the next change is refused.
            helper.assertTrue(fixture.data.checkedSetAltitude(fixture.server, fixture.station(), 30)
                    == StationRegistrySavedData.CheckedUpdate.COMMITTED, "The altitude fixture failed");
            click(helper, menu, owner, 2, StationManagementCode.STATION_CHANGED);
            click(helper, menu, owner, 2, StationManagementCode.CONTROL_RATE_LIMITED);
            helper.assertTrue(!menu.clickMenuButton(owner, 99), "An unknown button was accepted");
            helper.assertTrue(fixture.station().orbitalControls().eastRate() == 0, "A refused click changed the rate");
            scheduled = true;
            helper.runAfterDelay(RATE_TICKS, () -> {
                try {
                    menu.broadcastChanges();
                    click(helper, menu, owner, 2, StationManagementCode.ORIENTATION_SET);
                    helper.assertTrue(fixture.station().orbitalControls().eastRate() == 1, "The rate was not set");
                    // A fixed phase needs the rotation stopped first.
                    helper.runAfterDelay(RATE_TICKS, () -> {
                        try {
                            menu.broadcastChanges();
                            click(helper, menu, owner, OrientationControllerMenu.FIRST_PHASE_BUTTON + 1,
                                    StationManagementCode.ORIENTATION_ROTATING);
                        } finally {
                            clear(fixture, placed);
                            fixture.close();
                        }
                        helper.succeed();
                    });
                } catch (RuntimeException | Error failure) {
                    clear(fixture, placed);
                    fixture.close();
                    throw failure;
                }
            });
        } finally {
            if (!scheduled) {
                clear(fixture, placed);
                fixture.close();
            }
        }
    }

    @GameTest(template = "empty", batch = "station_controls_elevator_guard", timeoutTicks = 100)
    public static void theElevatorGuardRefusesAChangedValueAndAcceptsAnUnchangedOne(GameTestHelper helper) {
        StationWarpGameTests.Fixture fixture = new StationWarpGameTests.Fixture(helper, "Elevator guard",
                CelestialIds.EARTH_ID, 0);
        ElevatorStationGuard installed = ElevatorStationGuard.Installed.current();
        UUID stationId = fixture.id();
        ElevatorStationGuard.Decision[] decision = {ElevatorStationGuard.Decision.BOUND};
        try {
            ServerPlayer owner = owner(fixture, "elevatorGuard");
            ElevatorStationGuard.Installed.install(new ElevatorStationGuard() {
                @Override
                public Decision warp(MinecraftServer server, StationState station) {
                    return installed.warp(server, station);
                }

                @Override
                public Decision delete(MinecraftServer server, StationState station) {
                    return station.stationId().equals(stationId) ? decision[0] : installed.delete(server, station);
                }
            });
            var controls = StationRuntime.manager().orElseThrow().orbitalControls();
            StationActorContext command = StationActorContext.command(true);
            StationState before = fixture.station();
            expect(helper, controls.setAltitude(owner, command, Optional.empty(), 4).code(),
                    StationManagementCode.ALTITUDE_UNCHANGED, "an unchanged distance while bound");
            expect(helper, controls.setRates(owner, command, Optional.empty(), 0, 0, 0).code(),
                    StationManagementCode.ORIENTATION_UNCHANGED, "unchanged rates while bound");
            expect(helper, controls.stop(owner, command, Optional.empty()).code(),
                    StationManagementCode.ORIENTATION_UNCHANGED, "STOP of a stopped station while bound");
            expect(helper, controls.setAltitude(owner, command, Optional.empty(), 5).code(),
                    StationManagementCode.ELEVATOR_BOUND, "a changed distance while bound");
            expect(helper, controls.setRates(owner, command, Optional.empty(), 1, 0, 0).code(),
                    StationManagementCode.ELEVATOR_BOUND, "changed rates while bound");
            expect(helper, controls.setPhases(owner, command, Optional.empty(), 1, 0, 0).code(),
                    StationManagementCode.ELEVATOR_BOUND, "a changed fixed phase while bound");
            decision[0] = ElevatorStationGuard.Decision.UNAVAILABLE;
            expect(helper, controls.setAltitude(owner, command, Optional.empty(), 5).code(),
                    StationManagementCode.ENDGAME_UNAVAILABLE, "a changed distance while endgame data is down");
            helper.assertTrue(fixture.station().equals(before), "A guarded refusal changed the station");
            decision[0] = ElevatorStationGuard.Decision.ALLOWED;
            expect(helper, controls.setAltitude(owner, command, Optional.empty(), 5).code(),
                    StationManagementCode.ALTITUDE_SET, "a changed distance once unbound");
        } finally {
            ElevatorStationGuard.Installed.install(installed);
            fixture.close();
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "station_controls_heartbeat", timeoutTicks = 260)
    public static void aRotatingStationsSkyHeartbeatStaysWithinItsRate(GameTestHelper helper) {
        StationWarpGameTests.Fixture rotating = new StationWarpGameTests.Fixture(helper, "Heartbeat",
                CelestialIds.EARTH_ID, 0);
        StationWarpGameTests.Fixture still = new StationWarpGameTests.Fixture(helper, "Still sky",
                CelestialIds.EARTH_ID, 0);
        UUID viewerId = UUID.randomUUID();
        UUID calmId = UUID.randomUUID();
        List<Long> sentAt = new ArrayList<>();
        List<StationSkyContextPacket> packets = new ArrayList<>();
        List<StationSkyContextPacket> calmPackets = new ArrayList<>();
        Runnable cleanup = () -> {
            rotating.close();
            still.close();
        };
        try {
            rotating.keepLoaded();
            still.keepLoaded();
            long sample = rotating.data.orbitalTick();
            StationState observed = rotating.station();
            helper.assertTrue(rotating.data.checkedSetOrientation(rotating.server, observed,
                    observed.orbitalControls().withRates(60, -7, 3, sample), sample)
                    == StationRegistrySavedData.CheckedUpdate.COMMITTED, "The rotation fixture failed");
            rotating.join(viewerId, "heartbeatViewer");
            still.join(calmId, "heartbeatCalm");
        } catch (RuntimeException | Error failure) {
            cleanup.run();
            throw failure;
        }
        MinecraftServer server = rotating.server;
        StationSkyContextService service = new StationSkyContextService((player, packet) -> {
            if (player.getUUID().equals(viewerId)) {
                packets.add(packet);
                sentAt.add((long) server.getTickCount());
            } else if (player.getUUID().equals(calmId)) {
                calmPackets.add(packet);
            }
        });
        long start = server.getTickCount();
        helper.onEachTick(() -> {
            if (server.getTickCount() % StationSkyContextService.PASS_TICKS == 0) {
                service.pass(server);
            }
        });
        helper.runAfterDelay(200, () -> {
            try {
                long elapsed = server.getTickCount() - start;
                helper.assertTrue(!packets.isEmpty(), "The rotating station sent no context");
                for (int index = 1; index < sentAt.size(); index++) {
                    helper.assertTrue(sentAt.get(index) - sentAt.get(index - 1)
                            >= StationSkyContextService.HEARTBEAT_TICKS, "Heartbeats closer than 20 ticks: " + sentAt);
                }
                helper.assertTrue(packets.size() <= 1 + elapsed / StationSkyContextService.HEARTBEAT_TICKS
                                && packets.size() >= elapsed / StationSkyContextService.HEARTBEAT_TICKS - 1,
                        "Heartbeat count " + packets.size() + " over " + elapsed + " ticks");
                long previousSample = -1L;
                for (StationSkyContextPacket packet : packets) {
                    StationSkyContextPacket.Context context = packet.context().orElseThrow();
                    helper.assertTrue(context.controls().eastRate() == 60 && context.sample() > previousSample
                            && context.controls().epoch() <= context.sample(), "A heartbeat differs: " + context);
                    previousSample = context.sample();
                    FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
                    StationSkyContextPacket.encode(packet, buffer);
                    helper.assertTrue(buffer.readableBytes() <= StationSkyContextPacket.MAX_BYTES,
                            "A heartbeat exceeds 256 bytes");
                }
                helper.assertTrue(calmPackets.size() == 1, "A still station's context was re-sent: " + calmPackets.size());
                helper.assertTrue(service.lastSentTick(viewerId).filter(sentAt.get(sentAt.size() - 1)::equals)
                        .isPresent(), "The viewer's last send is not tracked");
            } finally {
                service.onServerStopping(null);
                cleanup.run();
            }
            helper.succeed();
        });
    }

    private static ServerPlayer owner(StationWarpGameTests.Fixture fixture, String name) {
        fixture.keepLoaded();
        fixture.join(fixture.ownerId, name);
        return fixture.server.getPlayerList().getPlayer(fixture.ownerId);
    }

    private static Block block(StationControllerKind kind) {
        Supplier<Block> block = switch (kind) {
            case WARP -> ModBlocks.WARP_CONTROLLER;
            case GRAVITY -> ModBlocks.STATION_GRAVITY_CONTROLLER;
            case ALTITUDE -> ModBlocks.STATION_ALTITUDE_CONTROLLER;
            case ORIENTATION -> ModBlocks.STATION_ORIENTATION_CONTROLLER;
        };
        return block.get();
    }

    private static BlockPos place(StationWarpGameTests.Fixture fixture, List<BlockPos> placed, StationControllerKind kind,
                                  BlockPos position) {
        fixture.space.getChunkAt(position); // Test setup only.
        fixture.space.setBlockAndUpdate(position, block(kind).defaultBlockState());
        placed.add(position);
        return position;
    }

    private static StationActorContext context(StationWarpGameTests.Fixture fixture, StationControllerKind kind,
                                               BlockPos position) {
        return StationActorContext.controller(fixture.space.dimension(), position, block(kind));
    }

    /** The server menu, as the controller block opens it; its first broadcast records the observed station. */
    private static StationControllerMenu menu(StationWarpGameTests.Fixture fixture, ServerPlayer player,
                                              StationControllerKind kind, BlockPos position) {
        StationControllerMenu.OpenData open = StationControllerMenu.openData(kind, player, position, fixture.station());
        StationControllerMenu menu = StationControllerMenu.server(kind, 9, player.getInventory(), fixture.space,
                position, block(kind), open);
        menu.broadcastChanges();
        return menu;
    }

    private static void click(GameTestHelper helper, StationControllerMenu menu, ServerPlayer player, int button,
                              StationManagementCode expected) {
        helper.assertTrue(menu.clickMenuButton(player, button), "Button " + button + " was not handled");
        helper.assertTrue(menu.status().equals(Optional.of(expected)),
                "Button " + button + " expected " + expected + " but was " + menu.status());
    }

    /** No countdown runs, and the orbit and the full warp balance are unchanged. */
    private static void unspent(GameTestHelper helper, StationWarpGameTests.Fixture fixture, String label) {
        helper.assertTrue(fixture.warp.countdown(fixture.id()).isEmpty(), label + ": a countdown started");
        helper.assertTrue(fixture.station().orbitBody().equals(fixture.created.orbitBody())
                && fixture.data.warpEnergy(fixture.id()) == 2_500_000, label + ": energy was spent or the orbit moved");
    }

    private static void expect(GameTestHelper helper, StationManagementCode actual, StationManagementCode expected,
                               String label) {
        helper.assertTrue(actual == expected, label + ": expected " + expected + " but was " + actual);
    }

    private static void clear(StationWarpGameTests.Fixture fixture, List<BlockPos> placed) {
        placed.forEach(position -> {
            if (fixture.space.getBlockState(position).getBlock() instanceof
                    io.github.sunthemoon.advancedrocketrycommunity.station.controller.StationControllerBlock) {
                fixture.space.setBlockAndUpdate(position, Blocks.AIR.defaultBlockState());
            }
        });
    }
}
