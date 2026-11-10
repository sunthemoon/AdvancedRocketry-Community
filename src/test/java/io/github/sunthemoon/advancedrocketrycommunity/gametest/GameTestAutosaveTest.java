package io.github.sunthemoon.advancedrocketrycommunity.gametest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class GameTestAutosaveTest {
    @Test
    void countsTheTickEndsToVanillasNextAutosave() {
        assertEquals(0, GameTestAutosave.ticksUntil(0));
        assertEquals(0, GameTestAutosave.ticksUntil(6_000));
        assertEquals(5_999, GameTestAutosave.ticksUntil(1));
        assertEquals(60, GameTestAutosave.ticksUntil(5_940));
        assertEquals(61, GameTestAutosave.ticksUntil(17_939));
        assertEquals(1, GameTestAutosave.ticksUntil(11_999));
    }

    /** W347: just below, at and just after a multiple of 6,000. */
    @Test
    void countsAroundEachAutosaveTick() {
        assertEquals(1, GameTestAutosave.ticksUntil(5_999));
        assertEquals(0, GameTestAutosave.ticksUntil(6_000));
        assertEquals(5_999, GameTestAutosave.ticksUntil(6_001));
        assertEquals(1, GameTestAutosave.ticksUntil(83_999));
        assertEquals(0, GameTestAutosave.ticksUntil(84_000));
        assertEquals(5_999, GameTestAutosave.ticksUntil(84_001));
    }

    /** W347: a window holds the autosave from {@code window} ticks before it through its own tick. */
    @Test
    void aWindowHoldsTheAutosaveThroughItsOwnTick() {
        int window = 230;
        assertFalse(GameTestAutosave.within(6_000 - window - 1, window), "the autosave lies just past the window");
        assertTrue(GameTestAutosave.within(6_000 - window, window));
        assertTrue(GameTestAutosave.within(5_999, window));
        assertTrue(GameTestAutosave.within(6_000, window), "a callback in tick 6,000 runs before that tick's autosave");
        assertFalse(GameTestAutosave.within(6_001, window), "the autosave has passed");
        // Shard 0 of run 42748839833 opened the W159 Replace window at about tick 11,998.
        assertTrue(GameTestAutosave.within(11_998, window));
        assertFalse(GameTestAutosave.within(12_001, window));
    }

    @Test
    void theLongestWaitIsTheWindowAndTheAutosaveTick() {
        int window = 230;
        int waited = 0;
        for (int tick = 12_000 - window; GameTestAutosave.within(tick, window); tick++) {
            waited++;
        }
        assertEquals(window + 1, waited);
    }
}
