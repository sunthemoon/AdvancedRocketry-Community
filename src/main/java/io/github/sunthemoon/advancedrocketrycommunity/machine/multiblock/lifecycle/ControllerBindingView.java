package io.github.sunthemoon.advancedrocketrycommunity.machine.multiblock.lifecycle;

import java.util.Objects;
import java.util.UUID;

/** Minimal loaded-controller identity required to validate a part binding. */
public record ControllerBindingView(
        UUID machineInstanceId,
        long generation,
        MultiblockFormationState formationState
) {
    public ControllerBindingView {
        Objects.requireNonNull(machineInstanceId, "machineInstanceId");
        if (generation < 0) {
            throw new IllegalArgumentException("controller binding view generation cannot be negative");
        }
        Objects.requireNonNull(formationState, "formationState");
    }

    public boolean acceptsPartAccess() {
        return formationState == MultiblockFormationState.FORMED
                || formationState == MultiblockFormationState.WAITING_UNLOADED;
    }
}
