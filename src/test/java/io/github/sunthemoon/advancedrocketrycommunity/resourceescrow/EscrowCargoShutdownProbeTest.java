package io.github.sunthemoon.advancedrocketrycommunity.resourceescrow;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class EscrowCargoShutdownProbeTest {
    @Test void shutdownWaitsRequireHooksAnExactCaseAndStartScenario() {
        for (String name : new String[] {EscrowCargoShutdownProbe.L1, EscrowCargoShutdownProbe.L2}) {
            assertTrue(EscrowCargoShutdownProbe.selected(true, name, false));
            assertFalse(EscrowCargoShutdownProbe.selected(false, name, false));
            assertFalse(EscrowCargoShutdownProbe.selected(true, name, true));
        }
        for (String name : new String[] {null, "", "FC_LOAD_L1_BEFORE", "IC_LOAD_G3_L1_INFLIGHT", "FC_UNLOAD_G3_L2_UNKNOWN"}) {
            assertFalse(EscrowCargoShutdownProbe.selected(true, name, false));
        }
        assertEquals("E4L2_UNKNOWN", EscrowCargoStorageProbe.writerCase(EscrowCargoShutdownProbe.L2));
    }
}
