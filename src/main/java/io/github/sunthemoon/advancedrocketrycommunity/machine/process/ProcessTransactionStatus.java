package io.github.sunthemoon.advancedrocketrycommunity.machine.process;

/** Commit/recovery outcomes consumed by the server-side machine adapter. */
public enum ProcessTransactionStatus {
    APPLIED,
    DUPLICATE,
    RECOVERED_APPLY,
    RECOVERED_FINALIZE,
    NO_TRANSACTION,
    STALE_TRANSACTION,
    JOURNAL_CONFLICT,
    RECOVERY_REQUIRED
}
