package io.github.sunthemoon.advancedrocketrycommunity.machine.solar;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class SolarArrayRulesTest {
    private static SolarGeneration.Environment surface(boolean sky, int day, double rain, double thunder) {
        double transmission = SolarGeneration.attenuation(rain, thunder);
        return new SolarGeneration.Environment(true, sky, day, 1, transmission, SolarGeneration.weatherPermille(transmission));
    }
    private static SolarGeneration.Step step(SolarGeneration.Environment environment) {
        return SolarArrayRules.step(0, SolarArrayRules.Structure.FORMED, 63, 1, true, false, environment);
    }
    @Test void clearRainThunderNightAndOcclusionUseTheSharedRule() {
        assertEquals(126, step(surface(true, 1, 0, 0)).credit());
        assertEquals(63, step(surface(true, 1, 1, 0)).credit());
        assertEquals(31, step(surface(true, 1, 1, 1)).credit());
        assertEquals(SolarGeneration.Reason.NOT_DAYLIGHT, step(surface(true, 0, 0, 0)).reason());
        assertEquals(SolarGeneration.Reason.SKY_BLOCKED, step(surface(false, 1, 0, 0)).reason());
    }
    @Test void stationExposureAndMissingOrbitDoNotUseSurfaceDay() {
        for (int context : new int[]{2, 3}) {
            var open = new SolarGeneration.Environment(true, true, 2, context, 1.25, 1_000);
            assertEquals(157, step(open).credit());
            assertEquals(context, step(open).environment().context());
            assertEquals(0, step(new SolarGeneration.Environment(true, false, 2, context, 1.25, 1_000)).credit());
        }
        assertEquals(SolarGeneration.Reason.CONTEXT_UNAVAILABLE, step(null).reason());
    }
    @Test void fullAndNearlyFullBuffersNeverLoseOrWrapEnergy() {
        var maximum = new SolarGeneration.Environment(true, true, 2, 2, 16, 1_000);
        for (int collectors = 1; collectors <= 63; collectors++) {
            for (int multiplier = 1; multiplier <= 4; multiplier++) {
                for (int energy : new int[]{0, 1, 11_937, 19_999, 20_000}) {
                    var step = SolarArrayRules.step(energy, SolarArrayRules.Structure.FORMED, collectors, multiplier, true, false, maximum);
                    int expected = Math.min(20_000 - energy, 2 * collectors * multiplier * 16);
                    assertEquals(expected, step.credit()); assertEquals(energy + expected, step.energy());
                    assertTrue(step.credit() <= SolarArrayRules.MAX_CREDIT);
                }
            }
        }
        assertEquals(SolarGeneration.Reason.BUFFER_FULL, SolarArrayRules.step(20_000, SolarArrayRules.Structure.FORMED, 63, 4, true, false, maximum).reason());
        assertEquals(8_064, SolarArrayRules.MAX_CREDIT);
    }
    @Test void unformedRepairAndDisableRetainStorage() {
        for (var structure : SolarArrayRules.Structure.values()) {
            if (structure == SolarArrayRules.Structure.FORMED) { continue; }
            var step = SolarArrayRules.step(19_999, structure, 0, 4, true, false, surface(true, 1, 0, 0));
            assertEquals(19_999, step.energy()); assertEquals(0, step.credit());
            assertEquals(SolarGeneration.Reason.CONTEXT_UNAVAILABLE, step.reason());
        }
        assertEquals(SolarGeneration.Reason.DISABLED, SolarArrayRules.step(777, SolarArrayRules.Structure.FORMED, 63, 1, false, false, null).reason());
        assertEquals(SolarGeneration.Reason.REPAIR_REQUIRED, SolarArrayRules.step(777, SolarArrayRules.Structure.FORMED, 63, 1, false, true, null).reason());
    }
    @Test void collectorAndEnergyBoundsAreStrictAndSingleProfileIsUnchanged() {
        for (int collectors : new int[]{0, -1, 64, Integer.MAX_VALUE}) {
            assertThrows(IllegalArgumentException.class, () -> SolarGeneration.tick(0, 20_000, collectors, 1, true, false, null));
        }
        for (int energy : new int[]{-1, 20_001, Integer.MAX_VALUE}) {
            assertThrows(IllegalArgumentException.class, () -> SolarArrayRules.step(energy, SolarArrayRules.Structure.FORMED, 63, 4, true, false, null));
        }
        var environment = surface(true, 1, 0.013, 0.025);
        assertEquals(SolarGeneration.tick(100, 3, true, false, environment),
                SolarGeneration.tick(100, SolarGeneration.CAPACITY, 1, 3, true, false, environment));
    }
}
