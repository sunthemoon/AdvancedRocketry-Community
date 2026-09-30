package io.github.sunthemoon.advancedrocketrycommunity.satellite.mission;

/** Asteroid instance lifecycle (ADR-051 §2), persisted by lowercase name. */
public enum InstanceState {
    PENDING,
    AVAILABLE,
    ALLOCATED,
    DEPLETED,
    EXPIRED,
    QUARANTINED;

    /** Live instances count toward the per-owner limit. */
    public boolean live() {
        return this == PENDING || this == AVAILABLE || this == ALLOCATED;
    }
}
