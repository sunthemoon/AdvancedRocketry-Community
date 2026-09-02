package io.github.sunthemoon.advancedrocketrycommunity.satellite.mission;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

final class MonotonicMissionClockTest {
    @Test
    void rollbackNeverProducesNegativeProgressOrInfiniteDelay() {
        MonotonicMissionClock clock = MonotonicMissionClock.create(1_000L);
        assertEquals(1_100L, clock.advance(1_100L));
        assertEquals(1_100L, clock.advance(500L));
        assertEquals(1_120L, clock.advance(520L));
        assertEquals(520L, clock.lastObservedGameTime());
    }

    @Test
    void persistedClockResumesFromLogicalTime() {
        MonotonicMissionClock restored = MonotonicMissionClock.restore(8_000L, 2_000L);

        assertEquals(8_040L, restored.advance(2_040L));
        assertThrows(IllegalArgumentException.class, () -> restored.advance(-1L));
    }
}
