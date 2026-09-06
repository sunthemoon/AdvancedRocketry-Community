package io.github.sunthemoon.advancedrocketrycommunity.machine.process;

import java.util.Objects;

/** Immutable completion plan produced without mutating the resource store. */
public record ProcessPlan(
        String definitionId,
        long expectedRevision,
        String expectedFingerprint,
        ProcessResourceSnapshot before,
        ProcessResourceSnapshot after
) {
    public ProcessPlan {
        Objects.requireNonNull(definitionId, "definitionId");
        Objects.requireNonNull(expectedFingerprint, "expectedFingerprint");
        Objects.requireNonNull(before, "before");
        Objects.requireNonNull(after, "after");
        if (expectedRevision != before.revision()) {
            throw new IllegalArgumentException("expected revision does not match the before snapshot");
        }
        if (!expectedFingerprint.equals(before.fingerprint())) {
            throw new IllegalArgumentException("expected fingerprint does not match the before snapshot");
        }
        if (after.revision() != Math.addExact(before.revision(), 1)) {
            throw new IllegalArgumentException("after revision must advance exactly once");
        }
    }
}
