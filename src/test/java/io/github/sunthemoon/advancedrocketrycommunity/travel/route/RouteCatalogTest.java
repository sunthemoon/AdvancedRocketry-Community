package io.github.sunthemoon.advancedrocketrycommunity.travel.route;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import io.github.sunthemoon.advancedrocketrycommunity.travel.route.model.RouteAnchor;
import io.github.sunthemoon.advancedrocketrycommunity.travel.route.model.RouteDefinition;
import io.github.sunthemoon.advancedrocketrycommunity.travel.route.model.RouteLimits;
import io.github.sunthemoon.advancedrocketrycommunity.travel.route.model.RoutePlan;
import io.github.sunthemoon.advancedrocketrycommunity.travel.route.service.RouteCatalog;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

class RouteCatalogTest {
    @Test
    void shortestPathWinsAndCyclesRemainBounded() {
        RouteAnchor earth = surface("earth");
        RouteAnchor moon = surface("moon");
        RouteAnchor orbit = RouteAnchor.orbit(ModIdentity.id("earth"));
        RouteCatalog catalog = catalog(List.of(
                route("earth_moon", earth, moon, 50, true),
                route("earth_orbit", earth, orbit, 25, true),
                route("moon_orbit", moon, orbit, 80, true)
        ), bodies("earth", "moon"));

        RoutePlan plan = catalog.plan(moon, orbit).getOrThrow(false, message -> {
            throw new AssertionError(message);
        });

        assertEquals(75L, plan.totalDistanceUnits());
        assertEquals(List.of(ModIdentity.id("earth_moon"), ModIdentity.id("earth_orbit")),
                plan.legs().stream().map(leg -> leg.routeId()).toList());
        assertTrue(plan.expandedNodes() <= RouteLimits.MAX_EXPANDED_NODES);
    }

    @Test
    void equalCostPathsUseLexicographicRouteIdOrder() {
        RouteAnchor start = surface("start");
        RouteAnchor alpha = surface("alpha");
        RouteAnchor beta = surface("beta");
        RouteAnchor end = surface("end");
        RouteCatalog catalog = catalog(List.of(
                route("a_start", start, alpha, 5, false),
                route("z_finish", alpha, end, 5, false),
                route("b_start", start, beta, 5, false),
                route("a_finish", beta, end, 5, false)
        ), bodies("start", "alpha", "beta", "end"));

        RoutePlan plan = catalog.plan(start, end).getOrThrow(false, message -> {
            throw new AssertionError(message);
        });

        assertEquals(List.of(ModIdentity.id("a_start"), ModIdentity.id("z_finish")),
                plan.legs().stream().map(leg -> leg.routeId()).toList());
    }

    @Test
    void successfulPlansAreCachedWithoutExceedingTheBound() {
        List<RouteDefinition> routes = new ArrayList<>();
        Set<ResourceLocation> bodies = new LinkedHashSet<>();
        List<RouteAnchor> anchors = new ArrayList<>();
        for (int index = 0; index < 24; index++) {
            RouteAnchor anchor = surface("cache_" + index);
            anchors.add(anchor);
            bodies.add(anchor.bodyId());
            if (index > 0) {
                routes.add(route("cache_edge_" + index, anchors.get(index - 1), anchor, 1, true));
            }
        }
        RouteCatalog catalog = catalog(routes, bodies);

        int planned = 0;
        for (RouteAnchor source : anchors) {
            for (RouteAnchor destination : anchors) {
                if (!source.equals(destination)) {
                    catalog.plan(source, destination).getOrThrow(false, message -> {
                        throw new AssertionError(message);
                    });
                    planned++;
                    if (planned == 300) {
                        break;
                    }
                }
            }
            if (planned == 300) {
                break;
            }
        }

        assertEquals(300, planned);
        assertEquals(RouteLimits.MAX_CACHED_PLANS, catalog.cachedPlanCount());
        RoutePlan first = catalog.plan(anchors.get(0), anchors.get(1)).getOrThrow(false, message -> {
            throw new AssertionError(message);
        });
        RoutePlan second = catalog.plan(anchors.get(0), anchors.get(1)).getOrThrow(false, message -> {
            throw new AssertionError(message);
        });
        assertSame(first, second);
    }

    @Test
    void duplicateIdsMissingBodiesEqualEndpointsAndDirectedEdgesAreRejected() {
        RouteAnchor earth = surface("earth");
        RouteAnchor moon = surface("moon");
        RouteDefinition route = route("one", earth, moon, 1, false);

        assertTrue(RouteCatalog.create(List.of(route, route), bodies("earth", "moon")).error().isPresent());
        assertTrue(RouteCatalog.create(List.of(route), bodies("earth")).error().isPresent());
        assertTrue(RouteCatalog.create(
                List.of(route("same", earth, earth, 1, false)),
                bodies("earth")
        ).error().isPresent());
        assertTrue(RouteCatalog.create(List.of(
                route,
                route("two", earth, moon, 2, false)
        ), bodies("earth", "moon")).error().isPresent());
    }

    @Test
    void routeAnchorAndOutgoingEdgeBudgetsAreEnforced() {
        List<RouteDefinition> star = new ArrayList<>();
        Set<ResourceLocation> starBodies = new LinkedHashSet<>();
        RouteAnchor center = surface("center");
        starBodies.add(center.bodyId());
        for (int index = 0; index <= RouteLimits.MAX_OUTGOING_EDGES; index++) {
            RouteAnchor leaf = surface("leaf_" + index);
            starBodies.add(leaf.bodyId());
            star.add(route("star_" + index, center, leaf, 1, false));
        }
        assertTrue(RouteCatalog.create(star, starBodies).error().orElseThrow().message()
                .contains("outgoing edges"));

        List<RouteDefinition> disjoint = new ArrayList<>();
        Set<ResourceLocation> disjointBodies = new LinkedHashSet<>();
        for (int index = 0; index <= RouteLimits.MAX_ANCHORS / 2; index++) {
            RouteAnchor from = surface("from_" + index);
            RouteAnchor to = surface("to_" + index);
            disjointBodies.add(from.bodyId());
            disjointBodies.add(to.bodyId());
            disjoint.add(route("disjoint_" + index, from, to, 1, false));
        }
        assertTrue(RouteCatalog.create(disjoint, disjointBodies).error().orElseThrow().message()
                .contains("anchors"));
    }

    @Test
    void routeCountAndUnknownPlanEndpointsFailClosed() {
        RouteAnchor earth = surface("earth");
        RouteAnchor moon = surface("moon");
        RouteDefinition route = route("one", earth, moon, 1, false);
        List<RouteDefinition> tooMany = new ArrayList<>();
        for (int index = 0; index <= RouteLimits.MAX_ROUTES; index++) {
            tooMany.add(route);
        }
        assertTrue(RouteCatalog.create(tooMany, bodies("earth", "moon")).error().isPresent());

        RouteCatalog catalog = catalog(List.of(route), bodies("earth", "moon"));
        assertTrue(catalog.plan(moon, earth).error().isPresent());
        assertTrue(catalog.plan(earth, earth).error().isPresent());
        assertTrue(catalog.plan(earth, surface("mars")).error().isPresent());
    }

    private static RouteCatalog catalog(
            List<RouteDefinition> routes,
            Set<ResourceLocation> bodies
    ) {
        return RouteCatalog.create(routes, bodies).getOrThrow(false, message -> {
            throw new AssertionError(message);
        });
    }

    private static RouteDefinition route(
            String id,
            RouteAnchor from,
            RouteAnchor to,
            int distance,
            boolean bidirectional
    ) {
        return new RouteDefinition(
                RouteLimits.SCHEMA_VERSION,
                ModIdentity.id(id),
                from,
                to,
                distance,
                bidirectional
        );
    }

    private static RouteAnchor surface(String body) {
        return RouteAnchor.bodySurface(ModIdentity.id(body));
    }

    private static Set<ResourceLocation> bodies(String... ids) {
        Set<ResourceLocation> bodies = new LinkedHashSet<>();
        for (String id : ids) {
            bodies.add(ModIdentity.id(id));
        }
        return bodies;
    }
}
