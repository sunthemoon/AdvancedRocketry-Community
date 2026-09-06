package io.github.sunthemoon.advancedrocketrycommunity.machine.multiblock.pattern;

import java.util.Objects;
import java.util.Optional;

/** World observation that distinguishes an unloaded position from a loaded block. */
public record PatternObservation(boolean loaded, Optional<PatternBlock> block) {
    public PatternObservation {
        Objects.requireNonNull(block, "block");
        if (loaded != block.isPresent()) {
            throw new IllegalArgumentException("loaded observations require exactly one block");
        }
    }

    public static PatternObservation unloaded() {
        return new PatternObservation(false, Optional.empty());
    }

    public static PatternObservation loaded(PatternBlock block) {
        return new PatternObservation(true, Optional.of(block));
    }
}
