package io.github.sunthemoon.advancedrocketrycommunity.machine.multiblock.lifecycle;

import io.github.sunthemoon.advancedrocketrycommunity.machine.multiblock.pattern.PatternValidationResult;
import java.util.Objects;

public record MultiblockLifecycleResult(
        MultiblockControllerState controllerState,
        PatternValidationResult validation,
        LifecycleMutation mutation,
        int changedBindings
) {
    public MultiblockLifecycleResult {
        Objects.requireNonNull(controllerState, "controllerState");
        Objects.requireNonNull(validation, "validation");
        Objects.requireNonNull(mutation, "mutation");
        if (changedBindings < 0 || changedBindings > MultiblockControllerState.MAX_PARTS) {
            throw new IllegalArgumentException("changed binding count exceeds the part limit");
        }
    }
}
