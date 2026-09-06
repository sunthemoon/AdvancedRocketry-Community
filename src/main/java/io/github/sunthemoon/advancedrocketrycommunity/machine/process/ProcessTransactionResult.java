package io.github.sunthemoon.advancedrocketrycommunity.machine.process;

import java.util.Objects;

/** Structured transaction outcome with stable failure details. */
public record ProcessTransactionResult(ProcessTransactionStatus status, ProcessFailure failure) {
    public ProcessTransactionResult {
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(failure, "failure");
    }

    public static ProcessTransactionResult success(ProcessTransactionStatus status) {
        return new ProcessTransactionResult(status, ProcessFailure.NONE);
    }

    public static ProcessTransactionResult failure(
            ProcessTransactionStatus status,
            ProcessFailureCode code,
            String subject
    ) {
        return new ProcessTransactionResult(status, new ProcessFailure(code, subject));
    }
}
