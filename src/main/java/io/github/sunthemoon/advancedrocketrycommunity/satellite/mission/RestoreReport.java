package io.github.sunthemoon.advancedrocketrycommunity.satellite.mission;

/** What the load invariants changed (ADR-050 section 9). */
public record RestoreReport(int quarantinedMissions, int recoveries, int quarantinedInstances, int accountsAdded) {
    public boolean changed() {
        return quarantinedMissions > 0 || recoveries > 0 || quarantinedInstances > 0 || accountsAdded > 0;
    }
}
