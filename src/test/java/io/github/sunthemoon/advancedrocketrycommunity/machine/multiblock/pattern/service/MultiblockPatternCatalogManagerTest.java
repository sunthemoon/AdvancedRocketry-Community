package io.github.sunthemoon.advancedrocketrycommunity.machine.multiblock.pattern.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.sunthemoon.advancedrocketrycommunity.machine.multiblock.pattern.MultiblockPatternCatalog;
import io.github.sunthemoon.advancedrocketrycommunity.machine.multiblock.pattern.MultiblockPatternDefinition;
import io.github.sunthemoon.advancedrocketrycommunity.machine.multiblock.pattern.PatternMatcher;
import io.github.sunthemoon.advancedrocketrycommunity.machine.multiblock.pattern.PatternPosition;
import io.github.sunthemoon.advancedrocketrycommunity.machine.multiblock.pattern.PatternRotation;
import io.github.sunthemoon.advancedrocketrycommunity.machine.multiblock.pattern.PatternSize;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class MultiblockPatternCatalogManagerTest {
    @Test
    void rejectionRetainsLastValidCatalogAndGeneration() {
        MultiblockPatternCatalogManager manager = new MultiblockPatternCatalogManager();
        MultiblockPatternCatalog accepted = catalog("test:first");

        manager.accept(accepted);
        manager.reject("broken replacement");

        assertSame(accepted, manager.current().orElseThrow());
        assertTrue(manager.status().ready());
        assertFalse(manager.status().lastReloadAccepted());
        assertEquals(1L, manager.status().generation());
        assertEquals(1, manager.status().definitionCount());
        assertEquals("broken replacement", manager.status().message());
    }

    @Test
    void clearDropsCatalogAndReloadStatus() {
        MultiblockPatternCatalogManager manager = new MultiblockPatternCatalogManager();
        manager.accept(catalog("test:first"));

        manager.clear();

        assertTrue(manager.current().isEmpty());
        assertFalse(manager.status().ready());
        assertEquals(0L, manager.status().generation());
    }

    @Test
    void rejectionMessageIsBounded() {
        MultiblockPatternCatalogManager manager = new MultiblockPatternCatalogManager();

        manager.reject("x".repeat(MultiblockPatternCatalogManager.MAX_STATUS_MESSAGE_CHARS + 20));

        assertEquals(
                MultiblockPatternCatalogManager.MAX_STATUS_MESSAGE_CHARS,
                manager.status().message().length()
        );
        assertTrue(manager.status().message().endsWith("..."));
    }

    private static MultiblockPatternCatalog catalog(String id) {
        PatternPosition origin = new PatternPosition(0, 0, 0);
        return MultiblockPatternCatalog.create(java.util.List.of(
                new MultiblockPatternDefinition(
                        id,
                        MultiblockPatternDefinition.SCHEMA_VERSION,
                        1,
                        new PatternSize(1, 1, 1),
                        origin,
                        Set.of(PatternRotation.ZERO),
                        false,
                        Map.of(origin, new PatternMatcher.Controller())
                )
        ));
    }
}
