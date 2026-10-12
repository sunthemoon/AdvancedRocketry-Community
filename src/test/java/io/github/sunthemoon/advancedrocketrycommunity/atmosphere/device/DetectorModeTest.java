package io.github.sunthemoon.advancedrocketrycommunity.atmosphere.device;

import static org.junit.jupiter.api.Assertions.*;
import io.github.sunthemoon.advancedrocketrycommunity.atmosphere.instrument.AnalyzerReading;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class DetectorModeTest {
    private AnalyzerReading reading(boolean breathable, boolean supplied, double pressure, double temperature) {
        return new AnalyzerReading(breathable ? AnalyzerReading.State.BREATHABLE : AnalyzerReading.State.NON_BREATHABLE,
                Optional.of("test:body"), Optional.of(AnalyzerReading.Locus.SURFACE), Optional.of("test:body"),
                Optional.of(new AnalyzerReading.Ambient(pressure, temperature)), supplied);
    }
    @Test void exactPressureAndTemperatureBoundariesAndOverlaps() {
        assertTrue(DetectorMode.VACUUM.matches(reading(false, false, 0, 293)));
        assertFalse(DetectorMode.NON_BREATHABLE_ATMOSPHERE.matches(reading(false, false, 0, 293)));
        assertTrue(DetectorMode.NON_BREATHABLE_ATMOSPHERE.matches(reading(false, false, 1, 293)));
        assertFalse(DetectorMode.HIGH_PRESSURE.matches(reading(false, false, 2, 293)));
        assertTrue(DetectorMode.HIGH_PRESSURE.matches(reading(true, false, 2.001, 331)));
        assertTrue(DetectorMode.HOT.matches(reading(true, false, 2.001, 331)));
        assertFalse(DetectorMode.HOT.matches(reading(false, false, 1, 330)));
        assertFalse(DetectorMode.COLD.matches(reading(false, false, 1, 240)));
        assertTrue(DetectorMode.COLD.matches(reading(false, false, 1, 239.99)));
    }
    @Test void suppliedRoomsOverrideEveryExteriorHazardButUnknownNeverMatches() {
        for (DetectorMode mode : DetectorMode.values()) {
            assertEquals(mode == DetectorMode.BREATHABLE, mode.matches(reading(true, true, 4, 600)));
            assertEquals(mode == DetectorMode.BREATHABLE, mode.matches(reading(true, true, 0, 100)));
            assertFalse(mode.matches(AnalyzerReading.unavailable())); assertFalse(mode.matches(AnalyzerReading.disabled()));
        }
        var pending = new AnalyzerReading(AnalyzerReading.State.PENDING, Optional.of("test:body"), Optional.of(AnalyzerReading.Locus.ORBIT),
                Optional.of("test:space"), Optional.of(new AnalyzerReading.Ambient(0, 100)), false);
        for (DetectorMode mode : DetectorMode.values()) { assertFalse(mode.matches(pending)); }
    }
}
