package io.github.sunthemoon.advancedrocketrycommunity.endgame.model;

import java.util.Objects;

/**
 * ADR-054 sections 1, 4, 6 and 9: the framework switches and limits, read at each use so a config reload applies on
 * the next tick. Limits can only be lowered below their {@link EndgameLimits} maxima.
 */
public record EndgameSettings(boolean laserDrill, boolean laserPhysicalMining, boolean railgun,
                              boolean blackHoleGenerator, boolean gravityField, boolean spaceElevator,
                              int intentIntervalTicks, int selectionIntervalTicks, int endpointsGlobal,
                              int endpointsPerOwner, int zones) {
    public static final EndgameSettings DEFAULTS = new EndgameSettings(true, false, true, true, true, true,
            EndgameLimits.MIN_INTENT_TICKS, EndgameLimits.MIN_SELECTION_TICKS, EndgameLimits.MAX_ENDPOINTS,
            EndgameLimits.MAX_ENDPOINTS_PER_OWNER, EndgameLimits.MAX_ZONES);

    public EndgameSettings {
        bounded(intentIntervalTicks, EndgameLimits.MIN_INTENT_TICKS, EndgameLimits.MAX_INTERVAL_TICKS, "intent interval");
        bounded(selectionIntervalTicks, EndgameLimits.MIN_SELECTION_TICKS, EndgameLimits.MAX_INTERVAL_TICKS,
                "selection interval");
        bounded(endpointsGlobal, 1, EndgameLimits.MAX_ENDPOINTS, "endpoints");
        bounded(endpointsPerOwner, 1, EndgameLimits.MAX_ENDPOINTS_PER_OWNER, "endpoints per owner");
        bounded(zones, 1, EndgameLimits.MAX_ZONES, "zones");
    }

    /** Whether a system may start new operations; settlement and recovery run regardless (section 1). */
    public boolean enabled(EndgameSystem system) {
        Objects.requireNonNull(system, "system");
        return switch (system) {
            case LASER_DRILL -> laserDrill;
            case RAILGUN -> railgun;
            case BLACK_HOLE_GENERATOR -> blackHoleGenerator;
            case GRAVITY_FIELD -> gravityField;
            case SPACE_ELEVATOR -> spaceElevator;
        };
    }

    private static void bounded(int value, int min, int max, String name) {
        if (value < min || value > max) {
            throw new IllegalArgumentException("Endgame " + name + " is outside " + min + ".." + max);
        }
    }
}
