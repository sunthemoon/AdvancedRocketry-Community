package io.github.sunthemoon.advancedrocketrycommunity.resourceescrow;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class EscrowLoaderS6ChainProbeTest {
    @Test void checkpointedVetoUsesAnExactReleaseSelectorNotAnO1Pause() {
        for (String cut : new String[] {"LF_S6_CHAIN_VETO", "LF_S6_CHAIN_VETO_BI"}) {
            assertTrue(EscrowLoaderS6ChainProbe.selected(true, cut));
            assertFalse(EscrowLoaderS6ChainProbe.selected(false, cut));
            assertFalse(EscrowLoaderS6ChainProbe.selected(true, cut + "_OTHER"));
            for (String hook : new String[] {"K1", "K2", "O1_ACK"}) {
                assertFalse(EscrowLoaderS6ChainProbe.boundary(cut, hook));
            }
        }
    }
    @Test void exactReleaseSelectors() {
        for (String suffix : new String[] {"", "_BI"}) {
            for (String side : new String[] {"BEFORE", "REPLACED", "ACK"}) {
                String cut = "LF_S6_CHAIN_" + side + suffix;
                assertTrue(EscrowLoaderS6ChainProbe.selected(true, cut));
                assertFalse(EscrowLoaderS6ChainProbe.selected(false, cut));
                assertFalse(EscrowLoaderS6ChainProbe.selected(true, cut + "_OTHER"));
            }
        }
        assertFalse(EscrowLoaderS6ChainProbe.selected(true, null));
        assertFalse(EscrowLoaderS6ChainProbe.selected(true, "LF_S6_O1_ACK"));
    }
    @Test void replacementAndAcknowledgementAreDifferentCuts() {
        String[] sides = {"BEFORE", "REPLACED", "ACK"}, hooks = {"K1", "K2", "O1_ACK"};
        for (String suffix : new String[] {"", "_BI"}) {
            for (int i = 0; i < sides.length; i++) {
                for (int j = 0; j < hooks.length; j++) {
                    assertEquals(i == j, EscrowLoaderS6ChainProbe.boundary("LF_S6_CHAIN_" + sides[i] + suffix, hooks[j]));
                }
            }
        }
        assertFalse(EscrowLoaderS6ChainProbe.boundary("LF_S6_CHAIN_ACK_OTHER", "O1_ACK"));
    }
    @Test void bothRealFillsMustApplyBeforeTheManualHold() {
        assertFalse(EscrowLoaderS6ChainProbe.readyToHold(1, 1));
        assertFalse(EscrowLoaderS6ChainProbe.readyToHold(2, 1));
        assertTrue(EscrowLoaderS6ChainProbe.readyToHold(2, 2));
        assertThrows(IllegalArgumentException.class, () -> EscrowLoaderS6ChainProbe.readyToHold(1, 2));
        assertThrows(IllegalArgumentException.class, () -> EscrowLoaderS6ChainProbe.readyToHold(3, 3));
    }
}
