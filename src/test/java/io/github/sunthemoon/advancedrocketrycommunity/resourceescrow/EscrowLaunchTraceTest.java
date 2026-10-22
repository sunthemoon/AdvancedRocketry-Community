package io.github.sunthemoon.advancedrocketrycommunity.resourceescrow;

import static org.junit.jupiter.api.Assertions.*;

import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class EscrowLaunchTraceTest {
    @BeforeAll static void bootstrap() { MinecraftBootstrap.initialize(); }

    @Test void traceRequiresBothExplicitSwitches() {
        assertFalse(EscrowLaunchTrace.selected(false, false));
        assertFalse(EscrowLaunchTrace.selected(false, true));
        assertFalse(EscrowLaunchTrace.selected(true, false));
        assertTrue(EscrowLaunchTrace.selected(true, true));
    }
}
