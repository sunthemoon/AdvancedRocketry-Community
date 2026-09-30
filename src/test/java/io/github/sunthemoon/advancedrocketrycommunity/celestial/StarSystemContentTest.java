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

    /** Review F6: the packaged route graph gives the rocket planner no way into the example system. */
    @Test
    void thePackagedPlannerCannotReachTheExampleSystem() {
        CelestialCatalog catalog = catalog();
        var ids = catalog.definitions().stream().map(CelestialBodyDefinition::id).toList();
        RouteCatalog routes = RouteCatalog.create(PlanetaryContent.routes(), ids).getOrThrow(false, m -> { });
        var stats = new io.github.sunthemoon.advancedrocketrycommunity.rocket.stats.RocketStats(
                4, 200L, 1_000L, 1_000L, 1, 1, 1, 0);
        var fuel = io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFuelState.empty(1_000L)
                .fill(1_000L).state();
        var earth = new io.github.sunthemoon.advancedrocketrycommunity.travel.model.TravelTarget.BodySurface(
                CelestialIds.EARTH_ID);
        java.util.UUID stationId = java.util.UUID.randomUUID();
        var tauStation = io.github.sunthemoon.advancedrocketrycommunity.station.model.StationState.fromReservation(
                new io.github.sunthemoon.advancedrocketrycommunity.station.model.StationReservation(stationId,
                        java.util.UUID.randomUUID(), "Tau orbit",
                        new io.github.sunthemoon.advancedrocketrycommunity.station.model.StationGridCell(1, 1),
                        StarSystemContent.TAU_CETI_E, 0L));
        java.util.function.Function<java.util.UUID, java.util.Optional<
                io.github.sunthemoon.advancedrocketrycommunity.station.model.StationState>> stations =
                id -> id.equals(stationId) ? java.util.Optional.of(tauStation) : java.util.Optional.empty();
        var overworld = net.minecraft.world.level.Level.OVERWORLD.location();
        var mars = io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketTargetFlightPlanner.plan(
                stats, fuel, earth, overworld,
                new io.github.sunthemoon.advancedrocketrycommunity.travel.model.TravelTarget.BodySurface(
                        PlanetaryContent.MARS),
                catalog, routes, stations, java.util.UUID.randomUUID(), 0L);
        assertTrue(mars.success(), "Control: the home system is routable: " + mars.code());
        for (var target : List.<io.github.sunthemoon.advancedrocketrycommunity.travel.model.TravelTarget>of(
                new io.github.sunthemoon.advancedrocketrycommunity.travel.model.TravelTarget.Orbit(
                        StarSystemContent.TAU_CETI_E),
                new io.github.sunthemoon.advancedrocketrycommunity.travel.model.TravelTarget.Station(stationId))) {
            var plan = io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketTargetFlightPlanner.plan(
                    stats, fuel, earth, overworld, target, catalog, routes, stations, java.util.UUID.randomUUID(), 0L);
            assertEquals(io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFlightPlanCode
                    .UNSUPPORTED_ROUTE, plan.code(), target.toString());
        }
    }

    /** Review F6: the v1.5 data satellite surveys Tau Ceti e through the ordinary mission flow. */
    @Test
    void aSurveyMissionToTheExamplePlanetEndsInAPendingDiscovery() {
        var missions = io.github.sunthemoon.advancedrocketrycommunity.satellite.persistence.SatelliteMissionSavedData
                .create(0);
        java.util.UUID owner = java.util.UUID.randomUUID();
        java.util.UUID mission = java.util.UUID.randomUUID();
        var survey = StarSystemContent.surveySatellite();
        assertTrue(missions.launch(java.util.UUID.randomUUID(), mission, owner, survey, StarSystemContent.TAU_CETI_E,
                0, true).success());
        assertEquals(1, missions.completeDue(survey.missionDurationTicks()).completed());
        assertEquals(io.github.sunthemoon.advancedrocketrycommunity.satellite.mission.SatelliteOperationCode
                .PENDING_DISCOVERY, missions.claim(mission, owner, survey.missionDurationTicks() + 1).code());
        assertTrue(missions.finishDiscovery(mission).success());
        assertFalse(missions.launch(java.util.UUID.randomUUID(), java.util.UUID.randomUUID(), owner, survey,
                StarSystemContent.TAU_CETI, 0, true).success(), "The star is not a satellite target");
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
