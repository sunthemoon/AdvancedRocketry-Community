package io.github.sunthemoon.advancedrocketrycommunity.station.orbit;

import java.util.Objects;
import java.util.UUID;
import net.minecraft.resources.ResourceLocation;

/**
 * ADR-041 effective environment inside one committed station region. Configured gravity is the
 * stored value reported by the public API; effective gravity is what player physics uses.
 */
public record StationOrbitEnvironment(
        UUID stationId,
        String stationName,
        ResourceLocation orbitBody,
        boolean orbitBodyAvailable,
        double configuredGravity,
        double effectiveGravity,
        boolean vacuum,
        double solarIntensity,
        double sunAngleDegrees
) {
    public StationOrbitEnvironment {
        Objects.requireNonNull(stationId, "stationId");
        Objects.requireNonNull(stationName, "stationName");
        Objects.requireNonNull(orbitBody, "orbitBody");
        if (effectiveGravity > configuredGravity || effectiveGravity < 0.0D) {
            throw new IllegalArgumentException("Effective gravity must be the clamped configured gravity");
        }
    }

    public boolean gravityClamped() {
        return effectiveGravity < configuredGravity;
    }
}
