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

    /** W311 (finding 7-5): the console's launch refusal vanished after the timed lifetime. */
    @Test void aPinnedRefusalOutlivesTheLifetimeUntilItsOwnerReleasesIt() {
        var state = new ScreenMessageState<String>();
        Object screen = new Object();
        state.accept(screen, true, true, "Landing pad refused", 100, true);
        assertEquals("Landing pad refused", state.current(screen, 100 + 10 * ScreenMessageState.VISIBLE_MILLIS).orElseThrow());
        state.release(new Object());
        assertEquals("Landing pad refused", state.current(screen, 100_000).orElseThrow());
        state.release(screen);
        assertTrue(state.current(screen, 100_000).isEmpty());
    }

    @Test void aNewerTimedNoticeShowsFirstAndThePinnedRefusalReturnsAfterIt() {
        var state = new ScreenMessageState<String>();
        Object screen = new Object();
        state.accept(screen, true, true, "refused", 100, true);
        state.accept(screen, true, true, "progress", 200);
        assertEquals("progress", state.current(screen, 6199).orElseThrow());
        assertEquals("refused", state.current(screen, 6200).orElseThrow());
        state.accept(screen, true, true, "refused again", 7000, true);
        assertEquals("refused again", state.current(screen, 7000).orElseThrow());
        state.accept(screen, true, true, "later", 8000);
        state.release(screen);
        assertEquals("later", state.current(screen, 8000).orElseThrow(), "A release keeps a timed notice");
        assertTrue(state.current(screen, 14_000).isEmpty());
    }

    @Test void switchingClosingAndIgnoredMessagesNeverKeepAPinnedRefusal() {
        var state = new ScreenMessageState<String>();
        Object screen = new Object();
        state.accept(screen, false, true, "foreign", 1, true);
        state.accept(screen, true, false, "chat", 1, true);
        assertTrue(state.current(screen, 1).isEmpty());
        state.accept(screen, true, true, "refused", 1, true);
        assertTrue(state.current(new Object(), 1).isEmpty());
        assertTrue(state.current(screen, 1).isEmpty());
        state.accept(screen, true, true, "refused", 1, true);
        Object next = new Object();
        state.accept(next, true, true, "other screen", 2);
        assertTrue(state.current(next, 6002).isEmpty(), "Another screen's notice inherited the refusal");
        state.accept(screen, true, true, "refused", 1, true);
        state.clear();
        assertTrue(state.current(screen, 1).isEmpty());
    }
}
