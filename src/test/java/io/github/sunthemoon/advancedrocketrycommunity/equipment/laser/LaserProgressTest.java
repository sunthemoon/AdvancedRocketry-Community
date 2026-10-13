package io.github.sunthemoon.advancedrocketrycommunity.equipment.laser;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class LaserProgressTest {
    @Test void twentyTicksThenFiveWholeIdleTicksBeforeNewWork() {
        var work = new LaserProgress<String>();
        for (int tick = 1; tick < 20; tick++) { assertFalse(work.tick("stone", tick, 20)); }
        assertTrue(work.tick("stone", 20, 20)); work.attempted(20);
        for (int tick = 21; tick <= 25; tick++) { assertFalse(work.tick("stone", tick, 1)); }
        assertTrue(work.tick("stone", 26, 1));
    }
    @Test void targetChangeStateChangeMissingTickAndMissResetProgress() {
        var work = new LaserProgress<String>();
        for (int tick = 1; tick <= 19; tick++) { assertFalse(work.tick("stone", tick, 20)); }
        assertFalse(work.tick("dirt", 20, 20)); assertEquals(1, work.progress());
        assertFalse(work.tick("state2", 21, 20)); assertEquals(1, work.progress());
        assertFalse(work.tick("state2", 23, 20)); assertEquals(1, work.progress());
        assertFalse(work.tick(null, 24, 1)); assertEquals(0, work.progress());
        assertFalse(work.tick("state2", 25, 20)); assertEquals(1, work.progress());
    }
    @Test void duplicateOrBackwardTicksDoNotAdvanceOrRepeatRewards() {
        var work = new LaserProgress<String>();
        assertTrue(work.tick("zero", 100, 1)); work.attempted(100);
        assertFalse(work.tick("zero", 100, 1)); assertFalse(work.tick("zero", 99, 1));
        assertEquals(0, work.progress()); assertTrue(work.cooling(105)); assertFalse(work.cooling(106));
    }
    @Test void useEndAndIdentityResetDoNotEraseCooldown() {
        var work = new LaserProgress<String>();
        assertTrue(work.tick("zero", 1, 1)); work.attempted(1); work.reset();
        assertFalse(work.tick("other", 2, 1)); assertTrue(work.tick("other", 7, 1));
        work.reset(); assertFalse(work.tick("other", 8, 20)); assertEquals(1, work.progress());
    }
    @Test void thresholdsAreBounded() {
        var work = new LaserProgress<String>();
        assertThrows(IllegalArgumentException.class, () -> work.tick("x", 1, 0));
        assertThrows(IllegalArgumentException.class, () -> work.tick("x", 1, 21));
    }
}
