package io.github.sunthemoon.advancedrocketrycommunity.gametest;

import com.mojang.authlib.GameProfile;
import io.github.sunthemoon.advancedrocketrycommunity.AdvancedRocketryCommunity;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.CelestialIds;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.network.StationSkyContextPacket;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.entity.RocketEntity;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.menu.RocketNavigationStatus;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.server.RocketRuntime;
import io.github.sunthemoon.advancedrocketrycommunity.station.controller.StationControllerKind;
import io.github.sunthemoon.advancedrocketrycommunity.station.controller.StationControllerMenu;
import io.github.sunthemoon.advancedrocketrycommunity.station.controller.WarpControllerMenu;
import io.github.sunthemoon.advancedrocketrycommunity.station.model.StationState;
import io.github.sunthemoon.advancedrocketrycommunity.station.orbit.StationSkyContextRuntime;
import io.github.sunthemoon.advancedrocketrycommunity.station.persistence.StationRegistrySavedData;
import io.github.sunthemoon.advancedrocketrycommunity.station.service.StationManagementCode;
import io.github.sunthemoon.advancedrocketrycommunity.station.warp.StationRocketAuthority;
import io.github.sunthemoon.advancedrocketrycommunity.travel.model.TravelTarget;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/**
 * W308: the shared Space body is not a warp target. Its warp left a consistent record, but no rocket route reaches or
 * leaves an orbit of it, so the warp controller does not list it and a request is refused with the reason. A station
 * that already orbits it, from an earlier build or an operator's creation, still warps away through the real path
 * (the warp controller's request, cost, confirmation, countdown and commit) and is reachable again.
 */
@GameTestHolder(AdvancedRocketryCommunity.MOD_ID)
@PrefixGameTestTemplate(false)
public final class StationWarpSpaceGameTests {
    private static final int RATE_TICKS = 11;
    private static final int BALANCE = 2_500_000;
    private static final int IN_SYSTEM_COST = 2_000_000;

    private StationWarpSpaceGameTests() {
    }

    @GameTest(template = "rocket_test", batch = "station_warp_space", timeoutTicks = 400)
    public static void theSpaceBodyIsNoWarpTargetAndAStationInItsOrbitCanStillLeave(GameTestHelper helper) {
        var driver = new GameTestTickDriver(helper);
        StationWarpGameTests.Fixture earth = new StationWarpGameTests.Fixture(helper, "Earth orbit guard",
                CelestialIds.EARTH_ID, BALANCE);
        StationWarpGameTests.Fixture inSpace = new StationWarpGameTests.Fixture(helper, "Space orbit guard",
                CelestialIds.SPACE_ID, BALANCE);
        StationRocketAuthority previous = earth.warp.installRocketAuthority(StationWarpGameTests.NOTHING_IN_MOTION);
        List<BlockPos> earthPlaced = new ArrayList<>();
        List<BlockPos> spacePlaced = new ArrayList<>();
        RocketEntity[] rocket = new RocketEntity[1];
        boolean[] closed = {false};
        Runnable cleanup = () -> {
            if (closed[0]) {
                return;
            }
            closed[0] = true;
            if (rocket[0] != null) {
                rocket[0].discard();
            }
            earth.warp.installRocketAuthority(previous);
            StationControlsGameTests.clear(earth, earthPlaced);
            StationControlsGameTests.clear(inSpace, spacePlaced);
            earth.close();
            inSpace.close();
        };
        boolean scheduled = false;
        try {
            earth.keepLoaded();
            List<String> earthReplies = earth.join(earth.ownerId, "spaceGuardEarth");
            ServerPlayer earthOwner = earth.server.getPlayerList().getPlayer(earth.ownerId);
            ServerPlayer spaceOwner = StationControlsGameTests.owner(inSpace, "spaceGuardSpace");
            UUID navigatorId = UUID.randomUUID();
            for (StationWarpGameTests.Fixture fixture : List.of(earth, inSpace)) {
                fixture.data.invite(fixture.id(), navigatorId);
                fixture.data.acceptInvitation(fixture.id(), navigatorId);
            }
            earth.core(earth.pad.east(2));
            BlockPos earthController = StationControlsGameTests.place(earth, earthPlaced, StationControllerKind.WARP,
                    earth.pad.offset(3, 0, 2));
            BlockPos spaceController = StationControlsGameTests.place(inSpace, spacePlaced, StationControllerKind.WARP,
                    inSpace.pad.offset(3, 0, 2));
            inSpace.core(spaceController.offset(0, 0, 3));
            scheduled = true;
            RocketFlightGameTestFixtures.assembleFueledRocket(helper, driver, new BlockPos(3, 2, 3), navigatorId,
                    cleanup, assembled -> step(cleanup, () -> {
                        rocket[0] = assembled;
                        ServerPlayer navigator = RocketFlightConsoleFixtures.standIn(helper.getLevel(),
                                new GameProfile(navigatorId, "spaceGuardNavigator"));
                        navigator.setPos(assembled.position());

                        // An Earth-orbit station: the screen does not list the Space body, and a request is refused
                        // with the reason through the command and the screen context, with nothing issued or spent.
                        StationControllerMenu earthMenu = StationControlsGameTests.menu(earth, earthOwner,
                                StationControllerKind.WARP, earthController);
                        List<?> earthTargets = targets(earthMenu);
                        helper.assertTrue(earthTargets.contains(CelestialIds.MOON_ID)
                                        && !earthTargets.contains(CelestialIds.SPACE_ID),
                                "The warp screen's targets differ: " + earthTargets);
                        earth.look(earth.ownerId, earth.pad.east(2));
                        earth.run(earth.ownerId, "arce station warp " + CelestialIds.SPACE_ID);
                        StationWarpGameTests.expect(helper, earthReplies, StationManagementCode.WARP_TARGET_UNAVAILABLE,
                                "the command request for the Space body");
                        StationControlsGameTests.expect(helper, earth.warp.request(earthOwner,
                                        StationControlsGameTests.context(earth, StationControllerKind.WARP, earthController),
                                        CelestialIds.SPACE_ID).code(), StationManagementCode.WARP_TARGET_UNAVAILABLE,
                                "the screen-context request for the Space body");
                        earth.run(earth.ownerId, "arce station warp confirm " + earth.id());
                        StationWarpGameTests.expect(helper, earthReplies, StationManagementCode.WARP_NO_CONFIRMATION,
                                "a confirmation after the refused request");
                        StationControlsGameTests.unspent(helper, earth, "the refused Space request");
                        helper.assertTrue(status(navigator, assembled, earth) == RocketNavigationStatus.READY,
                                "Fixture: an Earth rocket cannot reach the Earth-orbit station");

                        // A station already in the Space body's orbit: no rocket route reaches it, and the sky names
                        // the Space body as its planet. Its own screen offers other targets but not the Space body.
                        helper.assertTrue(status(navigator, assembled, inSpace) == RocketNavigationStatus.NO_ROUTE,
                                "A rocket route now reaches the Space body's orbit; W308's exclusion needs review: "
                                        + status(navigator, assembled, inSpace));
                        helper.assertTrue(skyBody(inSpace, spaceOwner).filter(CelestialIds.SPACE_ID::equals).isPresent(),
                                "The Space-orbit station's sky context differs: " + skyBody(inSpace, spaceOwner));
                        StationControllerMenu spaceMenu = StationControlsGameTests.menu(inSpace, spaceOwner,
                                StationControllerKind.WARP, spaceController);
                        List<?> spaceTargets = targets(spaceMenu);
                        int earthIndex = spaceTargets.indexOf(CelestialIds.EARTH_ID);
                        helper.assertTrue(spaceMenu.names().get(0).equals(CelestialIds.SPACE_ID) && earthIndex >= 0
                                        && !spaceTargets.contains(CelestialIds.SPACE_ID),
                                "The Space-orbit station's screen differs: " + spaceMenu.names());
                        for (int index = 0; index < earthIndex; index++) {
                            StationControlsGameTests.send(spaceMenu, spaceOwner, WarpControllerMenu.BUTTON_NEXT, 0, 0L);
                        }
                        int selected = spaceMenu.lastView().orElseThrow().value(WarpControllerMenu.SELECTED);
                        helper.assertTrue(selected == earthIndex, "The view does not show Earth selected: " + selected);
                        StationControlsGameTests.click(helper, spaceMenu, spaceOwner, WarpControllerMenu.BUTTON_REQUEST,
                                selected, 0L, StationManagementCode.WARP_ISSUED);
                        var quoted = spaceMenu.lastView().orElseThrow();
                        helper.assertTrue(quoted.value(WarpControllerMenu.COST) == IN_SYSTEM_COST
                                        && quoted.value(WarpControllerMenu.COST_CLASS) == 1,
                                "Leaving the Space body's orbit is not the in-system cost: cost="
                                        + quoted.value(WarpControllerMenu.COST) + " class="
                                        + quoted.value(WarpControllerMenu.COST_CLASS));
                        StationState before = inSpace.station();
                        driver.after(RATE_TICKS, () -> step(cleanup, () -> {
                            spaceMenu.broadcastChanges();
                            long token = spaceMenu.lastView().orElseThrow().token();
                            helper.assertTrue(token > 0L, "The view shows no quote token");
                            StationControlsGameTests.click(helper, spaceMenu, spaceOwner, WarpControllerMenu.BUTTON_CONFIRM,
                                    0, token, StationManagementCode.WARP_STARTED);
                            driver.after(StationWarpGameTests.COMMIT_DELAY, () -> {
                                try {
                                    left(helper, inSpace, spaceOwner, navigator, assembled, before);
                                    StationControlsGameTests.unspent(helper, earth, "the Earth-orbit station afterwards");
                                } finally {
                                    cleanup.run();
                                }
                                helper.succeed();
                            });
                        }));
                    }));
        } finally {
            if (!scheduled) {
                cleanup.run();
            }
        }
    }

    /** The station left the Space body's orbit for Earth's: charged once, on disk, reachable and with Earth's sky. */
    private static void left(GameTestHelper helper, StationWarpGameTests.Fixture fixture, ServerPlayer owner,
                             ServerPlayer navigator, RocketEntity rocket, StationState before) {
        StationState after = fixture.station();
        helper.assertTrue(after.equals(before.withOrbitBody(CelestialIds.EARTH_ID)),
                "Leaving the Space body's orbit did not relocate only the orbit: " + after.orbitBody());
        helper.assertTrue(fixture.data.warpEnergy(fixture.id()) == BALANCE - IN_SYSTEM_COST,
                "The warp was not charged once: " + fixture.data.warpEnergy(fixture.id()));
        helper.assertTrue(fixture.warp.countdown(fixture.id()).isEmpty(), "The countdown is still running");
        StationRegistrySavedData onDisk = StationRegistrySavedData.load(fixture.stationFile());
        helper.assertTrue(onDisk.find(fixture.id()).filter(after::equals).isPresent(), "The checked write is not on disk");
        helper.assertTrue(status(navigator, rocket, fixture) == RocketNavigationStatus.READY,
                "An Earth rocket cannot reach the station in Earth orbit: " + status(navigator, rocket, fixture));
        helper.assertTrue(skyBody(fixture, owner).filter(CelestialIds.EARTH_ID::equals).isPresent(),
                "The sky context did not follow the orbit: " + skyBody(fixture, owner));
    }

    private static List<?> targets(StationControllerMenu menu) {
        return menu.names().subList(1, menu.names().size());
    }

    /** The navigator's quote, from the rocket in the GameTest structure, for a flight to the station's pad. */
    private static RocketNavigationStatus status(ServerPlayer navigator, RocketEntity rocket,
                                                 StationWarpGameTests.Fixture fixture) {
        return RocketRuntime.navigation(navigator, rocket).quotes().forTarget(new TravelTarget.Station(fixture.id()))
                .status();
    }

    /** The body of the planetary sky context the server derives for a player in the station. */
    private static Optional<ResourceLocation> skyBody(StationWarpGameTests.Fixture fixture, ServerPlayer player) {
        return StationSkyContextRuntime.service().orElseThrow().contextFor(fixture.server, fixture.data, player)
                .filter(context -> context.kind() == StationSkyContextPacket.SkyKind.PLANETARY)
                .flatMap(StationSkyContextPacket.Context::body);
    }

    /** Runs one scheduled step; a failure cleans up before it fails the test. */
    private static void step(Runnable cleanup, Runnable body) {
        try {
            body.run();
        } catch (RuntimeException | Error failure) {
            cleanup.run();
            throw failure;
        }
    }
}
