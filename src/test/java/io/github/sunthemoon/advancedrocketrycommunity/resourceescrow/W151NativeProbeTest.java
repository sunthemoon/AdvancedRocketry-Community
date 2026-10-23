package io.github.sunthemoon.advancedrocketrycommunity.resourceescrow;

import static org.junit.jupiter.api.Assertions.*;

import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class W151NativeProbeTest {
    @BeforeAll static void bootstrap() { MinecraftBootstrap.initialize(); }

    @Test void completionWaitsForBothRequiredPeriodicSaves() {
        assertFalse(W151NativeProbe.readyToValidate(false, true, 0));
        assertFalse(W151NativeProbe.readyToValidate(false, true, 1));
        assertTrue(W151NativeProbe.readyToValidate(false, true, 2));
    }

    @Test void savesDoNotReplaceTheProducerWindowOrReferenceTransfer() {
        assertFalse(W151NativeProbe.readyToValidate(true, true, 2));
        assertFalse(W151NativeProbe.readyToValidate(false, false, 2));
    }
}
