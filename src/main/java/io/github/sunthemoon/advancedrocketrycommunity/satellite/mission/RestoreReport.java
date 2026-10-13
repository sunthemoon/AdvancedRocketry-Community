package io.github.sunthemoon.advancedrocketrycommunity.satellite.mission;

/** What the load invariants changed (ADR-050 section 9). */
public record RestoreReport(int quarantinedMissions, int recoveries, int quarantinedInstances, int accountsAdded,
                            int quarantinedGroundSurveys) {
    public RestoreReport(int missions, int recoveries, int instances, int accounts) {
        this(missions, recoveries, instances, accounts, 0);
    }

    public boolean changed() {
        return quarantinedMissions > 0 || recoveries > 0 || quarantinedInstances > 0 || accountsAdded > 0
                || quarantinedGroundSurveys > 0;
    }
}
