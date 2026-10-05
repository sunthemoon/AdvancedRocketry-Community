package io.github.sunthemoon.advancedrocketrycommunity.machine.solar;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class SolarGenerationTest {
    private static SolarGeneration.Environment sample(double intensity) {
        return new SolarGeneration.Environment(true, true, 1, 1, intensity, 1_000);
    }
    @Test void floorsOnlyTheFinalRequestAndCreditsOnlyRoom() {
        assertEquals(0, SolarGeneration.tick(0, 1, true, false, sample(0.499)).credit());
        assertEquals(1, SolarGeneration.tick(0, 1, true, false, sample(0.5)).credit());
        assertEquals(3, SolarGeneration.tick(0, 1, true, false, sample(1.999)).credit());
        assertEquals(128, SolarGeneration.tick(0, 4, true, false, sample(16)).credit());
        var last = SolarGeneration.tick(9_999, 4, true, false, sample(16));
        assertEquals(10_000, last.energy()); assertEquals(1, last.credit());
        assertEquals(SolarGeneration.Reason.GENERATING, last.reason());
        assertEquals(SolarGeneration.Reason.BUFFER_FULL, SolarGeneration.tick(10_000, 4, true, false, sample(16)).reason());
    }
    @Test void finiteGridNeverEscapesTheBufferOrCreditCeiling() {
        for (int energy : new int[]{0, 1, 9_872, 9_999, 10_000}) {
            for (int multiplier = 1; multiplier <= 4; multiplier++) {
                for (int hundredths = 0; hundredths <= 1_600; hundredths++) {
                    double intensity = hundredths / 100.0D;
                    var step = SolarGeneration.tick(energy, multiplier, true, false, sample(intensity));
                    int expected = Math.min(10_000 - energy, (int) Math.floor(2.0D * multiplier * intensity));
                    assertEquals(expected, step.credit()); assertEquals(energy + expected, step.energy());
                    assertTrue(step.credit() <= SolarGeneration.MAX_CREDIT);
                }
            }
        }
    }
    @Test void invalidSamplesAndMultiplierRefuseRatherThanClampIntensity() {
        for (double intensity : new double[]{-1, 16.01, Double.NaN, Double.POSITIVE_INFINITY}) {
            var step = SolarGeneration.tick(777, 1, true, false, sample(intensity));
            assertEquals(777, step.energy()); assertEquals(0, step.credit());
            assertEquals(SolarGeneration.Reason.CONTEXT_UNAVAILABLE, step.reason());
        }
        for (int multiplier : new int[]{Integer.MIN_VALUE, 0, 5, Integer.MAX_VALUE}) {
            assertEquals(SolarGeneration.Reason.CONTEXT_UNAVAILABLE,
                    SolarGeneration.tick(0, multiplier, true, false, sample(1)).reason());
        }
        assertThrows(IllegalArgumentException.class, () -> SolarGeneration.tick(-1, 1, true, false, sample(1)));
        assertThrows(IllegalArgumentException.class, () -> SolarGeneration.tick(10_001, 1, true, false, sample(1)));
    }
    @Test void precedenceAndDisabledFeedbackAreExplicit() {
        var blockedNight = new SolarGeneration.Environment(true, false, 0, 1, 0, 500);
        assertEquals(SolarGeneration.Reason.REPAIR_REQUIRED, SolarGeneration.tick(10_000, 1, false, true, blockedNight).reason());
        var disabled = SolarGeneration.tick(777, 1, false, false, blockedNight);
        assertEquals(777, disabled.energy()); assertEquals(0, disabled.credit());
        assertEquals(SolarGeneration.Reason.DISABLED, disabled.reason());
        assertEquals(SolarGeneration.Environment.unavailable(), disabled.environment());
        assertEquals(SolarGeneration.Reason.CONTEXT_UNAVAILABLE, SolarGeneration.tick(0, 1, true, false, null).reason());
        assertEquals(SolarGeneration.Reason.SKY_BLOCKED, SolarGeneration.tick(10_000, 1, true, false, blockedNight).reason());
        assertEquals(SolarGeneration.Reason.NOT_DAYLIGHT, SolarGeneration.tick(10_000, 1, true, false,
                new SolarGeneration.Environment(true, true, 0, 1, 0, 500)).reason());
        assertEquals(SolarGeneration.Reason.NO_IRRADIANCE, SolarGeneration.tick(10_000, 1, true, false, sample(0)).reason());
    }
    @Test void weatherUsesContinuousBinary64RatherThanDisplayPermille() {
        assertEquals(1, SolarGeneration.attenuation(0, 0));
        assertEquals(0.5, SolarGeneration.attenuation(1, 0));
        assertEquals(0.25, SolarGeneration.attenuation(1, 1));
        assertEquals(1, SolarGeneration.attenuation(-99, -99));
        assertEquals(0.25, SolarGeneration.attenuation(99, 99));
        assertThrows(IllegalArgumentException.class, () -> SolarGeneration.attenuation(Double.NaN, 0));
        assertThrows(IllegalArgumentException.class, () -> SolarGeneration.attenuation(0, Double.POSITIVE_INFINITY));
        assertEquals(313, SolarGeneration.weatherPermille(0.3125));
        assertEquals(250, SolarGeneration.weatherPermille(0.25));
        assertEquals(1_000, SolarGeneration.weatherPermille(1));
        double intensity = 1.0D * SolarGeneration.attenuation(0.001, 0);
        assertEquals(1_000, SolarGeneration.weatherPermille(intensity));
        assertEquals(1, SolarGeneration.tick(0, 1, true, false,
                new SolarGeneration.Environment(true, true, 1, 1, intensity, 1_000)).credit());
    }
    @Test void weatherAttenuationIsMonotonicAndBounded() {
        for (int rain = 0; rain <= 100; rain++) { for (int thunder = 0; thunder <= 100; thunder++) {
            double value = SolarGeneration.attenuation(rain / 100D, thunder / 100D);
            assertTrue(value >= 0.25 && value <= 1);
            if (rain < 100) { assertTrue(value >= SolarGeneration.attenuation((rain + 1) / 100D, thunder / 100D)); }
            if (thunder < 100) { assertTrue(value >= SolarGeneration.attenuation(rain / 100D, (thunder + 1) / 100D)); }
        } }
    }
    @Test void stationDayIsNotApplicableIncludingMissingOrbitFallback() {
        for (int context : new int[]{2, 3}) {
            var sample = new SolarGeneration.Environment(true, true, 2, context, 1.25, 1_000);
            assertEquals(2, SolarGeneration.tick(0, 1, true, false, sample).credit());
            assertEquals(context, SolarGeneration.tick(0, 1, true, false, sample).environment().context());
        }
        assertFalse(new SolarGeneration.Environment(true, true, 1, 2, 1, 1_000).valid());
        assertFalse(new SolarGeneration.Environment(true, true, 2, 1, 1, 1_000).valid());
    }
    @Test void columnRuleAdmitsExteriorTopFaceAndRejectsUnknownCacheValues() {
        assertTrue(SolarGeneration.exposed(0, Integer.MIN_VALUE, 0, 256));
        assertTrue(SolarGeneration.exposed(0, 1, 0, 256));
        assertFalse(SolarGeneration.exposed(0, 2, 0, 256));
        assertTrue(SolarGeneration.exposed(255, 256, 0, 256));
        assertTrue(SolarGeneration.exposed(-64, -63, -64, 320));
        for (int cutoff : new int[]{-1, 257, Integer.MAX_VALUE}) {
            assertFalse(SolarGeneration.validCutoff(cutoff, 0, 256));
            assertThrows(IllegalArgumentException.class, () -> SolarGeneration.exposed(0, cutoff, 0, 256));
        }
        assertThrows(IllegalArgumentException.class, () -> SolarGeneration.exposed(256, 256, 0, 256));
        assertThrows(IllegalArgumentException.class, () -> SolarGeneration.exposed(-1, 0, 0, 256));
    }
}
