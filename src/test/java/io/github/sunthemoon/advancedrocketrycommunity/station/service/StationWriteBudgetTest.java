package io.github.sunthemoon.advancedrocketrycommunity.station.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

/** Final v1.5 review B9: one server-wide spacing between checked station writes, scaled by registry size. */
final class StationWriteBudgetTest {
    @Test
    void spacingScalesWithTheRegistryAndSmallRegistriesAreNotSpaced() {
        assertEquals(0L, StationWriteBudget.spacingTicks(33, 3));
        assertEquals(1L, StationWriteBudget.spacingTicks(34, 3));
        assertEquals(3L, StationWriteBudget.spacingTicks(100, 3));
        assertEquals(122L, StationWriteBudget.spacingTicks(4_096, 3), "About 6 seconds at the station limit");
        assertTrue(StationWriteBudget.spacingTicks(4_096, 3) >= 100,
                "At most 1 % of ticks carry a full-registry write at the default");
        assertEquals(0L, StationWriteBudget.spacingTicks(4_096, 0), "0 disables the spacing");
        assertEquals(0L, StationWriteBudget.spacingTicks(0, 100));
    }

    @Test
    void aWriteBlocksTheNextUntilTheSpacingHasPassedWhateverStationAsks() {
        AtomicInteger perHundred = new AtomicInteger(3);
        StationWriteBudget budget = new StationWriteBudget(perHundred::get);
        assertTrue(budget.ready(1_000, 4_096), "Nothing written yet");
        budget.record(1_000);
        assertFalse(budget.ready(1_000, 4_096));
        assertEquals(122L, budget.remainingTicks(1_000, 4_096));
        assertFalse(budget.ready(1_121, 4_096));
        assertTrue(budget.ready(1_122, 4_096));
        assertTrue(budget.ready(1_001, 20), "A small registry is not spaced");
        perHundred.set(0);
        assertTrue(budget.ready(1_001, 4_096), "The config value applies at once");
        perHundred.set(3);
        budget.clear();
        assertTrue(budget.ready(1_001, 4_096), "Clearing at server stop forgets the last write");
        assertTrue(StationWriteBudget.unbounded().ready(0, 4_096));
    }
}
