package io.github.sunthemoon.advancedrocketrycommunity.machine.classic.adapter;

import io.github.sunthemoon.advancedrocketrycommunity.machine.multiblock.pattern.MultiblockPatternDefinition;
import java.util.Objects;

/** Immutable selection data, not an owner binding or guard witness. */
record ClassicPatternSelection(long catalogGeneration, MultiblockPatternDefinition definition) {
    ClassicPatternSelection {
        ClassicValueChecks.require(catalogGeneration >= 0, "Negative pattern generation");
        Objects.requireNonNull(definition, "definition");
    }
}
