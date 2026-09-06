package io.github.sunthemoon.advancedrocketrycommunity.machine.process;

import java.util.Objects;
import java.util.Optional;

/** Result of a side-effect-free completion simulation. */
public record ProcessSimulationResult(Optional<ProcessPlan> plan, ProcessFailure failure) {
    public ProcessSimulationResult {
        Objects.requireNonNull(plan, "plan");
        Objects.requireNonNull(failure, "failure");
        if (plan.isPresent() == (failure.code() != ProcessFailureCode.NONE)) {
            throw new IllegalArgumentException("simulation must contain either a plan or a failure");
        }
    }

    public static ProcessSimulationResult success(ProcessPlan plan) {
        return new ProcessSimulationResult(Optional.of(plan), ProcessFailure.NONE);
    }

    public static ProcessSimulationResult failure(ProcessFailureCode code, String subject) {
        if (code == ProcessFailureCode.NONE) {
            throw new IllegalArgumentException("failure result requires a failure code");
        }
        return new ProcessSimulationResult(Optional.empty(), new ProcessFailure(code, subject));
    }

    public boolean successful() {
        return plan.isPresent();
    }
}
