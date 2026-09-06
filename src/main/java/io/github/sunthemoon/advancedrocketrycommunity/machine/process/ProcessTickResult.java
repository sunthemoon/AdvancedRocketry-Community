package io.github.sunthemoon.advancedrocketrycommunity.machine.process;

import java.util.Objects;

/** One pure process-state transition; final resource commit remains a separate transaction. */
public record ProcessTickResult(
        ProcessProgress progress,
        long energyConsumed,
        boolean completionDue,
        ProcessMachineState state,
        ProcessFailure failure
) {
    public ProcessTickResult {
        Objects.requireNonNull(progress, "progress");
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(failure, "failure");
        if (energyConsumed < 0) {
            throw new IllegalArgumentException("energyConsumed cannot be negative");
        }
    }
}
