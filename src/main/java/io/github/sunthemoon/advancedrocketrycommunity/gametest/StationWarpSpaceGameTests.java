package io.github.sunthemoon.advancedrocketrycommunity.gametest;

import com.mojang.authlib.GameProfile;
import io.github.sunthemoon.advancedrocketrycommunity.AdvancedRocketryCommunity;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.CelestialIds;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.model.CelestialBodyDefinition;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.network.StationSkyContextPacket;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.device.EndgameDevices;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.service.EndgameRuntime;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.entity.RocketEntity;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.menu.RocketNavigation;
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
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/**
 * W308: a station warped to the shared Space body through the real warp path (the warp controller's target list,
 * request, cost, confirmation, countdown and commit), and the state it is left in: orbit, balance, sky context,
 * rocket navigation to its pad and the orbital laser's eligibility.
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
    public static void aWarpToTheSpaceBodyCommitsAndLeavesTheStationWithoutRocketRoutes(GameTestHelper helper) {
        var driver = new GameTestTickDriver(helper);
        StationWarpGameTests.Fixture fixture = new StationWarpGameTests.Fixture(helper, "Space warp",
                CelestialIds.EARTH_ID, BALANCE);
        StationRocketAuthority previous = fixture.warp.installRocketAuthority(StationWarpGameTests.NOTHING_IN_MOTION);
        List<BlockPos> placed = new ArrayList<>();
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
            fixture.warp.installRocketAuthority(previous);
            StationControlsGameTests.clear(fixture, placed);
            fixture.close();
        };
        boolean scheduled = false;
        try {
            ServerPlayer owner = StationControlsGameTests.owner(fixture, "spaceWarpOwner");
            UUID navigatorId = UUID.randomUUID();
            fixture.data.invite(fixture.id(), navigatorId);
            fixture.data.acceptInvitation(fixture.id(), navigatorId);
            BlockPos controller = StationControlsGameTests.place(fixture, placed, StationControllerKind.WARP,
                    fixture.pad.offset(3, 0, 2));
            fixture.core(controller.offset(0, 0, 3));
            scheduled = true;
            RocketFlightGameTestFixtures.assembleFueledRocket(helper, driver, new BlockPos(3, 2, 3), navigatorId,
                    cleanup, assembled -> step(cleanup, () -> {
                        rocket[0] = assembled;
                        ServerPlayer navigator = RocketFlightConsoleFixtures.standIn(helper.getLevel(),
                                new GameProfile(navigatorId, "spaceWarpNavigator"));
                        navigator.setPos(assembled.position());
                        TravelTarget station = new TravelTarget.Station(fixture.id());
                        RocketNavigation earthOrbit = RocketRuntime.navigation(navigator, assembled);
                        helper.assertTrue(earthOrbit.stations().stream().anyMatch(view -> view.stationId().equals(fixture.id()))
                                        && earthOrbit.quotes().forTarget(station).status() == RocketNavigationStatus.READY,
                                "Fixture: an Earth rocket cannot reach the Earth-orbit station: "
                                        + earthOrbit.quotes().forTarget(station).status());

                        StationControllerMenu menu = StationControlsGameTests.menu(fixture, owner,
                                StationControllerKind.WARP, controller);
                        List<?> targets = menu.names().subList(1, menu.names().size());
                        int space = targets.indexOf(CelestialIds.SPACE_ID);
                        helper.assertTrue(space >= 0, "The warp screen does not offer the Space body: " + menu.names());
                        for (int index = 0; index < space; index++) {
                            StationControlsGameTests.send(menu, owner, WarpControllerMenu.BUTTON_NEXT, 0, 0L);
                        }
                        int selected = menu.lastView().orElseThrow().value(WarpControllerMenu.SELECTED);
                        helper.assertTrue(selected == space, "The view does not show the Space body selected: " + selected);
                        StationControlsGameTests.click(helper, menu, owner, WarpControllerMenu.BUTTON_REQUEST, selected, 0L,
                                StationManagementCode.WARP_ISSUED);
                        var quoted = menu.lastView().orElseThrow();
                        helper.assertTrue(quoted.value(WarpControllerMenu.COST) == IN_SYSTEM_COST
                                        && quoted.value(WarpControllerMenu.COST_CLASS) == 1,
                                "The Space quote is not the in-system cost: cost=" + quoted.value(WarpControllerMenu.COST)
                                        + " class=" + quoted.value(WarpControllerMenu.COST_CLASS));
                        StationState before = fixture.station();
                        driver.after(RATE_TICKS, () -> step(cleanup, () -> {
                            menu.broadcastChanges();
                            long token = menu.lastView().orElseThrow().token();
                            helper.assertTrue(token > 0L, "The view shows no quote token");
                            StationControlsGameTests.click(helper, menu, owner, WarpControllerMenu.BUTTON_CONFIRM, 0, token,
                                    StationManagementCode.WARP_STARTED);
                            driver.after(StationWarpGameTests.COMMIT_DELAY, () -> {
                                try {
                                    observeAfterTheWarp(helper, fixture, owner, navigator, assembled, before);
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

    /** What a committed warp to the Space body leaves: everything the station's orbit feeds. */
    private static void observeAfterTheWarp(GameTestHelper helper, StationWarpGameTests.Fixture fixture,
                                            ServerPlayer owner, ServerPlayer navigator, RocketEntity rocket,
                                            StationState before) {
        // The warp completes: only the orbit changed, the in-system cost was debited once, and it is on disk.
        StationState after = fixture.station();
        helper.assertTrue(after.equals(before.withOrbitBody(CelestialIds.SPACE_ID)),
                "The warp to the Space body did not relocate only the orbit: " + after.orbitBody());
        helper.assertTrue(fixture.data.warpEnergy(fixture.id()) == BALANCE - IN_SYSTEM_COST,
                "The warp was not charged once: " + fixture.data.warpEnergy(fixture.id()));
        helper.assertTrue(fixture.warp.countdown(fixture.id()).isEmpty(), "The countdown is still running");
        StationRegistrySavedData onDisk = StationRegistrySavedData.load(fixture.stationFile());
        helper.assertTrue(onDisk.find(fixture.id()).filter(after::equals).isPresent(), "The checked write is not on disk");
        // The pad stays where it was (logical relocation, ADR-044 section 1).
        helper.assertTrue(after.landingPad().equals(fixture.created.landingPad())
                && after.region().equals(fixture.created.region()), "The pad or region moved");

        // Sky: the server names the Space body itself as the planet the station orbits.
        Optional<StationSkyContextPacket.Context> sky = StationSkyContextRuntime.service().orElseThrow()
                .contextFor(fixture.server, fixture.data, owner);
        helper.assertTrue(sky.filter(context -> context.kind() == StationSkyContextPacket.SkyKind.PLANETARY
                        && context.body().filter(CelestialIds.SPACE_ID::equals).isPresent()).isPresent(),
                "The sky context differs: " + sky);
        UUID visitorId = UUID.randomUUID();
        List<String> replies = fixture.join(visitorId, "spaceWarpVisitor");
        fixture.run(visitorId, "arce station environment");
        helper.assertTrue(StationWarpGameTests.last(replies).contains(" orbit=" + CelestialIds.SPACE_ID + " "),
                "The environment does not follow the orbit: " + replies);

        // Rockets: the station is still offered as a destination, but no route reaches an orbit of the Space body.
        TravelTarget station = new TravelTarget.Station(fixture.id());
        RocketNavigation inSpace = RocketRuntime.navigation(navigator, rocket);
        helper.assertTrue(inSpace.stations().stream().anyMatch(view -> view.stationId().equals(fixture.id())
                        && view.orbitBody().equals(CelestialIds.SPACE_ID)),
                "The warped station is not listed: " + inSpace.stations());
        helper.assertTrue(inSpace.quotes().forTarget(station).status() == RocketNavigationStatus.NO_ROUTE,
                "A rocket route reaches the Space body's orbit: " + inSpace.quotes().forTarget(station).status());

        // Orbital laser: no drill table for the Space body, and no surface for a physical drill.
        EndgameDevices devices = EndgameRuntime.devices().orElseThrow();
        CelestialBodyDefinition body = devices.celestial().orElseThrow().get(CelestialIds.SPACE_ID).orElseThrow();
        helper.assertTrue(devices.laserTables().forBody(body).isEmpty() && !body.supportsSurfaceArrival(),
                "The orbital laser would mine the Space body");
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
