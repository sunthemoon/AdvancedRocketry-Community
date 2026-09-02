package io.github.sunthemoon.advancedrocketrycommunity.satellite.mission;

public enum MissionStatus {
    ACTIVE,
    READY,
    CLAIM_PENDING_DISCOVERY,
    CLAIMED,
    CANCELLED;

    public boolean unfinished() {
        return this == ACTIVE || this == READY || this == CLAIM_PENDING_DISCOVERY;
    }
}
