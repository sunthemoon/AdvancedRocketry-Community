package io.github.sunthemoon.advancedrocketrycommunity.gametest;

import static org.junit.jupiter.api.Assertions.*;

import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class SatelliteRegistryTestWindowTest {
    @BeforeAll static void bootstrap() { MinecraftBootstrap.initialize(); }

    @Test void anIdleWriterAloneDoesNotExcludeTheNextAutosave() {
        for (int budget : new int[] {200, 300}) {
            for (int autosave : new int[] {6_000, 84_000}) {
                assertTrue(SatelliteRegistryTestWindow.ready(autosave - budget - 1, false, budget));
                assertFalse(SatelliteRegistryTestWindow.ready(autosave - budget, false, budget));
                assertFalse(SatelliteRegistryTestWindow.ready(autosave - 1, false, budget));
                assertFalse(SatelliteRegistryTestWindow.ready(autosave, false, budget));
                assertTrue(SatelliteRegistryTestWindow.ready(autosave + 1, false, budget));
            }
        }
    }

    @Test void aPendingRequestWithoutACaptureStillClosesTheWindow() {
        // W347 R1: the periodic autosave has passed, but its END-phase request has not started yet.
        assertFalse(SatelliteRegistryTestWindow.ready(84_001, true, 300));
        assertTrue(SatelliteRegistryTestWindow.ready(84_016, false, 300));
    }

    @Test void anInFlightCaptureAlsoClosesAnOtherwiseSafeWindow() {
        assertFalse(SatelliteRegistryTestWindow.ready(1_000, true, 300));
        assertTrue(SatelliteRegistryTestWindow.ready(1_000, false, 300));
    }

    @Test void autosaveWaitIsBoundedWithoutWaitingForAnotherWholePeriod() {
        for (int budget : new int[] {200, 300}) {
            int tick = 84_000 - budget;
            int waited = 0;
            while (!SatelliteRegistryTestWindow.ready(tick, false, budget)) { tick++; waited++; }
            assertEquals(budget + 1, waited);
            assertEquals(84_001, tick);
            assertEquals(waited + budget, SatelliteRegistryTestWindow.setupTicks(budget));
        }
    }
}
