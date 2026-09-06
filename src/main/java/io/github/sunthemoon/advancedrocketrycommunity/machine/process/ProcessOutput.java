package io.github.sunthemoon.advancedrocketrycommunity.machine.process;

import java.util.Objects;

/** One concrete output produced by a completed batch. */
public record ProcessOutput(ProcessResourceKey key, long amount) {
    public ProcessOutput {
        Objects.requireNonNull(key, "key");
        if (amount < 1) {
            throw new IllegalArgumentException("output amount must be positive");
        }
    }
}
