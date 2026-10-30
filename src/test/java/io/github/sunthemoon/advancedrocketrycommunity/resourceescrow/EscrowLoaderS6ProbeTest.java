package io.github.sunthemoon.advancedrocketrycommunity.resourceescrow;

import static org.junit.jupiter.api.Assertions.*;

import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class EscrowLoaderS6ProbeTest {
    @BeforeAll static void bootstrap() { MinecraftBootstrap.initialize(); }

    @Test void selectionRequiresTheExactDedicatedCaseAndReleaseGate() {
        assertTrue(EscrowLoaderS6Probe.selected(true, "LF_S6_E3_CACHE_CLOSE"));
        assertTrue(EscrowLoaderS6Probe.selected(true, "LF_S6_E5_UNLOADED"));
        assertFalse(EscrowLoaderS6Probe.selected(false, "LF_S6_E5_UNLOADED"));
        assertFalse(EscrowLoaderS6Probe.selected(false, "LF_S6_E3_CACHE_CLOSE"));
        for (String name : new String[] {null, "", "LF_M_K7", "FC_LOAD_E3_CACHE_CLOSE", "LF_S6_OTHER"}) {
            assertFalse(EscrowLoaderS6Probe.selected(true, name));
        }
    }
}
