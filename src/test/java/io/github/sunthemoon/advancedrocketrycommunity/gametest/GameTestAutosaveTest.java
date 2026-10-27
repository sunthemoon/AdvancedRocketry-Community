package io.github.sunthemoon.advancedrocketrycommunity.gametest;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class GameTestAutosaveTest {
    @Test
    void countsTheTickEndsToVanillasNextAutosave() {
        assertEquals(0, GameTestAutosave.ticksUntil(0));
        assertEquals(0, GameTestAutosave.ticksUntil(6_000));
        assertEquals(5_999, GameTestAutosave.ticksUntil(1));
        assertEquals(60, GameTestAutosave.ticksUntil(5_940));
        assertEquals(61, GameTestAutosave.ticksUntil(17_939));
        assertEquals(1, GameTestAutosave.ticksUntil(11_999));
    }
}
