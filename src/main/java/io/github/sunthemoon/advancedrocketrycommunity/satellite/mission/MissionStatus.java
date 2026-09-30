package io.github.sunthemoon.advancedrocketrycommunity.satellite.mission;

/** Persisted by lowercase name; new values are only appended. */
public enum MissionStatus {
    ACTIVE,
    READY,
    CLAIM_PENDING_DISCOVERY,
    CLAIMED,
    CANCELLED,
    /** Held for operator diagnosis; counts as unfinished for every limit (ADR-050 §4). */
    QUARANTINED;

    public boolean unfinished() {
        return this == ACTIVE || this == READY || this == CLAIM_PENDING_DISCOVERY || this == QUARANTINED;
    }
}
