package io.github.sunthemoon.advancedrocketrycommunity.client;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class ScreenMessageStateTest {
    @Test void onlyAnOverlayReceivedWhileAModScreenIsOpenIsMirrored() {
        var state = new ScreenMessageState<String>();
        Object screen = new Object();
        state.accept(null, true, true, "no screen", 1);
        state.accept(screen, false, true, "foreign screen", 1);
        state.accept(screen, true, false, "chat", 1);
        assertTrue(state.current(screen, 1).isEmpty());
        state.accept(screen, true, true, "Not authorized [UNAUTHORIZED]", 1);
        assertEquals("Not authorized [UNAUTHORIZED]", state.current(screen, 1).orElseThrow());
    }

    @Test void progressReplacesRefusalAndRefreshesTheBoundedLifetime() {
        var state = new ScreenMessageState<String>();
        Object screen = new Object();
        state.accept(screen, true, true, "refusal", 100);
        state.accept(screen, true, true, "Ride starting: stay on the platform", 200);
        assertEquals("Ride starting: stay on the platform", state.current(screen, 6199).orElseThrow());
        assertTrue(state.current(screen, 6200).isEmpty());
    }

    @Test void closingSwitchingDisconnectingAndClockRollbackClearTheNotice() {
        var state = new ScreenMessageState<String>();
        Object screen = new Object();
        state.accept(screen, true, true, "a", 100);
        assertTrue(state.current(new Object(), 100).isEmpty());
        assertTrue(state.current(screen, 100).isEmpty());
        state.accept(screen, true, true, "b", 100);
        state.clear();
        assertTrue(state.current(screen, 100).isEmpty());
        state.accept(screen, true, true, "c", 100);
        assertTrue(state.current(null, 100).isEmpty());
        state.accept(screen, true, true, "d", 100);
        assertTrue(state.current(screen, 99).isEmpty());
    }
}
