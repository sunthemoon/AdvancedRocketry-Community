package io.github.sunthemoon.advancedrocketrycommunity.client;

import static org.junit.jupiter.api.Assertions.*;

import io.github.sunthemoon.advancedrocketrycommunity.endgame.blackhole.BlackHoleGeneratorBlockEntity;
import org.junit.jupiter.api.Test;

class BlackHoleScreenLayoutTest {
    @Test void metricsHaveASeparateColumnBeyondAllNineFuelSlots() {
        assertEquals(9, BlackHoleGeneratorBlockEntity.FUEL_SLOTS);
        for (int slot = 0; slot < BlackHoleGeneratorBlockEntity.FUEL_SLOTS; slot++) {
            int right = 61 + (slot % 3) * 18 + 18;
            assertTrue(right + 4 <= BlackHoleGeneratorScreen.METRICS_X);
        }
        assertTrue(BlackHoleGeneratorScreen.METRICS_WIDTH >= 100);
        assertEquals(BlackHoleGeneratorScreen.PANEL_WIDTH - 8,
                BlackHoleGeneratorScreen.METRICS_X + BlackHoleGeneratorScreen.METRICS_WIDTH);
    }
}
