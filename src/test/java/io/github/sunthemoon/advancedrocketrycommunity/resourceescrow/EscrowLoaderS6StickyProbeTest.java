package io.github.sunthemoon.advancedrocketrycommunity.resourceescrow;

import static org.junit.jupiter.api.Assertions.*;

import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class EscrowLoaderS6StickyProbeTest {
    @BeforeAll static void bootstrap() { MinecraftBootstrap.initialize(); }

    @Test void onlyTheExactGatedSelectorAndJointReadbackAreSelected() {
        assertTrue(EscrowLoaderS6StickyProbe.selected(true, "LF_S6_STICKY_RESERVATION"));
        assertFalse(EscrowLoaderS6StickyProbe.selected(false, "LF_S6_STICKY_RESERVATION"));
        for (String other : new String[] {null, "", "LF_S6_OTHER", "LF_M_K7"}) {
            assertFalse(EscrowLoaderS6StickyProbe.selected(true, other));
        }
        assertTrue(EscrowLoaderS6StickyProbe.boundary("C6_JOINT_READBACK"));
        for (String other : new String[] {null, "C2_DESTINATION_AFTER", "C7_ACKNOWLEDGED", "K9"}) {
            assertFalse(EscrowLoaderS6StickyProbe.boundary(other));
        }
    }

    @Test void onlyTheAcknowledgedLongestReasonQualifies() {
        var operation = EscrowTestFixtures.operation(1);
        assertFalse(EscrowLoaderS6StickyProbe.durable(operation));
        assertTrue(EscrowLoaderS6StickyProbe.durable(operation.withReason(EscrowReason.JOURNAL_GUARD_MISMATCH)));
        assertThrows(IllegalArgumentException.class, () -> EscrowLoaderS6StickyProbe.durable(operation.withReason(EscrowReason.OTHER_STATE)));
    }
}
