package io.github.sunthemoon.advancedrocketrycommunity.rocket.monitor;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class RocketMonitorRulesTest {
    @Test void onlyANewRisingEdgeLaunches() {
        assertTrue(RocketMonitorRules.rising(false, true));
        assertFalse(RocketMonitorRules.rising(false, false));
        assertFalse(RocketMonitorRules.rising(true, true));
        assertFalse(RocketMonitorRules.rising(true, false));
    }
    @Test void heightAndFuelSignalsAreBoundedWithoutIntegerOverflow() {
        assertEquals(0, RocketMonitorRules.comparator(0, -10, 48, 1, 1));
        assertEquals(15, RocketMonitorRules.comparator(0, 1000, 48, 0, 1));
        assertEquals(7, RocketMonitorRules.comparator(0, 24, 48, 0, 1));
        assertEquals(15, RocketMonitorRules.comparator(1, 0, 48, Long.MAX_VALUE, Long.MAX_VALUE));
        assertEquals(0, RocketMonitorRules.comparator(1, 0, 48, 1, 0));
        assertEquals(7, RocketMonitorRules.comparator(1, 0, 48, 50, 100));
        assertEquals(0, RocketMonitorRules.milli(Double.NaN, 4096000));
        assertEquals(0, RocketMonitorRules.milli(Double.POSITIVE_INFINITY, 4096000));
        assertEquals(4096000, RocketMonitorRules.milli(Double.MAX_VALUE, 4096000));
        assertEquals(-4096000, RocketMonitorRules.milli(-Double.MAX_VALUE, 4096000));
        assertEquals(1250, RocketMonitorRules.milli(1.25, 4096000));
    }
}
