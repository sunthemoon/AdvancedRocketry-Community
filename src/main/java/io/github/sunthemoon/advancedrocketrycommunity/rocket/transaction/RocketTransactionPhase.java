package io.github.sunthemoon.advancedrocketrycommunity.rocket.transaction;

public enum RocketTransactionPhase {
    SNAPSHOT_VALIDATED,
    LOCKED,
    EXTRACTING,
    EXTRACTED,
    SPAWNED,
    RESTORING,
    RESTORED,
    COMMITTED,
    ROLLING_BACK,
    ROLLED_BACK,
    FAILED
}
