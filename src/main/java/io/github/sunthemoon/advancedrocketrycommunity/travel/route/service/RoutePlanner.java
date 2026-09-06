package io.github.sunthemoon.advancedrocketrycommunity.travel.route.service;

import com.mojang.serialization.DataResult;
import io.github.sunthemoon.advancedrocketrycommunity.travel.route.model.RouteAnchor;
import io.github.sunthemoon.advancedrocketrycommunity.travel.route.model.RouteLeg;
import io.github.sunthemoon.advancedrocketrycommunity.travel.route.model.RouteLimits;
import io.github.sunthemoon.advancedrocketrycommunity.travel.route.model.RoutePlan;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.PriorityQueue;
import java.util.HashSet;
import java.util.Set;
import net.minecraft.resources.ResourceLocation;

/** Deterministic bounded Dijkstra search over one validated immutable catalog. */
final class RoutePlanner {
    private static final Comparator<SearchState> STATE_ORDER = (left, right) -> {
        int distanceOrder = Long.compare(left.distance, right.distance);
        if (distanceOrder != 0) {
            return distanceOrder;
        }
        int pathOrder = compareRouteIds(left.legs, right.legs);
        return pathOrder != 0 ? pathOrder : left.anchor.compareTo(right.anchor);
    };

    private RoutePlanner() {
    }

    static DataResult<RoutePlan> plan(
            RouteCatalog catalog,
            RouteAnchor source,
            RouteAnchor destination
    ) {
        Objects.requireNonNull(catalog, "catalog");
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(destination, "destination");
        if (source.equals(destination)) {
            return DataResult.error(() -> "Route source and destination must differ");
        }
        if (!catalog.containsAnchor(source) || !catalog.containsAnchor(destination)) {
            return DataResult.error(() -> "Route endpoint is not present in the active graph");
        }

        PriorityQueue<SearchState> pending = new PriorityQueue<>(STATE_ORDER);
        Map<RouteAnchor, SearchState> best = new HashMap<>();
        Set<RouteAnchor> settled = new HashSet<>();
        SearchState initial = new SearchState(source, 0L, List.of());
        pending.add(initial);
        best.put(source, initial);
        int expanded = 0;

        while (!pending.isEmpty()) {
            SearchState current = pending.remove();
            if (best.get(current.anchor) != current) {
                continue;
            }
            if (!settled.add(current.anchor)) {
                continue;
            }
            expanded++;
            if (expanded > RouteLimits.MAX_EXPANDED_NODES) {
                return DataResult.error(() -> "Route planning exceeded "
                        + RouteLimits.MAX_EXPANDED_NODES + " expanded nodes");
            }
            if (current.anchor.equals(destination)) {
                return DataResult.success(new RoutePlan(
                        source,
                        destination,
                        current.legs,
                        current.distance,
                        expanded
                ));
            }

            for (RouteCatalog.RouteEdge edge : catalog.outgoing(current.anchor)) {
                if (settled.contains(edge.to())) {
                    continue;
                }
                long candidateDistance;
                try {
                    candidateDistance = Math.addExact(current.distance, edge.distanceUnits());
                } catch (ArithmeticException exception) {
                    return DataResult.error(() -> "Route distance overflow");
                }
                List<RouteLeg> candidateLegs = new ArrayList<>(current.legs);
                candidateLegs.add(new RouteLeg(
                        edge.routeId(),
                        edge.from(),
                        edge.to(),
                        edge.distanceUnits()
                ));
                SearchState candidate = new SearchState(edge.to(), candidateDistance, List.copyOf(candidateLegs));
                SearchState previous = best.get(edge.to());
                if (previous == null || STATE_ORDER.compare(candidate, previous) < 0) {
                    best.put(edge.to(), candidate);
                    pending.add(candidate);
                }
            }
        }
        return DataResult.error(() -> "No route from " + source + " to " + destination);
    }

    private static int compareRouteIds(List<RouteLeg> left, List<RouteLeg> right) {
        int common = Math.min(left.size(), right.size());
        for (int index = 0; index < common; index++) {
            ResourceLocation leftId = left.get(index).routeId();
            ResourceLocation rightId = right.get(index).routeId();
            int order = leftId.compareTo(rightId);
            if (order != 0) {
                return order;
            }
        }
        return Integer.compare(left.size(), right.size());
    }

    private record SearchState(RouteAnchor anchor, long distance, List<RouteLeg> legs) {
    }
}
