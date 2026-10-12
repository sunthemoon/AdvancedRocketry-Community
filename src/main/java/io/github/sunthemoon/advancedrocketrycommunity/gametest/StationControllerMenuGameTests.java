package io.github.sunthemoon.advancedrocketrycommunity.gametest;

import io.github.sunthemoon.advancedrocketrycommunity.AdvancedRocketryCommunity;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.CelestialIds;
import io.github.sunthemoon.advancedrocketrycommunity.classiccontrol.network.ClassicControlViewPacket;
import io.github.sunthemoon.advancedrocketrycommunity.station.controller.StationControllerKind;
import io.github.sunthemoon.advancedrocketrycommunity.station.controller.StationControllerMenu;
import io.github.sunthemoon.advancedrocketrycommunity.station.controller.WarpControllerMenu;
import io.github.sunthemoon.advancedrocketrycommunity.station.service.StationManagementCode;
import io.github.sunthemoon.advancedrocketrycommunity.station.warp.StationRocketAuthority;
import io.github.sunthemoon.advancedrocketrycommunity.station.warp.StationWarpRuntime;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/**
 * W07 review fixes on the real server, with menus opened through the controller block: a warp confirmation runs
 * only the quote its menu shows (R1).
 */
@GameTestHolder(AdvancedRocketryCommunity.MOD_ID)
@PrefixGameTestTemplate(false)
public final class StationControllerMenuGameTests {
    private static final int RATE_TICKS = 11;

    private StationControllerMenuGameTests() {
    }

    /**
     * R1, the review's three sequences on their own stations at once: Request A, then select B; Request A, then
     * close and reopen; Request A, then a failed Request B. A fourth station isolates a failed request of the same
     * target. Each Confirm with A's token is refused with no countdown and no energy spent, and the command finds
     * no confirmation either. A fresh quote of the menu still starts, once.
     */
    @GameTest(template = "empty", batch = "station_controls_confirm_binding", timeoutTicks = 200)
    public static void aConfirmationRunsOnlyTheQuoteItsMenuShows(GameTestHelper helper) {
        List<Warp> warps = new ArrayList<>();
        StationRocketAuthority previous = StationWarpRuntime.service().orElseThrow()
                .installRocketAuthority(StationWarpGameTests.NOTHING_IN_MOTION);
        Runnable cleanup = () -> {
            StationWarpRuntime.service().orElseThrow().installRocketAuthority(previous);
            warps.forEach(Warp::close);
        };
        boolean scheduled = false;
        try {
            Warp next = add(warps, new Warp(helper, "Bound next", "boundNext"));
            Warp reopen = add(warps, new Warp(helper, "Bound reopen", "boundReopen"));
            Warp failed = add(warps, new Warp(helper, "Bound failed", "boundFailed"));
            Warp retry = add(warps, new Warp(helper, "Bound retry", "boundRetry"));
            warps.forEach(Warp::open);
            warps.forEach(Warp::requestMoon);
            // Request A, then select B: the menu no longer shows A, and A's confirmation is withdrawn.
            StationControlsGameTests.send(next.menu, next.owner, WarpControllerMenu.BUTTON_NEXT, 0, 0L);
            noQuoteShown(helper, next.menu, "after Next");
            // Request A, then close and reopen: the new menu shows no quote.
            StationControllerMenu closed = reopen.menu;
            reopen.owner.closeContainer();
            reopen.open();
            helper.assertTrue(reopen.menu != closed, "Reopening returned the same menu");
            noQuoteShown(helper, reopen.menu, "after reopening");
            scheduled = true;
            helper.runAfterDelay(RATE_TICKS, () -> step(cleanup, () -> {
                next.confirmRefused();
                reopen.confirmRefused();
                // A request that fails: rockets in motion (or, for an interstellar B, more than the balance).
                StationWarpRuntime.service().orElseThrow().installRocketAuthority((server, station) ->
                        station.stationId().equals(failed.fixture.id())
                                || station.stationId().equals(retry.fixture.id()));
                StationControlsGameTests.send(failed.menu, failed.owner, WarpControllerMenu.BUTTON_NEXT, 0, 0L);
                failed.requestFails("Request B");
                retry.requestFails("Request A again");
                StationWarpRuntime.service().orElseThrow()
                        .installRocketAuthority(StationWarpGameTests.NOTHING_IN_MOTION);
                helper.runAfterDelay(RATE_TICKS, () -> step(cleanup, () -> {
                    failed.confirmRefused();
                    retry.confirmRefused();
                    helper.runAfterDelay(RATE_TICKS, () -> step(cleanup, () -> {
                        long fresh = retry.request();
                        helper.assertTrue(fresh != retry.token, "A new quote reused the old token");
                        helper.runAfterDelay(RATE_TICKS, () -> {
                            try {
                                StationControlsGameTests.click(helper, retry.menu, retry.owner,
                                        WarpControllerMenu.BUTTON_CONFIRM, 0, fresh, StationManagementCode.WARP_STARTED);
                                noQuoteShown(helper, retry.menu, "after the confirmation");
                                StationControlsGameTests.expect(helper, retry.fixture.warp.cancel(retry.owner,
                                                StationControlsGameTests.context(retry.fixture,
                                                        StationControllerKind.WARP, retry.controller)).code(),
                                        StationManagementCode.WARP_CANCELLED, "cancel the fresh quote's countdown");
                                warps.forEach(warp -> StationControlsGameTests.unspent(helper, warp.fixture,
                                        warp.fixture.created.name()));
                            } finally {
                                cleanup.run();
                            }
                            helper.succeed();
                        });
                    }));
                }));
            }));
        } finally {
            if (!scheduled) {
                cleanup.run();
            }
        }
    }

    private static Warp add(List<Warp> warps, Warp warp) {
        warps.add(warp);
        return warp;
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

    private static ClassicControlViewPacket shown(StationControllerMenu menu) {
        return menu.lastView().orElseThrow();
    }

    /** The holder's view offers no Confirm. */
    private static void noQuoteShown(GameTestHelper helper, StationControllerMenu menu, String label) {
        helper.assertTrue(shown(menu).token() == 0L, label + ": the view still offers a confirmation");
        helper.assertTrue(menu.intent(WarpControllerMenu.BUTTON_CONFIRM, menu.lastView()).isEmpty(),
                label + ": the screen could still send Confirm");
    }

    /** One charged station with its owner, a warp controller and a core 3 blocks from it. */
    private static final class Warp {
        final GameTestHelper helper;
        final StationWarpGameTests.Fixture fixture;
        final List<BlockPos> placed = new ArrayList<>();
        final ServerPlayer owner;
        final BlockPos controller;
        StationControllerMenu menu;
        /** The token of the first quote, for A. */
        long token;

        Warp(GameTestHelper helper, String station, String player) {
            this.helper = helper;
            fixture = new StationWarpGameTests.Fixture(helper, station, CelestialIds.EARTH_ID, 2_500_000);
            owner = StationControlsGameTests.owner(fixture, player);
            controller = StationControlsGameTests.place(fixture, placed, StationControllerKind.WARP,
                    fixture.pad.offset(3, 0, 2));
            fixture.core(controller.offset(0, 0, 3));
        }

        void open() {
            menu = ClassicControlsChannelGameTests.open(helper, owner, controller);
        }

        /** Selects the Moon and requests it; {@link #token} is the token its view offers. */
        void requestMoon() {
            List<?> targets = menu.names().subList(1, menu.names().size());
            int moon = targets.indexOf(StationWarpGameTests.MOON);
            helper.assertTrue(moon >= 0, "The warp screen does not offer the Moon: " + menu.names());
            for (int step = 0; step < moon; step++) {
                StationControlsGameTests.send(menu, owner, WarpControllerMenu.BUTTON_NEXT, 0, 0L);
            }
            token = request();
        }

        /** Requests the selected target, which is issued; returns the token the view offers for it. */
        long request() {
            int selected = shown(menu).value(WarpControllerMenu.SELECTED);
            StationControlsGameTests.click(helper, menu, owner, WarpControllerMenu.BUTTON_REQUEST, selected, 0L,
                    StationManagementCode.WARP_ISSUED);
            long offered = shown(menu).token();
            helper.assertTrue(offered > 0L && menu.intent(WarpControllerMenu.BUTTON_CONFIRM, menu.lastView())
                    .filter(intent -> intent.token() == offered).isPresent(), "The issued quote offers no Confirm");
            return offered;
        }

        /** Requests the selected target, which is refused; the menu then offers no confirmation. */
        void requestFails(String label) {
            int selected = shown(menu).value(WarpControllerMenu.SELECTED);
            StationControlsGameTests.send(menu, owner, WarpControllerMenu.BUTTON_REQUEST, selected, 0L);
            helper.assertTrue(menu.status().filter(code -> !code.success()).isPresent(),
                    fixture.created.name() + ": " + label + " did not fail: " + menu.status());
            noQuoteShown(helper, menu, fixture.created.name() + " after " + label);
        }

        /** Confirm with the first quote's token is refused, and the command finds no confirmation either. */
        void confirmRefused() {
            StationControlsGameTests.click(helper, menu, owner, WarpControllerMenu.BUTTON_CONFIRM, 0, token,
                    StationManagementCode.WARP_NO_CONFIRMATION);
            StationControlsGameTests.expect(helper, fixture.warp.confirm(owner, true, fixture.id()).code(),
                    StationManagementCode.WARP_NO_CONFIRMATION, fixture.created.name() + ": the command confirmation");
            StationControlsGameTests.unspent(helper, fixture, fixture.created.name());
        }

        void close() {
            StationControlsGameTests.clear(fixture, placed);
            fixture.close();
        }
    }
}
