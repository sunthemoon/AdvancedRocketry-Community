package io.github.sunthemoon.advancedrocketrycommunity.travel.route.service;

import com.mojang.serialization.DataResult;
import io.github.sunthemoon.advancedrocketrycommunity.travel.route.model.RouteAnchor;
import io.github.sunthemoon.advancedrocketrycommunity.travel.route.model.RouteDefinition;
import io.github.sunthemoon.advancedrocketrycommunity.travel.route.model.RouteLimits;
import io.github.sunthemoon.advancedrocketrycommunity.travel.route.model.RoutePlan;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import net.minecraft.resources.ResourceLocation;

/** Immutable validated route graph with a bounded derived-plan cache. */
public final class RouteCatalog {
    private static final Comparator<RouteEdge> EDGE_ORDER = Comparator
            .comparing(RouteEdge::routeId)
            .thenComparing(RouteEdge::to);

    private final Map<ResourceLocation, RouteDefinition> definitions;
    private final Map<RouteAnchor, List<RouteEdge>> outgoing;
    private final Set<RouteAnchor> anchors;
    private final Map<PlanKey, RoutePlan> planCache = new LinkedHashMap<>(16, 0.75F, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<PlanKey, RoutePlan> eldest) {
            return size() > RouteLimits.MAX_CACHED_PLANS;
        }
    };

    private RouteCatalog(
            Map<ResourceLocation, RouteDefinition> definitions,
            Map<RouteAnchor, List<RouteEdge>> outgoing,
            Set<RouteAnchor> anchors
    ) {
        this.definitions = Collections.unmodifiableMap(new LinkedHashMap<>(definitions));
        Map<RouteAnchor, List<RouteEdge>> immutableOutgoing = new LinkedHashMap<>();
        outgoing.forEach((anchor, edges) -> immutableOutgoing.put(anchor, List.copyOf(edges)));
        this.outgoing = Collections.unmodifiableMap(immutableOutgoing);
        this.anchors = Collections.unmodifiableSet(new LinkedHashSet<>(anchors));
    }

    public static DataResult<RouteCatalog> create(
            Collection<RouteDefinition> values,
            Collection<ResourceLocation> availableBodies
    ) {
        Objects.requireNonNull(values, "values");
        Objects.requireNonNull(availableBodies, "availableBodies");
        if (values.isEmpty()) {
            return DataResult.error(() -> "Route catalog cannot be empty");
        }
        if (values.size() > RouteLimits.MAX_ROUTES) {
            return DataResult.error(() -> "Route catalog exceeds " + RouteLimits.MAX_ROUTES + " routes");
        }

        Set<ResourceLocation> bodies = Set.copyOf(availableBodies);
        List<RouteDefinition> sorted = new ArrayList<>(values);
        sorted.sort(Comparator.comparing(RouteDefinition::id));
        Map<ResourceLocation, RouteDefinition> byId = new LinkedHashMap<>();
        Map<RouteAnchor, List<RouteEdge>> outgoing = new LinkedHashMap<>();
        Set<RouteAnchor> anchors = new LinkedHashSet<>();
        Map<EdgeKey, ResourceLocation> directedEdges = new LinkedHashMap<>();

        for (RouteDefinition definition : sorted) {
            if (byId.putIfAbsent(definition.id(), definition) != null) {
                return DataResult.error(() -> "Duplicate route id " + definition.id());
            }
            if (!bodies.contains(definition.from().bodyId())) {
                return DataResult.error(() -> "Route " + definition.id()
                        + " references missing body " + definition.from().bodyId());
            }
            if (!bodies.contains(definition.to().bodyId())) {
                return DataResult.error(() -> "Route " + definition.id()
                        + " references missing body " + definition.to().bodyId());
            }
            if (definition.from().equals(definition.to())) {
                return DataResult.error(() -> "Route " + definition.id() + " has equal endpoints");
            }
            anchors.add(definition.from());
            anchors.add(definition.to());
            if (anchors.size() > RouteLimits.MAX_ANCHORS) {
                return DataResult.error(() -> "Route catalog exceeds " + RouteLimits.MAX_ANCHORS + " anchors");
            }

            DataResult<Boolean> forward = addEdge(
                    definition,
                    definition.from(),
                    definition.to(),
                    directedEdges,
                    outgoing
            );
            if (forward.error().isPresent()) {
                return DataResult.error(() -> forward.error().orElseThrow().message());
            }
            if (definition.bidirectional()) {
                DataResult<Boolean> reverse = addEdge(
                        definition,
                        definition.to(),
                        definition.from(),
                        directedEdges,
                        outgoing
                );
                if (reverse.error().isPresent()) {
                    return DataResult.error(() -> reverse.error().orElseThrow().message());
                }
            }
        }

        for (RouteAnchor anchor : anchors) {
            List<RouteEdge> edges = outgoing.computeIfAbsent(anchor, ignored -> new ArrayList<>());
            edges.sort(EDGE_ORDER);
            if (edges.size() > RouteLimits.MAX_OUTGOING_EDGES) {
                return DataResult.error(() -> "Route anchor " + anchor + " exceeds "
                        + RouteLimits.MAX_OUTGOING_EDGES + " outgoing edges");
            }
        }
        return DataResult.success(new RouteCatalog(byId, outgoing, anchors));
    }

    public int routeCount() {
        return definitions.size();
    }

    public int anchorCount() {
        return anchors.size();
    }

    public List<RouteDefinition> definitions() {
        return List.copyOf(definitions.values());
    }

    public DataResult<RoutePlan> plan(RouteAnchor source, RouteAnchor destination) {
        PlanKey key = new PlanKey(source, destination);
        synchronized (planCache) {
            RoutePlan cached = planCache.get(key);
            if (cached != null) {
                return DataResult.success(cached);
            }
        }

        DataResult<RoutePlan> result = RoutePlanner.plan(this, source, destination);
        result.result().ifPresent(plan -> {
            synchronized (planCache) {
                planCache.put(key, plan);
            }
        });
        return result;
    }

    public int cachedPlanCount() {
        synchronized (planCache) {
            return planCache.size();
        }
    }

    boolean containsAnchor(RouteAnchor anchor) {
        return anchors.contains(anchor);
    }

    List<RouteEdge> outgoing(RouteAnchor anchor) {
        return outgoing.getOrDefault(anchor, List.of());
    }

    private static DataResult<Boolean> addEdge(
            RouteDefinition definition,
            RouteAnchor from,
            RouteAnchor to,
            Map<EdgeKey, ResourceLocation> directedEdges,
            Map<RouteAnchor, List<RouteEdge>> outgoing
    ) {
        EdgeKey key = new EdgeKey(from, to);
        ResourceLocation previous = directedEdges.putIfAbsent(key, definition.id());
        if (previous != null) {
            return DataResult.error(() -> "Duplicate directed route edge " + from + " -> " + to
                    + " from " + previous + " and " + definition.id());
        }
        outgoing.computeIfAbsent(from, ignored -> new ArrayList<>()).add(new RouteEdge(
                definition.id(),
                from,
                to,
                definition.distanceUnits()
        ));
        return DataResult.success(Boolean.TRUE);
    }

    record RouteEdge(
            ResourceLocation routeId,
            RouteAnchor from,
            RouteAnchor to,
            int distanceUnits
    ) {
    }

    private record EdgeKey(RouteAnchor from, RouteAnchor to) {
    }

    private record PlanKey(RouteAnchor source, RouteAnchor destination) {
        private PlanKey {
            Objects.requireNonNull(source, "source");
            Objects.requireNonNull(destination, "destination");
        }
    }
}
