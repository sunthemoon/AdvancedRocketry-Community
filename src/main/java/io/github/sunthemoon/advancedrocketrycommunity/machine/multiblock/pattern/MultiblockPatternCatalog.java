package io.github.sunthemoon.advancedrocketrycommunity.machine.multiblock.pattern;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

/** Immutable, bounded snapshot of the server's active machine patterns. */
public final class MultiblockPatternCatalog {
    public static final int MAX_DEFINITIONS = 256;

    private final Map<String, MultiblockPatternDefinition> definitions;

    private MultiblockPatternCatalog(Map<String, MultiblockPatternDefinition> definitions) {
        this.definitions = definitions;
    }

    public static MultiblockPatternCatalog create(
            Collection<MultiblockPatternDefinition> definitions
    ) {
        if (definitions == null || definitions.isEmpty()) {
            throw new PatternDefinitionException("pattern catalog: must contain at least one definition");
        }
        if (definitions.size() > MAX_DEFINITIONS) {
            throw new PatternDefinitionException("pattern catalog: exceeds 256 definitions");
        }
        TreeMap<String, MultiblockPatternDefinition> indexed = new TreeMap<>();
        for (MultiblockPatternDefinition definition : definitions) {
            if (definition == null) {
                throw new PatternDefinitionException("pattern catalog: contains a null definition");
            }
            if (indexed.put(definition.id(), definition) != null) {
                throw new PatternDefinitionException(
                        "pattern catalog: duplicate definition " + definition.id()
                );
            }
        }
        return new MultiblockPatternCatalog(Collections.unmodifiableMap(indexed));
    }

    public Optional<MultiblockPatternDefinition> get(String id) {
        return Optional.ofNullable(definitions.get(PatternIds.requireResourceId("id", id)));
    }

    public Collection<MultiblockPatternDefinition> definitions() {
        return definitions.values();
    }

    public int size() {
        return definitions.size();
    }
}
