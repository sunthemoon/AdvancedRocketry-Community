package io.github.sunthemoon.advancedrocketrycommunity.resourceescrow;

import static org.junit.jupiter.api.Assertions.*;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class EscrowLoaderS6RefillProbeTest {
    @BeforeAll static void bootstrap() { MinecraftBootstrap.initialize(); }

    @Test void onlyTheTwoExactRefillSelectionsAreEnabled() {
        for (var cut : new String[] {"LF_S6_REFILL", "LF_S6_REFILL_BI"}) {
            assertTrue(EscrowLoaderS6RefillProbe.selected(true, cut));
            assertFalse(EscrowLoaderS6RefillProbe.selected(false, cut));
            assertFalse(EscrowLoaderS6RefillProbe.selected(true, cut + "_OTHER"));
        }
        assertFalse(EscrowLoaderS6RefillProbe.selected(true, null));
    }

    @Test void eachFillHasTwoWritesBesidesTheSingleBaselineAndFlight() {
        assertEquals(3, EscrowLoaderS6RefillProbe.generation(1, false));
        assertEquals(5, EscrowLoaderS6RefillProbe.generation(2, false));
        assertEquals(8, EscrowLoaderS6RefillProbe.generation(2, true));
        assertEquals(12, EscrowLoaderS6RefillProbe.generation(4, true));
        assertTrue(EscrowLoaderS6RefillProbe.refusalBoundary("F7_ACK"));
        assertTrue(EscrowLoaderS6RefillProbe.refusalBoundary("F8_JOURNAL_ACK"));
        assertFalse(EscrowLoaderS6RefillProbe.refusalBoundary("F8_PRUNE_ACK"));
    }
}
