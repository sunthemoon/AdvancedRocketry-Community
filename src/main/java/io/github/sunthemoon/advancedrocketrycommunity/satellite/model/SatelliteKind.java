package io.github.sunthemoon.advancedrocketrycommunity.satellite.model;

import java.util.Locale;
import java.util.Optional;

/** Closed host set of satellite kinds, persisted by lowercase id (ADR-049 §1). */
public enum SatelliteKind {
    DATA,
    SURVEY,
    SOLAR,
    ASTEROID_MINER,
    GAS_HARVESTER;

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static Optional<SatelliteKind> parse(String raw) {
        if (raw == null || raw.isEmpty() || raw.length() > 32) {
            return Optional.empty();
        }
        for (SatelliteKind kind : values()) {
            if (kind.id().equals(raw)) {
                return Optional.of(kind);
            }
        }
        return Optional.empty();
    }

    /** Kinds that launch idle into an orbit body instead of starting a first mission. */
    public boolean launchesIdle() {
        return this != DATA;
    }
}
