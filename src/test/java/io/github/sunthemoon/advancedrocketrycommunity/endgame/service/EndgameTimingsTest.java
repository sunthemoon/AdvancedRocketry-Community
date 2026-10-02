package io.github.sunthemoon.advancedrocketrycommunity.endgame.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** ADR-054 section 7: per-place means and percentiles, totals with and without flush ticks, the 1,200-tick window. */
final class EndgameTimingsTest {
    @Test
    void placesTotalsAndFlushTicksAreSummarizedOverTheWindow() {
        EndgameTimings timings = new EndgameTimings();
        for (int tick = 0; tick < 100; tick++) {
            timings.add(EndgameTimings.Place.RAILGUN, 10_000L);
            timings.add(EndgameTimings.Place.LEDGER, 5_000L);
            if (tick == 99) {
                timings.add(EndgameTimings.Place.RAILGUN, 990_000L);
                timings.flushed(40_000_000L);
            }
            timings.endTick();
        }
        EndgameTimings.Summary railgun = timings.summary(EndgameTimings.Place.RAILGUN);
        assertEquals(100, railgun.ticks());
        assertEquals(19.9D, railgun.meanMicros(), 1e-9, "the mean over 100 ticks");
        assertEquals(10.0D, railgun.p99Micros(), 1e-9, "the 99th of 100 sorted values");
        assertEquals(1000.0D, railgun.maxMicros(), 1e-9);
        EndgameTimings.Summary total = timings.total(false);
        assertEquals(24.9D, total.meanMicros(), 1e-9, "the flush is not part of the total");
        EndgameTimings.Summary quiet = timings.total(true);
        assertEquals(99, quiet.ticks(), "the flush tick is left out");
        assertEquals(15.0D, quiet.maxMicros(), 1e-9);
        assertTrue(timings.report().get(timings.report().size() - 1).contains("flushes=1"));
        for (int tick = 0; tick < EndgameTimings.WINDOW; tick++) {
            timings.endTick();
        }
        assertEquals(0.0D, timings.summary(EndgameTimings.Place.RAILGUN).maxMicros(), 1e-9,
                "the window holds the last 1,200 ticks only");
        timings.clear();
        assertEquals(0, timings.summary(EndgameTimings.Place.LEDGER).ticks());
    }
}
