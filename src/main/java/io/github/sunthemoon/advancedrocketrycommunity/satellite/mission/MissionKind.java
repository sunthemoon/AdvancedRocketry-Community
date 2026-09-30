package io.github.sunthemoon.advancedrocketrycommunity.satellite.mission;

import java.util.Locale;
import java.util.Optional;

/** Mission kinds persisted by lowercase id (ADR-050 §3). */
public enum MissionKind {
    DATA,
    SURVEY,
    ASTEROID,
    GAS;

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static Optional<MissionKind> parse(String raw) {
        if (raw == null || raw.isEmpty() || raw.length() > 16) {
            return Optional.empty();
        }
        for (MissionKind kind : values()) {
            if (kind.id().equals(raw)) {
                return Optional.of(kind);
            }
        }
        return Optional.empty();
    }

    /** Asteroid and gas rewards are items delivered through a bound terminal (ADR-051). */
    public boolean resource() {
        return this == ASTEROID || this == GAS;
    }
}
