package io.github.sunthemoon.advancedrocketrycommunity.resourceescrow;

import static org.junit.jupiter.api.Assertions.*;

import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class EscrowLoaderS6SettlementProbeTest {
    @BeforeAll static void bootstrap() { MinecraftBootstrap.initialize(); }
    @Test void exactReleaseGateAndNoOtherFamily() {
        for (String side : java.util.List.of("BEFORE", "REPLACED", "ACK")) {
            String cut = "LF_S6_O1_" + side;
            assertTrue(EscrowLoaderS6SettlementProbe.selected(true, cut));
            assertFalse(EscrowLoaderS6SettlementProbe.selected(false, cut));
            assertFalse(EscrowLoaderS6SettlementProbe.selected(true, cut + "_OTHER"));
        }
        assertFalse(EscrowLoaderS6SettlementProbe.selected(true, null));
    }
    @Test void cutsDistinguishReplacementFromTheOwnerCallback() {
        String[] cuts = {"LF_S6_O1_BEFORE", "LF_S6_O1_REPLACED", "LF_S6_O1_ACK"};
        String[] hooks = {"K1", "K2", "O1_ACK"};
        for (int i = 0; i < cuts.length; i++) {
            for (int j = 0; j < hooks.length; j++) {
                assertEquals(i == j, EscrowLoaderS6SettlementProbe.boundary(cuts[i], hooks[j]));
            }
            assertFalse(EscrowLoaderS6SettlementProbe.boundary(cuts[i], "K9"));
        }
    }
}
