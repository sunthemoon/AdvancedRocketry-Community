package io.github.sunthemoon.advancedrocketrycommunity.resourceescrow;

import static org.junit.jupiter.api.Assertions.*;
import java.util.Map;
import org.junit.jupiter.api.Test;

class EscrowLoaderS6PhaseProbeTest {
    @Test void onlyExactCommittedPhaseCallbacksAreSelected() {
        var hooks = Map.of("LF_S6_F4_ACK", "F4_DESTINATION_ACK",
                "LF_S6_F4_PUBLICATION_BEFORE", "F4_PUBLICATION_BEFORE",
                "LF_S6_F4_PUBLICATION_AFTER", "F4_PUBLICATION_AFTER",
                "LF_S6_F5_PASSENGERS_ACK", "F5_PASSENGERS_TRANSFERRED_ACK",
                "LF_S6_F5_SOURCE_ACK", "F5_SOURCE_REMOVED_ACK",
                "LF_S6_F5_SOURCE_REMOVAL_BEFORE", "F5_SOURCE_REMOVAL_BEFORE",
                "LF_S6_F5_SOURCE_REMOVAL_AFTER", "F5_SOURCE_REMOVAL_AFTER",
                "LF_S6_F5_COMMITTED_ACK", "F5_COMMITTED_ACK");
        hooks.forEach((cut, point) -> {
            assertTrue(EscrowLoaderS6PhaseProbe.selected(cut));
            assertTrue(EscrowLoaderS6PhaseProbe.boundary(cut, point));
            assertFalse(EscrowLoaderS6PhaseProbe.boundary(cut, point + "_OTHER"));
            assertFalse(EscrowLoaderS6PhaseProbe.boundary(cut + "_OTHER", point));
        });
        assertTrue(EscrowLoaderS6PhaseProbe.selected("LF_S6_F4_STORED"));
        assertFalse(EscrowLoaderS6PhaseProbe.boundary("LF_S6_F4_STORED", "FIXTURE_PHASE_READBACK"));
        assertFalse(EscrowLoaderS6PhaseProbe.selected(null));
        assertFalse(EscrowLoaderS6PhaseProbe.selected("LF_S6_F6_PROOF_BEFORE_0"));
    }
}
