package io.github.sunthemoon.advancedrocketrycommunity.travel.route.model;

import java.util.Objects;
import net.minecraft.resources.ResourceLocation;

public record RouteLeg(
        ResourceLocation routeId,
        RouteAnchor from,
        RouteAnchor to,
        int distanceUnits
) {
    public RouteLeg {
        Objects.requireNonNull(routeId, "routeId");
        Objects.requireNonNull(from, "from");
        Objects.requireNonNull(to, "to");
        if (distanceUnits < 0 || distanceUnits > RouteLimits.MAX_DISTANCE_UNITS) {
            throw new IllegalArgumentException("Route leg distance is outside its fixed bound");
        }
    }
}
