package io.github.sunthemoon.advancedrocketrycommunity.gametest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;
import org.junit.jupiter.api.Test;

class GameTestBatchFilterTest {
    @Test
    void parsesTrimmedCommaSeparatedNamesAndIgnoresEmptyOnes() {
        assertEquals(Set.of("escrow_core", "railgun"), GameTestBatchFilter.names(" escrow_core, ,railgun ,"));
        assertTrue(GameTestBatchFilter.names("").isEmpty());
        assertTrue(GameTestBatchFilter.names(" , ").isEmpty());
    }

    @Test
    void keepsEveryNumberedPartOfANamedBatchOnly() {
        Set<String> names = Set.of("escrow_core");
        assertTrue(GameTestBatchFilter.keeps(names, "escrow_core:1"));
        assertTrue(GameTestBatchFilter.keeps(names, "escrow_core:12"));
        assertTrue(GameTestBatchFilter.keeps(names, "escrow_core"));
        assertFalse(GameTestBatchFilter.keeps(names, "escrow_core_chain:1"));
        assertFalse(GameTestBatchFilter.keeps(names, "escrow:1"));
        assertFalse(GameTestBatchFilter.keeps(names, "defaultBatch:1"));
    }
}
