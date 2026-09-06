package io.github.sunthemoon.advancedrocketrycommunity.machine.process;

import java.util.Objects;

/** Persistable progress and energy already consumed for one active recipe. */
public record ProcessProgress(String definitionId, int progressTicks, long consumedEnergy) {
    public ProcessProgress {
        Objects.requireNonNull(definitionId, "definitionId");
        new ProcessResourceKey(ProcessResourceKind.ITEM, "recipe", definitionId);
        if (progressTicks < 0 || consumedEnergy < 0) {
            throw new IllegalArgumentException("process progress values cannot be negative");
        }
    }

    public static ProcessProgress notStarted(ProcessDefinition definition) {
        return new ProcessProgress(definition.id(), 0, 0);
    }
}
