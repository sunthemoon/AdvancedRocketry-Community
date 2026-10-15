package io.github.sunthemoon.advancedrocketrycommunity.rocket.fuel.loader;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import org.junit.jupiter.api.Test;

class RefillPacerTest {
    @Test void theFirstAttemptWaitsOneFullIntervalAfterLoad() {
        var pacer = RefillPacer.loadedAt(100);
        for (long tick = 100; tick < 120; tick++) { assertFalse(pacer.tryStart(tick), "tick " + tick); }
        assertTrue(pacer.tryStart(120));
        assertFalse(pacer.tryStart(120));
    }

    @Test void atMostOneAttemptPerTwentyTicks() {
        var pacer = RefillPacer.loadedAt(0);
        var starts = new ArrayList<Long>();
        for (long tick = 0; tick <= 1_000; tick++) {
            if (pacer.tryStart(tick)) { starts.add(tick); }
        }
        assertEquals(50, starts.size());
        for (int index = 1; index < starts.size(); index++) {
            assertEquals(RefillPacer.INTERVAL_TICKS, starts.get(index) - starts.get(index - 1));
        }
    }

    @Test void aLongGapAllowsOneAttemptAndNoCatchUpBurst() {
        var pacer = RefillPacer.loadedAt(0);
        assertTrue(pacer.tryStart(20));
        assertTrue(pacer.tryStart(10_000));
        for (long tick = 10_001; tick < 10_020; tick++) { assertFalse(pacer.tryStart(tick), "tick " + tick); }
        assertTrue(pacer.tryStart(10_020));
    }

    @Test void aRestartedDeviceCannotStartWithinTwentyTicksOfItsLastAttempt() {
        var before = RefillPacer.loadedAt(980);
        assertTrue(before.tryStart(1_000));
        var after = RefillPacer.loadedAt(1_001);
        for (long tick = 1_001; tick < 1_021; tick++) { assertFalse(after.tryStart(tick), "tick " + tick); }
        assertTrue(after.tryStart(1_021));
    }

    @Test void aClockThatWentBackReanchorsWithoutABurst() {
        var pacer = RefillPacer.loadedAt(500);
        assertFalse(pacer.tryStart(100));
        assertFalse(pacer.tryStart(119));
        assertTrue(pacer.tryStart(120));
        assertThrows(IllegalArgumentException.class, () -> RefillPacer.loadedAt(-1));
        assertThrows(IllegalArgumentException.class, () -> pacer.tryStart(-1));
        var late = RefillPacer.loadedAt(Long.MAX_VALUE - 20);
        assertTrue(late.tryStart(Long.MAX_VALUE));
    }
}
