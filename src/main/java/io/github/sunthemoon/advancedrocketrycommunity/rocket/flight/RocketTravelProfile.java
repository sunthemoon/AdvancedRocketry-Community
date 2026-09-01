package io.github.sunthemoon.advancedrocketrycommunity.rocket.flight;

import io.github.sunthemoon.advancedrocketrycommunity.rocket.RocketLimits;
import java.util.Objects;
import net.minecraft.resources.ResourceLocation;

/** Server-owned bounded values used for deterministic reachability calculations. */
public record RocketTravelProfile(
        ResourceLocation bodyId,
        ResourceLocation dimensionId,
        int gravityMilli,
        int routeDistanceUnits
) {
    public RocketTravelProfile {
        Objects.requireNonNull(bodyId, "bodyId");
        Objects.requireNonNull(dimensionId, "dimensionId");
        if (bodyId.toString().length() > RocketLimits.MAX_IDENTIFIER_LENGTH
                || dimensionId.toString().length() > RocketLimits.MAX_IDENTIFIER_LENGTH) {
            throw new IllegalArgumentException("Travel profile identifier exceeds the fixed limit");
        }
        if (gravityMilli < 0 || gravityMilli > 10_000) {
            throw new IllegalArgumentException("Travel profile gravity is outside the fixed limit");
        }
        if (routeDistanceUnits < 0 || routeDistanceUnits > 1_000_000) {
            throw new IllegalArgumentException("Travel profile distance is outside the fixed limit");
        }
    }
}
