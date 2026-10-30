package io.github.sunthemoon.advancedrocketrycommunity.resourceescrow;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class EscrowLoaderS6ChainProbeTest {
    @Test void neutralSetupDecodesOfflineBeforeReplacingTheLockedLiveFixture() {
        var order = new java.util.ArrayList<String>();
        // The retained native failure: loading a locked live entity leaves its 2,000 mB payload unchanged.
        int[] live = {1, 2_000};
        int[] restored = EscrowLoaderS6ChainProbe.replaceFixture(live, () -> new int[] {0, -1}, candidate -> {
            if (candidate[0] == 0) { candidate[1] = 0; }
            order.add("decode");
        }, old -> {
            assertSame(live, old); assertEquals(2_000, old[1]); old[0] = 0; order.add("remove");
        }, candidate -> {
            assertNotSame(live, candidate); assertEquals(0, live[0]); assertEquals(0, candidate[1]);
            candidate[0] = 1; order.add("publish"); return true;
        });
        assertNotSame(live, restored);
        assertEquals(0, restored[1]);
        assertEquals(java.util.List.of("decode", "remove", "publish"), order);
    }
    @Test void invalidOfflineInputNeverRemovesTheOldFixtureAndPublicationFailureIsNotSuccess() {
        Object live = new Object();
        assertThrows(IllegalArgumentException.class, () -> EscrowLoaderS6ChainProbe.replaceFixture(live,
                Object::new, candidate -> { throw new IllegalArgumentException("invalid fixture"); },
                old -> fail("Removal preceded validated offline input"), candidate -> fail("Published invalid input")));
        assertThrows(IllegalArgumentException.class, () -> EscrowLoaderS6ChainProbe.replaceFixture(live,
                Object::new, candidate -> { }, old -> { }, candidate -> false));
        assertThrows(IllegalArgumentException.class, () -> EscrowLoaderS6ChainProbe.replaceFixture(live,
                () -> live, candidate -> fail("Live object was sent through offline decode"), old -> { }, candidate -> true));
    }
    @Test void normalizationSelectorsDoNotPauseAtTheO1BeforeCut() {
        for (String side : new String[] {"BEFORE", "AFTER"}) {
            for (String suffix : new String[] {"", "_BI"}) {
                String cut = "LF_S6_CHAIN_NORMALIZE_" + side + suffix;
                assertTrue(EscrowLoaderS6ChainProbe.selected(true, cut));
                assertFalse(EscrowLoaderS6ChainProbe.selected(false, cut));
                assertFalse(EscrowLoaderS6ChainProbe.selected(true, cut + "_OTHER"));
                assertEquals(side.equals("BEFORE"), EscrowLoaderS6ChainProbe.normalizationBoundary(cut, "O_BIND_BEFORE"));
                assertEquals(side.equals("AFTER"), EscrowLoaderS6ChainProbe.normalizationBoundary(cut, "O_BIND_AFTER"));
                for (String hook : new String[] {"K1", "K2", "O1_ACK"}) {
                    assertFalse(EscrowLoaderS6ChainProbe.boundary(cut, hook));
                }
            }
        }
    }
    @Test void abandonUsesTheSameExactDurableO1Sides() {
        String[] sides = {"BEFORE", "REPLACED", "ACK"}, hooks = {"K1", "K2", "O1_ACK"};
        for (String suffix : new String[] {"", "_BI"}) {
            for (int i = 0; i < sides.length; i++) {
                String cut = "LF_S6_CHAIN_ABANDON_" + sides[i] + suffix;
                assertTrue(EscrowLoaderS6ChainProbe.selected(true, cut));
                assertFalse(EscrowLoaderS6ChainProbe.selected(false, cut));
                for (int j = 0; j < hooks.length; j++) {
                    assertEquals(i == j, EscrowLoaderS6ChainProbe.boundary(cut, hooks[j]));
                }
            }
        }
    }
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
