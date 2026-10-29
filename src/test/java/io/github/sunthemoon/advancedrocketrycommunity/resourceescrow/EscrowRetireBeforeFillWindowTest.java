package io.github.sunthemoon.advancedrocketrycommunity.resourceescrow;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class EscrowRetireBeforeFillWindowTest {
    @BeforeAll static void bootstrap() { MinecraftBootstrap.initialize(); }

    @Test void doesNotSubmitThePositiveLaunchInThePeriodicSaveWindow() {
        for (int tick = 5_940; tick <= 6_000; tick++) {
            assertFalse(EscrowRetireBeforeFillGameTests.launchCaptureWindow(tick), "tick=" + tick);
            assertFalse(EscrowRetireBeforeFillGameTests.launchCaptureWindow(tick + 6_000), "tick=" + (tick + 6_000));
        }
    }

    @Test void admitsOutsideTheWindowWithoutChangingTheServerPeriod() {
        assertTrue(EscrowRetireBeforeFillGameTests.launchCaptureWindow(5_939));
        assertTrue(EscrowRetireBeforeFillGameTests.launchCaptureWindow(6_001));
        assertTrue(EscrowRetireBeforeFillGameTests.launchCaptureWindow(11_939));
        assertTrue(EscrowRetireBeforeFillGameTests.launchCaptureWindow(12_001));
    }
}
