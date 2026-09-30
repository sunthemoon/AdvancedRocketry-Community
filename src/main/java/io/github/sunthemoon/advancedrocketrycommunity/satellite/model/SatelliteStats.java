package io.github.sunthemoon.advancedrocketrycommunity.satellite.model;

/** Blueprint stats fixed at launch (ADR-049 §4); every value is within its cap. */
public record SatelliteStats(int power, int battery, int data, int cargo, int rating) {
    /** The label given to the terminal's fixed {@code data} recipe and to migrated records. */
    public static final SatelliteStats LEGACY_DATA = new SatelliteStats(4, SatelliteLimits.BASE_BATTERY, 1_000, 0, 0);

    public SatelliteStats {
        check(power, 0, SatelliteLimits.MAX_POWER, "power");
        check(battery, SatelliteLimits.BASE_BATTERY, SatelliteLimits.MAX_BATTERY, "battery");
        check(data, 0, SatelliteLimits.MAX_DATA, "data");
        check(cargo, 0, SatelliteLimits.MAX_CARGO, "cargo");
        check(rating, 0, SatelliteLimits.MAX_RATING, "rating");
    }

    private static void check(int value, int minimum, int maximum, String name) {
        if (value < minimum || value > maximum) {
            throw new IllegalArgumentException("Satellite stat " + name + " is outside its cap");
        }
    }
}
