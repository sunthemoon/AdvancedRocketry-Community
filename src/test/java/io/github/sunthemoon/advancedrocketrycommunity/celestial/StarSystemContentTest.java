package io.github.sunthemoon.advancedrocketrycommunity.celestial;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.sunthemoon.advancedrocketrycommunity.celestial.content.PlanetaryContent;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.content.StarSystemContent;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.data.PlanetaryCatalog;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.model.CelestialBodyDefinition;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.service.CelestialCatalog;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.service.StarSystemKnowledge;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import io.github.sunthemoon.advancedrocketrycommunity.travel.route.model.RouteAnchor;
import io.github.sunthemoon.advancedrocketrycommunity.travel.route.model.RouteDefinition;
import io.github.sunthemoon.advancedrocketrycommunity.travel.route.service.RouteCatalog;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

final class StarSystemContentTest {
    @BeforeAll
    static void bootstrap() {
        MinecraftBootstrap.initialize();
    }

    @Test
    void packagedContentFormsTwoSystemsWithOnlyInSystemRoutes() {
        CelestialCatalog catalog = catalog();
        assertEquals(List.of(CelestialIds.EARTH_ID, StarSystemContent.TAU_CETI), catalog.systems());
        for (CelestialBodyDefinition body : catalog.definitions()) {
            boolean example = body.id().equals(StarSystemContent.TAU_CETI) || body.id().equals(StarSystemContent.TAU_CETI_E);
            assertEquals(example ? StarSystemContent.TAU_CETI : CelestialIds.EARTH_ID,
                    catalog.systemOf(body.id()).orElseThrow(), body.id().toString());
        }
        var ids = catalog.definitions().stream().map(CelestialBodyDefinition::id).toList();
        List<RouteDefinition> routes = new ArrayList<>(PlanetaryContent.routes());
        assertTrue(PlanetaryCatalog.create(catalog, RouteCatalog.create(routes, ids).getOrThrow(false, m -> { }))
                .result().isPresent(), "Packaged routes must stay within one system");
        routes.add(new RouteDefinition(1, io.github.sunthemoon.advancedrocketrycommunity.ModIdentity.id("interstellar"),
                RouteAnchor.orbit(CelestialIds.EARTH_ID), RouteAnchor.orbit(StarSystemContent.TAU_CETI_E), 10, true));
        var rejected = PlanetaryCatalog.create(catalog, RouteCatalog.create(routes, ids).getOrThrow(false, m -> { }));
        assertTrue(rejected.error().isPresent() && rejected.error().get().message().contains("connects systems"));
    }

    @Test
    void exampleValuesStayInsideSchemaBoundsAndTheStarIsPublic() {
        CelestialBodyDefinition star = StarSystemContent.definitions().get(0);
        CelestialBodyDefinition planet = StarSystemContent.definitions().get(1);
        assertTrue(star.isRoot() && star.levelKey().isEmpty() && !star.discoveryRequired()
                && !star.capabilities().orbitable() && !star.capabilities().landable());
        assertTrue(planet.parentId().filter(StarSystemContent.TAU_CETI::equals).isPresent()
                && planet.capabilities().orbitable() && !planet.capabilities().landable()
                && planet.levelKey().isEmpty() && planet.discoveryRequired());
        var survey = StarSystemContent.surveySatellite();
        assertTrue(survey.allowedTargets().contains(StarSystemContent.TAU_CETI_E));
        assertTrue(survey.allowedTargets().containsAll(PlanetaryContent.surveySatellite().allowedTargets()));
        assertFalse(survey.allowedTargets().contains(StarSystemContent.TAU_CETI), "The star is not a satellite target");
    }

    @Test
    void systemKnowledgeFollowsBodyDiscovery() {
        CelestialCatalog catalog = catalog();
        // The public star makes its system known; its planet still needs discovery to be a warp target.
        assertTrue(StarSystemKnowledge.systemKnown(catalog, StarSystemContent.TAU_CETI, body -> false));
        assertFalse(StarSystemKnowledge.bodyKnown(catalog, StarSystemContent.TAU_CETI_E, body -> false));
        assertTrue(StarSystemKnowledge.bodyKnown(catalog, StarSystemContent.TAU_CETI_E,
                Set.of(StarSystemContent.TAU_CETI_E)::contains));
        assertTrue(StarSystemKnowledge.systemKnown(catalog, CelestialIds.EARTH_ID, body -> false));
        assertFalse(StarSystemKnowledge.bodyKnown(catalog, io.github.sunthemoon.advancedrocketrycommunity.ModIdentity
                .id("missing"), body -> true));
    }

    private static CelestialCatalog catalog() {
        List<CelestialBodyDefinition> bodies = new ArrayList<>(CelestialDefaults.definitions());
        bodies.addAll(PlanetaryContent.definitions());
        bodies.addAll(StarSystemContent.definitions());
        return CelestialCatalog.create(bodies).flatMap(CelestialCatalog::requireFixedBaseline)
                .getOrThrow(false, message -> {
                    throw new AssertionError(message);
                });
    }
}
