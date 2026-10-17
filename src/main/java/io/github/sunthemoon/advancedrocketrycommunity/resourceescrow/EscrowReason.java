package io.github.sunthemoon.advancedrocketrycommunity.resourceescrow;

/** Persisted manual conflicts never clear merely because an endpoint reappears. */
public enum EscrowReason {
    NONE, WAITING_UNLOADED, RECONCILIATION_UNIMPLEMENTED, BARRIER_IO, BARRIER_FORCE, BARRIER_UNSTABLE, BARRIER_VETO,
    BARRIER_EXTERNAL, BARRIER_BOUNDS, SUPERSEDING_SAVE, LEDGER_WRITE_FAILED, LEDGER_UNAVAILABLE,
    MISSING_ENDPOINT, OTHER_STATE, FOREIGN_MARKER, LEDGER_REPLACED, LEDGER_BEHIND,
    INVALID_ENDPOINT, RELOCATED_ENDPOINT, FLOOR_CONFLICT, JOURNAL_GUARD_MISMATCH, TRANSFER_JOURNAL_UNPROVED,
    ENDPOINT_RECORD_TOO_LARGE, ESCROW_FULL;

    public boolean manual() {
        return switch (this) {
            case MISSING_ENDPOINT, OTHER_STATE, FOREIGN_MARKER, LEDGER_REPLACED, LEDGER_BEHIND,
                    INVALID_ENDPOINT, RELOCATED_ENDPOINT, FLOOR_CONFLICT, JOURNAL_GUARD_MISMATCH -> true;
            default -> false;
        };
    }

    public boolean barrierFailure() {
        return switch (this) {
            case BARRIER_IO, BARRIER_FORCE, BARRIER_UNSTABLE, BARRIER_VETO, BARRIER_EXTERNAL,
                    BARRIER_BOUNDS -> true;
            default -> false;
        };
    }

    public boolean retryableProofFailure() { return barrierFailure() || this == SUPERSEDING_SAVE; }

    public static int backoff(int failures) {
        if (failures <= 0) { throw new IllegalArgumentException("Nonpositive failure count"); }
        return failures == 1 ? 100 : failures == 2 ? 400 : 1_200;
    }
}
