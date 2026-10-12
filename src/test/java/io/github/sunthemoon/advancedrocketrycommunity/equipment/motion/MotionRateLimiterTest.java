package io.github.sunthemoon.advancedrocketrycommunity.equipment.motion;

import static org.junit.jupiter.api.Assertions.*;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class MotionRateLimiterTest {
    private static final UUID ALICE = UUID.fromString("00000000-0000-0000-0000-00000000000a");
    private static final UUID BOB = UUID.fromString("00000000-0000-0000-0000-00000000000b");

    @Test void tenPacketsInOneTickThenNothingUntilTheWindowPasses() {
        MotionRateLimiter limiter = new MotionRateLimiter();
        for (int i = 0; i < 10; i++) { assertTrue(limiter.allow(ALICE, 100), "packet " + i); }
        assertFalse(limiter.allow(ALICE, 100));
        assertFalse(limiter.allow(ALICE, 119));
        assertTrue(limiter.allow(ALICE, 120));
    }

    @Test void anyTwentyTickWindowAcceptsAtMostTen() {
        MotionRateLimiter limiter = new MotionRateLimiter();
        int accepted = 0;
        long[] acceptedAt = new long[1_000];
        for (long tick = 0; tick < 200; tick++) {
            for (int burst = 0; burst < 3; burst++) {
                if (limiter.allow(ALICE, tick)) { acceptedAt[accepted++] = tick; }
            }
        }
        for (int i = 10; i < accepted; i++) {
            assertTrue(acceptedAt[i] - acceptedAt[i - 10] >= 20, "eleven packets inside one window at " + acceptedAt[i]);
        }
        assertEquals(100, accepted, "ten per 20 ticks over 200 ticks");
    }

    @Test void refusedPacketsDoNotExtendTheWindowAndPlayersAreIndependent() {
        MotionRateLimiter limiter = new MotionRateLimiter();
        for (int i = 0; i < 10; i++) { limiter.allow(ALICE, i); }
        for (int i = 0; i < 50; i++) { assertFalse(limiter.allow(ALICE, 15)); }
        assertTrue(limiter.allow(ALICE, 20), "only accepted packets count");
        assertTrue(limiter.allow(BOB, 15));
        assertEquals(2, limiter.tracked());
    }

    @Test void heartbeatsEveryThreeTicksLeaveRoomForToggles() {
        MotionRateLimiter limiter = new MotionRateLimiter();
        for (long tick = 0; tick < 400; tick += 3) { assertTrue(limiter.allow(ALICE, tick), "heartbeat " + tick); }
        assertTrue(limiter.allow(ALICE, 400), "a toggle among heartbeats");
    }

    @Test void forgetAndClearDropEntriesAndABackwardClockIsRefused() {
        MotionRateLimiter limiter = new MotionRateLimiter();
        for (int i = 0; i < 10; i++) { limiter.allow(ALICE, 1_000); }
        assertFalse(limiter.allow(ALICE, 5));
        limiter.forget(ALICE);
        assertEquals(0, limiter.tracked());
        assertTrue(limiter.allow(ALICE, 5));
        limiter.allow(BOB, 5);
        limiter.clear();
        assertEquals(0, limiter.tracked());
    }
}
