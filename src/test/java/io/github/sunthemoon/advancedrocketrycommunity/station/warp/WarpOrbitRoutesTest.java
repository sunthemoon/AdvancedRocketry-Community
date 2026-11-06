package io.github.sunthemoon.advancedrocketrycommunity.station.warp;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.CelestialDefaults;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.CelestialIds;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.data.PlanetaryCatalog;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.data.PlanetaryCatalogManager;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.service.CelestialCatalog;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import io.github.sunthemoon.advancedrocketrycommunity.travel.route.model.RouteAnchor;
import io.github.sunthemoon.advancedrocketrycommunity.travel.route.model.RouteDefinition;
import io.github.sunthemoon.advancedrocketrycommunity.travel.route.service.RouteCatalog;
import java.util.List;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** A station resolves to orbit(body), never to a surface or to any other body in the same system. */
final class WarpOrbitRoutesTest {
    private static final ResourceLocation EARTH = ModIdentity.id("earth");
    private static final ResourceLocation MOON = ModIdentity.id("moon");

    @BeforeAll
    static void bootstrap() {
        MinecraftBootstrap.initialize();
    }

    @Test
    void anIncomingOneWayRouteCountsOnlyForItsExactOrbit() {
        var planetary = new PlanetaryCatalogManager();
        apply(planetary, RouteAnchor.bodySurface(EARTH), RouteAnchor.orbit(MOON));
        var routes = StationWarpService.routesForOrbit(planetary);
        assertTrue(routes.test(MOON), "An orbit at a route's destination counts, even without a reverse edge");
        assertFalse(routes.test(EARTH), "A surface endpoint in the same system does not connect Earth's orbit");
        assertFalse(routes.test(CelestialIds.SPACE_ID), "A different orbit in the same system has no route");
        assertFalse(routes.test(ModIdentity.id("missing_body")));
    }

    @Test
    void aSurfaceOnlyRouteDoesNotConnectEitherOrbit() {
        var planetary = new PlanetaryCatalogManager();
        apply(planetary, RouteAnchor.bodySurface(EARTH), RouteAnchor.bodySurface(MOON));
        var routes = StationWarpService.routesForOrbit(planetary);
        assertFalse(routes.test(EARTH));
        assertFalse(routes.test(MOON));
    }

    @Test
    void anOutgoingRouteCountsAndThePredicateUsesTheCurrentReload() {
        var planetary = new PlanetaryCatalogManager();
        var routes = StationWarpService.routesForOrbit(planetary);
        assertFalse(routes.test(EARTH), "An unavailable catalog must not claim a rocket connection");
        apply(planetary, RouteAnchor.orbit(EARTH), RouteAnchor.bodySurface(MOON));
        assertTrue(routes.test(EARTH), "An orbit at a route's source counts");
        assertFalse(routes.test(MOON));
        apply(planetary, RouteAnchor.bodySurface(EARTH), RouteAnchor.orbit(MOON));
        assertFalse(routes.test(EARTH), "A removed orbit endpoint must not remain cached");
        assertTrue(routes.test(MOON), "An added orbit endpoint must be visible after reload");
        planetary.clear();
        assertFalse(routes.test(MOON), "A stopped catalog must not retain a previous connection");
    }

    private static void apply(PlanetaryCatalogManager planetary, RouteAnchor from, RouteAnchor to) {
        var bodies = CelestialDefaults.definitions();
        var celestial = CelestialCatalog.create(bodies).result().orElseThrow();
        var routes = RouteCatalog.create(List.of(new RouteDefinition(1, ModIdentity.id("test_orbit_route"),
                        from, to, 1, false)), bodies.stream().map(body -> body.id()).toList()).result().orElseThrow();
        assertTrue(planetary.applyCandidate(PlanetaryCatalog.create(celestial, routes)));
    }
}
