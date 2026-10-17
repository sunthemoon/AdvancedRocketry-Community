package io.github.sunthemoon.advancedrocketrycommunity.resourceescrow;

import static org.junit.jupiter.api.Assertions.*;

import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class EscrowTestEndpointsGateTest {
    @BeforeAll static void bootstrap() { MinecraftBootstrap.initialize(); }

    @Test void dedicatedFixtureNeedsOnlyRegisteredConnectionsAndEnabledHooks() {
        assertTrue(EscrowTestEndpoints.enabled(true, false, true, true, () -> true));
        assertFalse(EscrowTestEndpoints.enabled(true, false, true, true, () -> false));
        assertFalse(EscrowTestEndpoints.enabled(true, false, false, true, () -> { fail("Hooks off inspected players"); return true; }));
        assertFalse(EscrowTestEndpoints.enabled(true, false, true, false, () -> true));
    }

    @Test void gameTestExceptionAndOwnerThreadBoundaryAreUnchanged() {
        assertTrue(EscrowTestEndpoints.enabled(true, true, false, false, () -> { fail("GameTest inspected native policy"); return false; }));
        assertFalse(EscrowTestEndpoints.enabled(false, true, true, true, () -> true));
        assertFalse(EscrowTestEndpoints.enabled(false, false, true, true, () -> true));
    }
}
