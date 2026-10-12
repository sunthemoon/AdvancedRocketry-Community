package io.github.sunthemoon.advancedrocketrycommunity.station.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.sunthemoon.advancedrocketrycommunity.station.model.StationOrbitalControls.Axis;
import org.junit.jupiter.api.Test;

/** ADR-065 section 5.2.1: modular integer phase arithmetic, epochs and the saturating logical clock. */
final class StationOrbitalControlsTest {
    @Test
    void oneTurnPerPeriodIsFiveMilliDegreesPerTickAndAFullPeriodReturnsHome() {
        assertEquals(5, StationOrbitalControls.PHASE_STEP_PER_RATE);
        assertEquals(5, StationOrbitalControls.advance(0, 1, 1));
        assertEquals(300, StationOrbitalControls.advance(0, 60, 1), "60 turns per 72,000 ticks");
        assertEquals(0, StationOrbitalControls.advance(0, 1, 72_000), "one full turn");
        assertEquals(12_345, StationOrbitalControls.advance(12_345, 60, 72_000), "sixty full turns");
        assertEquals(12_345, StationOrbitalControls.advance(12_345, 0, 1_000_000L), "zero rate never moves");
    }

    @Test
    void wraparoundAndNegativeRatesStayInsideOneTurn() {
        assertEquals(4, StationOrbitalControls.advance(359_999, 1, 1), "positive wrap");
        assertEquals(359_995, StationOrbitalControls.advance(0, -1, 1), "negative wrap");
        assertEquals(359_700, StationOrbitalControls.advance(0, -60, 1));
        assertEquals(0, StationOrbitalControls.advance(0, -60, 72_000));
        // Half a period at -2 turns per period is exactly one turn back.
        assertEquals(1_000, StationOrbitalControls.advance(1_000, -2, 36_000));
        assertEquals(181_000, StationOrbitalControls.advance(1_000, -1, 36_000));
        for (long delta : new long[] {0, 1, 71_999, 72_000, 72_001, 1_000_003, 987_654_321_987L}) {
            for (int rate = -60; rate <= 60; rate++) {
                int phase = StationOrbitalControls.advance(359_999, rate, delta);
                assertTrue(phase >= 0 && phase <= 359_999, "phase " + phase + " for rate " + rate);
            }
        }
    }

    @Test
    void arbitrarilyLongElapsedTimeNeitherOverflowsNorDrifts() {
        long delta = Long.MAX_VALUE;
        long reduced = delta % 72_000;
        assertEquals((int) Math.floorMod(reduced * 60 * 5, 360_000L), StationOrbitalControls.advance(0, 60, delta));
        assertEquals((int) Math.floorMod(17L + reduced * -60 * 5, 360_000L),
                StationOrbitalControls.advance(17, -60, delta));
        // The result depends only on the elapsed ticks modulo one period.
        assertEquals(StationOrbitalControls.advance(5, 37, reduced),
                StationOrbitalControls.advance(5, 37, delta));
        StationOrbitalControls spinning = new StationOrbitalControls(4, 0, 0, 0, 60, -60, 1, 0L);
        assertEquals(StationOrbitalControls.advance(0, -60, Long.MAX_VALUE),
                spinning.phaseAt(Axis.UP, Long.MAX_VALUE));
        assertThrows(IllegalArgumentException.class, () -> StationOrbitalControls.advance(0, 1, -1));
    }

    @Test
    void aRateChangeSamplesTheOldPhasesIntoANewEpoch() {
        StationOrbitalControls start = new StationOrbitalControls(4, 270_000, 0, 10, 2, -3, 0, 100L);
        StationOrbitalControls changed = start.withRates(-1, 0, 60, 1_100L);
        assertEquals(1_100L, changed.epoch());
        assertEquals(start.phaseAt(Axis.EAST, 1_100L), changed.eastPhase());
        assertEquals(start.phaseAt(Axis.UP, 1_100L), changed.upPhase());
        assertEquals(10, changed.northPhase(), "a stopped axis keeps its phase");
        assertEquals(270_000 + 1_000 * 2 * 5, changed.eastPhase());
        assertEquals(360_000 - 1_000 * 3 * 5, changed.upPhase());
        assertEquals(-1, changed.eastRate());
        assertEquals(60, changed.northRate());
        // After the epoch, only the new rates apply.
        assertEquals(Math.floorMod(changed.eastPhase() - 5 * 10, 360_000), changed.phaseAt(Axis.EAST, 1_110L));
        assertEquals(Math.floorMod(10 + 300 * 10, 360_000), changed.phaseAt(Axis.NORTH, 1_110L));
        // STOP samples exactly the same phases and keeps them.
        StationOrbitalControls stopped = changed.stoppedAt(2_000L);
        assertFalse(stopped.rotating());
        assertEquals(changed.phaseAt(Axis.EAST, 2_000L), stopped.phaseAt(Axis.EAST, 9_999_999L));
        assertEquals(changed.phaseAt(Axis.NORTH, 2_000L), stopped.northPhase());
        assertThrows(IllegalArgumentException.class, () -> changed.phaseAt(Axis.EAST, 1_099L),
                "a tick before the epoch is an ordering error");
        assertThrows(IllegalArgumentException.class, () -> changed.withRates(0, 0, 0, 1_099L));
    }

    @Test
    void aFixedPhaseNeedsEveryRateStoppedAndDistanceLeavesOrientationAlone() {
        StationOrbitalControls rotating = new StationOrbitalControls(4, 0, 0, 0, 0, 1, 0, 0L);
        assertThrows(IllegalStateException.class, () -> rotating.withPhases(1, 2, 3, 10L));
        StationOrbitalControls fixed = rotating.stoppedAt(10L).withPhases(1, 2, 3, 20L);
        assertEquals(new StationOrbitalControls(4, 1, 2, 3, 0, 0, 0, 20L), fixed);
        StationOrbitalControls higher = fixed.withDistance(179);
        assertTrue(higher.sameOrientation(fixed));
        assertTrue(higher.geostationary());
        assertEquals(179L * 200 + 100, higher.altitudeKilometres());
        assertFalse(fixed.geostationary());
        assertEquals(900L, fixed.altitudeKilometres());
    }

    @Test
    void theInitialControlsReproduceTheStoredSunAngle() {
        assertEquals(new StationOrbitalControls(4, 270_000, 0, 0, 0, 0, 0, 0L),
                StationOrbitalControls.initial(StationEnvironmentProfile.BASIC_SPACE.solarAngleMilliDegrees()));
        StationOrbitalControls legacy = StationOrbitalControls.initial(359_999);
        assertEquals(359_999, legacy.phaseAt(Axis.EAST, 123_456_789L));
        assertFalse(legacy.rotating());
    }

    @Test
    void everyFieldIsRangeChecked() {
        assertThrows(IllegalArgumentException.class, () -> new StationOrbitalControls(3, 0, 0, 0, 0, 0, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> new StationOrbitalControls(191, 0, 0, 0, 0, 0, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> new StationOrbitalControls(4, 360_000, 0, 0, 0, 0, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> new StationOrbitalControls(4, 0, -1, 0, 0, 0, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> new StationOrbitalControls(4, 0, 0, 0, 61, 0, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> new StationOrbitalControls(4, 0, 0, 0, 0, 0, -61, 0));
        assertThrows(IllegalArgumentException.class, () -> new StationOrbitalControls(4, 0, 0, 0, 0, 0, 0, -1));
        new StationOrbitalControls(190, 359_999, 359_999, 359_999, -60, 60, -60, Long.MAX_VALUE);
    }

    @Test
    void theClockSaturatesInsteadOfWrapping() {
        StationOrbitalClock clock = new StationOrbitalClock(Long.MAX_VALUE - 2);
        assertFalse(clock.exhausted());
        clock.advance();
        clock.advance();
        assertTrue(clock.exhausted());
        clock.advance();
        assertEquals(Long.MAX_VALUE, clock.current());
        StationOrbitalClock fresh = new StationOrbitalClock(0L);
        fresh.advance();
        assertEquals(1L, fresh.current());
        assertThrows(IllegalArgumentException.class, () -> new StationOrbitalClock(-1L));
        // An exhausted clock still lets STOP sample the frozen phases.
        StationOrbitalControls spinning = new StationOrbitalControls(4, 0, 0, 0, 1, 0, 0, 0L);
        assertEquals(spinning.phaseAt(Axis.EAST, Long.MAX_VALUE),
                spinning.stoppedAt(Long.MAX_VALUE).eastPhase());
    }
}
