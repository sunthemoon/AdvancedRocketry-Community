package io.github.sunthemoon.advancedrocketrycommunity.atmosphere.device;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class LifeSupportArithmeticTest {
    @Test void zeroOneAndTwoScrubbersKeepExactlyTheirFiniteDebit() {
        for (int devices = 0; devices <= 6; devices++) {
            int paid = 0; int phase = 0;
            for (int debit = 0; debit < 400; debit++) {
                var result = ScrubberSavings.debit(phase, devices); phase = result.nextPhase();
                if (!result.skip()) { paid++; }
                if (debit % 4 == 3) { assertFalse(result.skip(), "The fourth debit is mandatory"); }
            }
            assertEquals(devices == 0 ? 400 : devices == 1 ? 200 : 100, paid);
            assertEquals(0, phase);
        }
    }
    @Test void invalidPhasesAndUnboundedDeviceCountsRefuse() {
        assertThrows(IllegalArgumentException.class, () -> ScrubberSavings.debit(-1, 1));
        assertThrows(IllegalArgumentException.class, () -> ScrubberSavings.debit(4, 1));
        assertThrows(IllegalArgumentException.class, () -> ScrubberSavings.debit(0, 7));
        assertThrows(IllegalArgumentException.class, () -> ScrubberSavings.debit(0, -1));
    }
    @Test void chargerBoundsRefuseOverfillUnderflowAndIntegerOverflow() {
        assertTrue(GasChargeBounds.accepts(16_000, 900, 1_000, 100));
        assertTrue(GasChargeBounds.accepts(1, 31_999, 32_000, 1));
        for (int[] values : new int[][]{{100, 999, 1000, 2}, {99, 0, 1000, 100}, {1000, 0, 1000, 101},
                {16001, 0, 1000, 1}, {-1, 0, 1000, 1}, {100, -1, 1000, 1}, {100, 1001, 1000, 1},
                {100, 0, 1000, 0}, {100, 0, 32001, 1}, {100, Integer.MAX_VALUE, Integer.MAX_VALUE, 100}}) {
            assertFalse(GasChargeBounds.accepts(values[0], values[1], values[2], values[3]));
        }
    }
}
