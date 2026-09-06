package io.github.sunthemoon.advancedrocketrycommunity.travel.route.model;

import java.util.List;
import java.util.Objects;

public record RoutePlan(
        RouteAnchor source,
        RouteAnchor destination,
        List<RouteLeg> legs,
        long totalDistanceUnits,
        int expandedNodes
) {
    public RoutePlan {
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(destination, "destination");
        legs = List.copyOf(legs);
        if (legs.isEmpty() || totalDistanceUnits < 0L) {
            throw new IllegalArgumentException("Route plan must contain a non-negative path");
        }
        if (expandedNodes < 1 || expandedNodes > RouteLimits.MAX_EXPANDED_NODES) {
            throw new IllegalArgumentException("Route plan expansion count is outside its fixed bound");
        }
    }
}
