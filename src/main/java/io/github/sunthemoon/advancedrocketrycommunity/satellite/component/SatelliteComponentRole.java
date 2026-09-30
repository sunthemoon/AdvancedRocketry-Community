package io.github.sunthemoon.advancedrocketrycommunity.satellite.component;

import java.util.Locale;
import java.util.Optional;

/** Component roles and the one stat each non-primary role contributes (ADR-049 §2). */
public enum SatelliteComponentRole {
    CHASSIS(null),
    PRIMARY("primary_rating"),
    POWER("power_generation"),
    BATTERY("battery_capacity"),
    DATA_STORAGE("data_capacity"),
    CARGO("cargo_stacks");

    private final String statField;

    SatelliteComponentRole(String statField) {
        this.statField = statField;
    }

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    /** The JSON field this role requires, or empty for the chassis. */
    public Optional<String> statField() {
        return Optional.ofNullable(statField);
    }

    public boolean module() {
        return this == POWER || this == BATTERY || this == DATA_STORAGE || this == CARGO;
    }

    public static Optional<SatelliteComponentRole> parse(String raw) {
        for (SatelliteComponentRole role : values()) {
            if (role.id().equals(raw)) {
                return Optional.of(role);
            }
        }
        return Optional.empty();
    }
}
