package io.github.sunthemoon.advancedrocketrycommunity.gametest;

import io.github.sunthemoon.advancedrocketrycommunity.AdvancedRocketryCommunity;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.CelestialIds;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.content.StarSystemContent;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.persistence.CelestialSavedData;
import io.github.sunthemoon.advancedrocketrycommunity.classiccontrol.network.ClassicControlViewPacket;
import io.github.sunthemoon.advancedrocketrycommunity.station.controller.StationControllerKind;
import io.github.sunthemoon.advancedrocketrycommunity.station.controller.StationControllerMenu;
import io.github.sunthemoon.advancedrocketrycommunity.station.controller.WarpControllerMenu;
import io.github.sunthemoon.advancedrocketrycommunity.station.service.StationManagementCode;
import io.github.sunthemoon.advancedrocketrycommunity.station.warp.StationRocketAuthority;
import io.netty.buffer.Unpooled;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/** W317: the real menu sends route availability from the same displayed quote as its price. */
@GameTestHolder(AdvancedRocketryCommunity.MOD_ID)
@PrefixGameTestTemplate(false)
public final class WarpControllerRouteWarningGameTests {
    private WarpControllerRouteWarningGameTests() { }

    @GameTest(template = "empty", batch = "station_controls_route_warning", timeoutTicks = 100)
    public static void routeFlagFollowsTheDisplayedQuoteAndClearsWithIt(GameTestHelper helper) {
        var driver = new GameTestTickDriver(helper);
        verifyTarget(helper, driver, StarSystemContent.TAU_CETI_E, "routeTau",
                () -> verifyTarget(helper, driver, StationWarpGameTests.MOON, "routeMoon", helper::succeed));
    }

    private static void verifyTarget(GameTestHelper helper, GameTestTickDriver driver, ResourceLocation target,
                                     String playerName, Runnable complete) {
        var fixture = new StationWarpGameTests.Fixture(helper, "Route warning", CelestialIds.EARTH_ID, 9_000_000);
        StationRocketAuthority previous = fixture.warp.installRocketAuthority(StationWarpGameTests.NOTHING_IN_MOTION);
        List<BlockPos> placed = new ArrayList<>();
        Runnable cleanup = () -> {
            fixture.warp.installRocketAuthority(previous);
            StationControlsGameTests.clear(fixture, placed);
            fixture.close();
        };
        boolean scheduled = false;
        try {
            CelestialSavedData discoveries = CelestialSavedData.get(fixture.server);
            if (discoveries.get(StarSystemContent.TAU_CETI_E).isEmpty()) {
                discoveries.discover(StarSystemContent.TAU_CETI_E, helper.getLevel().getGameTime());
            }
            ServerPlayer owner = StationControlsGameTests.owner(fixture, playerName);
            BlockPos controller = StationControlsGameTests.place(fixture, placed, StationControllerKind.WARP,
                    fixture.pad.offset(3, 0, 2));
            fixture.core(controller.offset(0, 0, 3));
            StationControllerMenu menu = ClassicControlsChannelGameTests.open(helper, owner, controller);
            assertNoQuote(helper, menu);
            List<ResourceLocation> targets = menu.names().subList(1, menu.names().size());
            int selected = targets.indexOf(target);
            helper.assertTrue(selected >= 0, "Target not offered: " + target);
            for (int step = 0; step < selected; step++) {
                StationControlsGameTests.send(menu, owner, WarpControllerMenu.BUTTON_NEXT, 0, 0L);
            }
            StationControlsGameTests.click(helper, menu, owner, WarpControllerMenu.BUTTON_REQUEST, selected, 0L,
                    StationManagementCode.WARP_ISSUED);
            assertQuote(helper, menu, target);
            // Refuse the same target while its quote is still live, without navigating first.
            StationControlsGameTests.click(helper, menu, owner, WarpControllerMenu.BUTTON_REQUEST, selected, 0L,
                    StationManagementCode.CONTROL_RATE_LIMITED);
            assertNoQuote(helper, menu);
            driver.after(11, () -> {
                try {
                    // Navigation must independently clear a fresh, live quote after the rate window.
                    StationControlsGameTests.click(helper, menu, owner, WarpControllerMenu.BUTTON_REQUEST,
                            selected, 0L, StationManagementCode.WARP_ISSUED);
                    assertQuote(helper, menu, target);
                    StationControlsGameTests.send(menu, owner, WarpControllerMenu.BUTTON_NEXT, 0, 0L);
                    assertNoQuote(helper, menu);
                    owner.closeContainer();
                    helper.assertTrue(fixture.station().orbitBody().equals(CelestialIds.EARTH_ID)
                            && fixture.data.warpEnergy(fixture.id()) == 9_000_000
                            && fixture.warp.countdown(fixture.id()).isEmpty(), "Viewing quotes changed the station");
                } finally {
                    cleanup.run();
                }
                complete.run();
            });
            scheduled = true;
        } finally {
            if (!scheduled) {
                cleanup.run();
            }
        }
    }

    private static void assertQuote(GameTestHelper helper, StationControllerMenu menu, ResourceLocation target) {
        ClassicControlViewPacket view = wireView(menu);
        boolean routed = target.equals(StationWarpGameTests.MOON);
        helper.assertTrue(view.value(WarpControllerMenu.TARGET_ORBIT_HAS_ROUTES) == (routed ? 1 : 0)
                        && view.value(WarpControllerMenu.COST) == (routed ? 2_000_000 : 8_000_000)
                        && view.value(WarpControllerMenu.COST_CLASS) == (routed ? 1 : 2)
                        && view.token() > 0L,
                "Route flag and price do not describe " + target + ": " + view);
        helper.assertTrue(WarpControllerMenu.showsRouteWarning(view) == !routed, "Wrong warning predicate");
        List<Component> lines = menu.lines(view);
        helper.assertTrue(lines.size() == (routed ? 4 : 5), "Wrong quoted lines: " + lines);
        if (!routed) {
            helper.assertTrue(lines.get(3).getContents() instanceof TranslatableContents cost
                            && cost.getKey().equals(StationControllerMenu.KEY + "cost")
                            && lines.get(4).getContents() instanceof TranslatableContents warning
                            && warning.getKey().equals(StationControllerMenu.KEY + "no_orbit_routes"),
                    "The warning does not immediately follow the price: " + lines);
        }
    }

    private static void assertNoQuote(GameTestHelper helper, StationControllerMenu menu) {
        ClassicControlViewPacket view = wireView(menu);
        helper.assertTrue(view.value(WarpControllerMenu.TARGET_ORBIT_HAS_ROUTES) == -1
                && view.value(WarpControllerMenu.COST_CLASS) == 0 && view.value(WarpControllerMenu.COST) == 0
                && view.token() == 0L && !WarpControllerMenu.showsRouteWarning(view)
                && menu.lines(view).size() == 3, "A missing quote still shows price or warning: " + view);
    }

    private static ClassicControlViewPacket wireView(StationControllerMenu menu) {
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            ClassicControlViewPacket.encode(menu.lastView().orElseThrow(), buffer);
            return ClassicControlViewPacket.decode(buffer);
        } finally {
            buffer.release();
        }
    }
}
