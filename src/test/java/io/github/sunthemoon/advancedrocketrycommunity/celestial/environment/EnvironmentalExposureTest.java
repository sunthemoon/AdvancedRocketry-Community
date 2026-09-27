package io.github.sunthemoon.advancedrocketrycommunity.celestial.environment;

import static org.junit.jupiter.api.Assertions.*;
import static io.github.sunthemoon.advancedrocketrycommunity.celestial.environment.EnvironmentalExposure.*;

import org.junit.jupiter.api.Test;

class EnvironmentalExposureTest {
    @Test void inclusiveSafeEdgesAndAdjacentUnsafeValues() {
        assertEquals(0, mask(2, 240, 1.5));
        assertEquals(0, mask(2, 330, 1.5));
        assertEquals(COLD, mask(1, Math.nextDown(240.0), 1));
        assertEquals(HEAT, mask(1, Math.nextUp(330.0), 1));
        assertEquals(PRESSURE, mask(Math.nextUp(2.0), 288, 1));
        assertEquals(SOLAR, mask(1, 288, Math.nextUp(1.5)));
        assertEquals(COLD, mask(0, 0, 0));
        assertEquals(HEAT | PRESSURE | SOLAR, mask(10, 2000, 16));
    }

    @Test void everyProtectionCombinationOnlyRemovesItsOwnHazard() {
        for (int flags = 0; flags < 8; flags++) {
            var protection = new Protection((flags & 1) != 0, (flags & 2) != 0, (flags & 4) != 0);
            int pressure = protection.pressure() ? 0 : PRESSURE;
            int solar = protection.solar() ? 0 : SOLAR;
            assertEquals((protection.thermal() ? 0 : COLD) | pressure | solar,
                    hazards(new EnvironmentalConditions(true, 10, 0, 16), protection, false, true));
            assertEquals((protection.thermal() ? 0 : HEAT) | pressure | solar,
                    hazards(new EnvironmentalConditions(true, 10, 2000, 16), protection, false, true));
        }
    }

    @Test void optOutAndControlledRoomsRemoveAllNewHazards() {
        assertEquals(0, hazards(new EnvironmentalConditions(false, 10, 2000, 16), Protection.NONE, false, true));
        assertEquals(0, hazards(new EnvironmentalConditions(true, 10, 2000, 16), Protection.NONE, true, true));
        assertEquals(HEAT | PRESSURE, hazards(new EnvironmentalConditions(true, 10, 2000, 16), Protection.NONE, false, false));
    }

    @Test void climateControlIsIndependentOfSolarAndOptOutRetainsTheOldShortcut() {
        assertFalse(new EnvironmentalConditions(false, 10, 2000, 16).requiresClimateControl());
        assertFalse(new EnvironmentalConditions(true, 2, 240, 16).requiresClimateControl());
        assertFalse(new EnvironmentalConditions(true, 2, 330, 0).requiresClimateControl());
        assertTrue(new EnvironmentalConditions(true, 2.01, 288, 0).requiresClimateControl());
        assertTrue(new EnvironmentalConditions(true, 1, 210, 0).requiresClimateControl());
        assertTrue(new EnvironmentalConditions(true, 1, 737, 0).requiresClimateControl());
    }

    @Test void oneIntervalOneDamageRegardlessOfHazardCount() {
        int phase = 0;
        float total = 0;
        for (int i = 1; i <= 100; i++) {
            var decision = tick(HEAT | PRESSURE | SOLAR, phase);
            phase = decision.phase();
            assertEquals(i % 20 == 0 ? 2 : 0, decision.damage());
            total += decision.damage();
        }
        assertEquals(10, total);
    }

    @Test void remainingHazardsContinueCadenceAndCompleteProtectionResetsIt() {
        assertEquals(2, tick(HEAT, 19).damage());
        assertEquals(0, tick(0, 19).damage());
        assertEquals(0, tick(0, 19).phase());
        assertEquals(1, tick(PRESSURE, 0).phase());
        assertEquals("pressure", primary(HEAT | PRESSURE | SOLAR));
        assertEquals("heat", primary(HEAT | SOLAR));
        assertEquals("cold", primary(COLD | SOLAR));
        assertEquals("solar", primary(SOLAR));
    }

    @Test void malformedStateAndNonfiniteInputsAreRejected() {
        for (int mask : new int[]{-1, 16, 32}) {
            assertThrows(IllegalArgumentException.class, () -> tick(mask, 0));
            assertThrows(IllegalArgumentException.class, () -> primary(mask));
        }
        assertThrows(IllegalArgumentException.class, () -> primary(0));
        assertThrows(IllegalArgumentException.class, () -> tick(COLD, -1));
        assertThrows(IllegalArgumentException.class, () -> tick(COLD, 20));
        for (double bad : new double[]{Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY, -1}) {
            assertThrows(IllegalArgumentException.class, () -> new EnvironmentalConditions(true, bad, 288, 1));
            assertThrows(IllegalArgumentException.class, () -> new EnvironmentalConditions(true, 1, bad, 1));
            assertThrows(IllegalArgumentException.class, () -> new EnvironmentalConditions(true, 1, 288, bad));
        }
        assertThrows(IllegalArgumentException.class, () -> new EnvironmentalConditions(true, 10.01, 288, 1));
        assertThrows(IllegalArgumentException.class, () -> new EnvironmentalConditions(true, 1, 2000.01, 1));
        assertThrows(IllegalArgumentException.class, () -> new EnvironmentalConditions(true, 1, 288, 16.01));
    }

    private static int mask(double pressure, double temperature, double solar) {
        return hazards(new EnvironmentalConditions(true, pressure, temperature, solar), Protection.NONE, false, true);
    }
}
