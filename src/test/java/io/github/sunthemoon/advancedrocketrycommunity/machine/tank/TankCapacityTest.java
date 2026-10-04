package io.github.sunthemoon.advancedrocketrycommunity.machine.tank;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class TankCapacityTest {
    @Test void defaultEndpointsAndWholeMilliBuckets() {
        assertEquals(64_000, TankCapacity.fromMultiplier(1));
        assertEquals(16_000, TankCapacity.fromMultiplier(0.25));
        assertEquals(256_000, TankCapacity.fromMultiplier(4));
        assertEquals(64_000, TankCapacity.fromMultiplier(1.000001));
        for (double bad : new double[]{0, -1, 0.249, 4.001, Double.NaN, Double.POSITIVE_INFINITY}) {
            assertThrows(IllegalArgumentException.class, () -> TankCapacity.fromMultiplier(bad));
        }
    }

    @Test void overCapacityBalancesRefuseFillButRemainDrainable() {
        for (int retained : new int[]{64_001, 256_000, Integer.MAX_VALUE}) {
            assertEquals(0, TankCapacity.accepted(retained, 16_000, Integer.MAX_VALUE, true));
            assertEquals(1_000, TankCapacity.drained(retained, 1_000));
        }
        assertEquals(1, TankCapacity.accepted(63_999, 64_000, Integer.MAX_VALUE, true));
        assertEquals(0, TankCapacity.accepted(0, 64_000, 1_000, false));
        assertEquals(0, TankCapacity.accepted(0, 64_000, 0, true));
        assertEquals(0, TankCapacity.drained(0, Integer.MAX_VALUE));
    }
}
