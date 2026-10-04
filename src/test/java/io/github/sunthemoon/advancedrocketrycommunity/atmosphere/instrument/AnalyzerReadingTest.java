package io.github.sunthemoon.advancedrocketrycommunity.atmosphere.instrument;

import static org.junit.jupiter.api.Assertions.*;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class AnalyzerReadingTest {
    private static AnalyzerReading known(AnalyzerReading.State state, boolean supplied) {
        return new AnalyzerReading(state, Optional.of("test:body"), Optional.of(AnalyzerReading.Locus.SURFACE),
                Optional.of("test:body"), Optional.of(new AnalyzerReading.Ambient(1, 288)), supplied);
    }

    @Test void completeKnownStatesKeepPairedAmbient() {
        for (var state : new AnalyzerReading.State[] {AnalyzerReading.State.BREATHABLE, AnalyzerReading.State.NON_BREATHABLE, AnalyzerReading.State.PENDING}) {
            assertEquals(288, known(state, false).ambient().orElseThrow().temperatureKelvin());
        }
        assertTrue(known(AnalyzerReading.State.BREATHABLE, true).supplied());
    }

    @Test void pendingAndUnavailableCannotClaimSupplyOrPartialMetadata() {
        assertThrows(IllegalArgumentException.class, () -> known(AnalyzerReading.State.PENDING, true));
        assertThrows(IllegalArgumentException.class, () -> known(AnalyzerReading.State.NON_BREATHABLE, true));
        assertThrows(IllegalArgumentException.class, () -> known(AnalyzerReading.State.UNAVAILABLE, false));
        assertThrows(IllegalArgumentException.class, () -> new AnalyzerReading(AnalyzerReading.State.BREATHABLE,
                Optional.of("test:body"), Optional.of(AnalyzerReading.Locus.SURFACE), Optional.empty(), Optional.empty(), false));
        assertEquals(Optional.empty(), AnalyzerReading.unavailable().ambient());
        assertEquals(Optional.empty(), AnalyzerReading.disabled().bodyId());
    }

    @Test void finiteExistingBoundsIncludeBothEndpointsAndRefuseInvalidNumbers() {
        assertEquals(0, new AnalyzerReading.Ambient(0, 0).pressure());
        assertEquals(2000, new AnalyzerReading.Ambient(10, 2000).temperatureKelvin());
        for (double invalid : new double[] {-0.01, 10.01, Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY}) {
            assertThrows(IllegalArgumentException.class, () -> new AnalyzerReading.Ambient(invalid, 200));
        }
        for (double invalid : new double[] {-0.01, 2000.01, Double.NaN, Double.POSITIVE_INFINITY}) {
            assertThrows(IllegalArgumentException.class, () -> new AnalyzerReading.Ambient(1, invalid));
        }
    }

    @Test void boundedStableIdsRejectOversizeAndUntrustedText() {
        AnalyzerReading.requireId("a:" + "b".repeat(126));
        assertThrows(IllegalArgumentException.class, () -> AnalyzerReading.requireId("a:" + "b".repeat(127)));
        for (String invalid : new String[] {"", "test", "test:UPPER", "test:\nbody", "x:y:z"}) {
            assertThrows(IllegalArgumentException.class, () -> AnalyzerReading.requireId(invalid));
        }
    }
}
