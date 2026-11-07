package io.github.sunthemoon.advancedrocketrycommunity.client;

import static org.junit.jupiter.api.Assertions.*;

import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import io.github.sunthemoon.advancedrocketrycommunity.station.notice.StationNotice;
import io.github.sunthemoon.advancedrocketrycommunity.station.notice.StationNoticeKind;
import io.github.sunthemoon.advancedrocketrycommunity.station.notice.StationNoticeService;
import io.github.sunthemoon.advancedrocketrycommunity.station.service.StationManagementCode;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class WarpScreenNoticeTest {
    @BeforeAll static void bootstrap() { MinecraftBootstrap.initialize(); }
    @AfterEach void clear() throws Exception { notices().clear(); }

    @Test void scheduledAnnouncementsReplaceThePreviousSecondsAndCancellationReplacesTheCountdown() throws Exception {
        Object screen = new Object();
        var state = notices();
        Component ten = countdown("Station", "moon", 10);
        Component five = countdown("Station", "moon", 5);
        ScreenActionMessages.receive(screen, true, true, ten, 0);
        ScreenActionMessages.receive(screen, true, true, five, 5000);
        assertEquals(five, state.current(screen, 5001).orElseThrow());
        ScreenActionMessages.receive(screen, true, false, Component.literal("Unrelated chat"), 5001);
        ScreenActionMessages.receive(screen, true, false, aborted("Other", "moon"), 5001);
        assertEquals(five, state.current(screen, 5001).orElseThrow());
        Component cancelled = aborted("Station", "moon");
        assertTrue(WarpScreenNotice.replacesCountdown(state.current(screen, 5001).orElseThrow(), cancelled));
        ScreenActionMessages.receive(screen, true, false, cancelled, 5001);
        assertEquals(cancelled, state.current(screen, 5001).orElseThrow());
        assertEquals(cancelled, state.current(screen, 11_000).orElseThrow());
        assertTrue(state.current(screen, 11_001).isEmpty());
    }

    @Test void onlyTheSameStationAndTargetOutcomeReplacesAStructuredCountdown() {
        Component current = countdown("Station", "moon", 1);
        Component committed = StationNoticeService.component(StationNotice.committed("Station", ModIdentity.id("earth"),
                ModIdentity.id("moon"), "in_system", 1, 2));
        assertTrue(WarpScreenNotice.replacesCountdown(current, committed));
        assertTrue(WarpScreenNotice.replacesCountdown(current, aborted("Station", "moon")));
        assertFalse(WarpScreenNotice.replacesCountdown(current, aborted("Other", "moon")));
        assertFalse(WarpScreenNotice.replacesCountdown(current, aborted("Station", "mars")));
        assertFalse(WarpScreenNotice.replacesCountdown(null, committed));
        assertFalse(WarpScreenNotice.replacesCountdown(current, null));
        assertFalse(WarpScreenNotice.replacesCountdown(current, Component.literal("Warp aborted")));
        assertFalse(WarpScreenNotice.replacesCountdown(Component.literal("Warp in 1 s"), committed));
        assertFalse(WarpScreenNotice.replacesCountdown(current, Component.translatable(
                StationNoticeKind.WARP_ABORTED.key(), "Station")));
        assertFalse(WarpScreenNotice.replacesCountdown(current, countdown("Station", "moon", 1)));
        assertTrue(WarpScreenNotice.isNotice(current));
        assertTrue(WarpScreenNotice.isNotice(committed));
        assertFalse(WarpScreenNotice.isNotice(Component.literal("Chat")));
    }

    private static Component countdown(String station, String target, int seconds) {
        return StationNoticeService.component(StationNotice.countdown(station, ModIdentity.id(target), seconds));
    }

    private static Component aborted(String station, String target) {
        return StationNoticeService.component(StationNotice.aborted(station, StationManagementCode.WARP_CANCELLED,
                ModIdentity.id(target)));
    }

    @SuppressWarnings("unchecked")
    private static ScreenMessageState<Component> notices() throws Exception {
        var field = ScreenActionMessages.class.getDeclaredField("NOTICE");
        field.setAccessible(true);
        return (ScreenMessageState<Component>) field.get(null);
    }
}
