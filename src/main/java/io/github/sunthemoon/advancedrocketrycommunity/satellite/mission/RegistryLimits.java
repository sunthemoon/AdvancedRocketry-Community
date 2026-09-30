package io.github.sunthemoon.advancedrocketrycommunity.satellite.mission;

import io.github.sunthemoon.advancedrocketrycommunity.satellite.model.SatelliteLimits;

/**
 * ADR-050 section 6 admission limits. Server config may lower them; the maxima are also the defaults, and the
 * intent intervals can only be made longer. Per-owner limits apply at admission only, so an over-limit root
 * still loads and simply admits nothing new until retention has drained it.
 */
public record RegistryLimits(
        int unfinishedGlobal,
        int unfinishedPerOwner,
        int finishedPerOwner,
        int missionsTotal,
        int satellitesGlobal,
        int satellitesPerOwner,
        int instancesGlobal,
        int instancesPerOwner,
        int intentTicks,
        int selectionTicks
) {
    public static final int MAX_UNFINISHED_PER_OWNER = 64;
    public static final int MAX_FINISHED_PER_OWNER = 128;
    public static final int MAX_MISSIONS_TOTAL = 3_072;
    public static final int MAX_INSTANCES_PER_OWNER = 16;
    public static final int MIN_INTENT_TICKS = 10;
    public static final int MIN_SELECTION_TICKS = 2;
    public static final int MAX_INTERVAL_TICKS = 1_200;

    public static final RegistryLimits DEFAULTS = new RegistryLimits(
            SatelliteLimits.MAX_ACTIVE_MISSIONS,
            MAX_UNFINISHED_PER_OWNER,
            MAX_FINISHED_PER_OWNER,
            MAX_MISSIONS_TOTAL,
            SatelliteLimits.MAX_SATELLITES,
            SatelliteLimits.MAX_SATELLITES_PER_OWNER,
            SatelliteLimits.MAX_INSTANCES,
            MAX_INSTANCES_PER_OWNER,
            MIN_INTENT_TICKS,
            MIN_SELECTION_TICKS
    );

    public RegistryLimits {
        within(unfinishedGlobal, SatelliteLimits.MAX_ACTIVE_MISSIONS, "unfinished missions");
        within(unfinishedPerOwner, MAX_UNFINISHED_PER_OWNER, "unfinished missions per owner");
        within(finishedPerOwner, MAX_FINISHED_PER_OWNER, "finished missions per owner");
        within(missionsTotal, MAX_MISSIONS_TOTAL, "missions");
        within(satellitesGlobal, SatelliteLimits.MAX_SATELLITES, "satellites");
        within(satellitesPerOwner, SatelliteLimits.MAX_SATELLITES_PER_OWNER, "satellites per owner");
        within(instancesGlobal, SatelliteLimits.MAX_INSTANCES, "instances");
        within(instancesPerOwner, MAX_INSTANCES_PER_OWNER, "instances per owner");
        if (intentTicks < MIN_INTENT_TICKS || intentTicks > MAX_INTERVAL_TICKS
                || selectionTicks < MIN_SELECTION_TICKS || selectionTicks > MAX_INTERVAL_TICKS) {
            throw new IllegalArgumentException("Intent intervals are outside their bounds");
        }
    }

    private static void within(int value, int maximum, String name) {
        if (value < 1 || value > maximum) {
            throw new IllegalArgumentException("The " + name + " limit must be within 1.." + maximum);
        }
    }
}
