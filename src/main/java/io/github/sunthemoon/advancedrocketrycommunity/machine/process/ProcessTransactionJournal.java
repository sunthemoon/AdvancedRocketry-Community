package io.github.sunthemoon.advancedrocketrycommunity.machine.process;

import java.util.Objects;
import java.util.UUID;

/** Schema-versioned transaction journal containing complete bounded before/after snapshots. */
public record ProcessTransactionJournal(
        int schemaVersion,
        UUID transactionId,
        UUID machineId,
        String definitionId,
        long portRevision,
        String beforeFingerprint,
        ProcessResourceSnapshot before,
        ProcessResourceSnapshot after,
        ProcessJournalPhase phase
) {
    public static final int SCHEMA_VERSION = 1;

    public ProcessTransactionJournal {
        if (schemaVersion != SCHEMA_VERSION) {
            throw new IllegalArgumentException("unsupported process journal schema");
        }
        Objects.requireNonNull(transactionId, "transactionId");
        Objects.requireNonNull(machineId, "machineId");
        Objects.requireNonNull(definitionId, "definitionId");
        Objects.requireNonNull(beforeFingerprint, "beforeFingerprint");
        Objects.requireNonNull(before, "before");
        Objects.requireNonNull(after, "after");
        Objects.requireNonNull(phase, "phase");
        if (portRevision != before.revision() || !beforeFingerprint.equals(before.fingerprint())) {
            throw new IllegalArgumentException("journal identity does not match the before snapshot");
        }
        if (after.revision() != Math.addExact(before.revision(), 1)) {
            throw new IllegalArgumentException("journal after revision must advance exactly once");
        }
    }

    public static ProcessTransactionJournal prepared(UUID transactionId, UUID machineId, ProcessPlan plan) {
        return new ProcessTransactionJournal(
                SCHEMA_VERSION,
                transactionId,
                machineId,
                plan.definitionId(),
                plan.expectedRevision(),
                plan.expectedFingerprint(),
                plan.before(),
                plan.after(),
                ProcessJournalPhase.PREPARED
        );
    }

    public ProcessTransactionJournal advance(ProcessJournalPhase nextPhase) {
        if (nextPhase.ordinal() < phase.ordinal() || nextPhase.ordinal() > phase.ordinal() + 1) {
            throw new IllegalArgumentException("journal phase transition is not monotonic");
        }
        if (nextPhase == phase) {
            return this;
        }
        return new ProcessTransactionJournal(
                schemaVersion,
                transactionId,
                machineId,
                definitionId,
                portRevision,
                beforeFingerprint,
                before,
                after,
                nextPhase
        );
    }
}
