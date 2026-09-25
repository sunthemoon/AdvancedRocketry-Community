package io.github.sunthemoon.advancedrocketrycommunity.machine.process.persistence;

import io.github.sunthemoon.advancedrocketrycommunity.machine.process.ProcessFailure;
import io.github.sunthemoon.advancedrocketrycommunity.machine.process.ProcessMachineState;
import io.github.sunthemoon.advancedrocketrycommunity.machine.process.ProcessProgress;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;

/** Persisted process progress, resource revision and exactly-once replay marker. */
public record ProcessStateData(
        ProcessMachineState state,
        long resourceRevision,
        Optional<ProcessProgress> progress,
        Optional<String> recipeSignature,
        Optional<UUID> lastAppliedTransactionId,
        ProcessFailure failure
) {
    private static final Pattern SIGNATURE = Pattern.compile("[0-9a-f]{64}");

    public ProcessStateData {
        Objects.requireNonNull(state, "state");
        if (resourceRevision < 0) {
            throw new IllegalArgumentException("resource revision cannot be negative");
        }
        progress = Objects.requireNonNull(progress, "progress");
        recipeSignature = Objects.requireNonNull(recipeSignature, "recipeSignature");
        lastAppliedTransactionId = Objects.requireNonNull(
                lastAppliedTransactionId,
                "lastAppliedTransactionId"
        );
        Objects.requireNonNull(failure, "failure");
        if (progress.isPresent() != recipeSignature.isPresent()) {
            throw new IllegalArgumentException("progress and recipe signature must be present together");
        }
        recipeSignature.ifPresent(value -> {
            if (!SIGNATURE.matcher(value).matches()) {
                throw new IllegalArgumentException("recipe signature must be lowercase SHA-256");
            }
        });
    }

    public static ProcessStateData empty() {
        return new ProcessStateData(
                ProcessMachineState.IDLE,
                0,
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                ProcessFailure.NONE
        );
    }
}
