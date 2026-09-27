package io.github.sunthemoon.advancedrocketrycommunity.rocket.flight;

import static org.junit.jupiter.api.Assertions.*;

import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.CelestialDefaults;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.CelestialIds;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.model.CelestialBodyDefinition;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.model.CelestialCapabilities;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.service.CelestialCatalog;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.stats.RocketStats;
import io.github.sunthemoon.advancedrocketrycommunity.station.model.StationGridCell;
import io.github.sunthemoon.advancedrocketrycommunity.station.model.StationReservation;
import io.github.sunthemoon.advancedrocketrycommunity.station.model.StationState;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import io.github.sunthemoon.advancedrocketrycommunity.travel.model.TravelTarget;
import io.github.sunthemoon.advancedrocketrycommunity.travel.route.model.RouteAnchor;
import io.github.sunthemoon.advancedrocketrycommunity.travel.route.model.RouteDefinition;
import io.github.sunthemoon.advancedrocketrycommunity.travel.route.service.RouteCatalog;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class PlanetaryFlightAdmissionTest {
    private static final UUID STATION = UUID.fromString("123e4567-e89b-42d3-a456-426614174721");
    private static final TravelTarget EARTH = new TravelTarget.BodySurface(CelestialIds.EARTH_ID);
    private static final TravelTarget MOON = new TravelTarget.BodySurface(CelestialIds.MOON_ID);

    @BeforeAll
    static void bootstrap() {
        MinecraftBootstrap.initialize();
    }

    @Test
    void closedMappedSurfaceRetainsAnExitButRejectsNewEntry() {
        var closedMoon = moon(new CelestialCapabilities(false, true, false), true);
        assertEquals(RocketFlightPlanCode.UNSUPPORTED_ROUTE, plan(closedMoon, EARTH,
                Level.OVERWORLD.location(), MOON).code());
        assertTrue(plan(closedMoon, MOON, CelestialIds.MOON_LEVEL.location(), EARTH).success());
        assertEquals(RocketFlightPlanCode.UNSUPPORTED_ROUTE, plan(closedMoon, MOON,
                Level.OVERWORLD.location(), EARTH).code());
    }

    @Test
    void unmappedAndGasBodiesCannotBeSurfaceSourcesOrDestinations() {
        for (var body : List.of(moon(new CelestialCapabilities(false, true, false), false),
                moon(new CelestialCapabilities(false, true, true), false))) {
            assertEquals(RocketFlightPlanCode.UNSUPPORTED_ROUTE, plan(body, EARTH,
                    Level.OVERWORLD.location(), MOON).code());
            assertEquals(RocketFlightPlanCode.UNSUPPORTED_ROUTE, plan(body, MOON,
                    CelestialIds.MOON_LEVEL.location(), EARTH).code());
        }
    }

    @Test
    void closedOrbitPreservesCommittedStationDepartureButRejectsNewArrival() {
        var closedMoon = moon(new CelestialCapabilities(true, false, false), true);
        var station = new TravelTarget.Station(STATION);
        assertEquals(RocketFlightPlanCode.UNSUPPORTED_ROUTE, plan(closedMoon, EARTH,
                Level.OVERWORLD.location(), station).code());
        assertEquals(RocketFlightPlanCode.UNSUPPORTED_ROUTE, plan(closedMoon, EARTH,
                Level.OVERWORLD.location(), new TravelTarget.Orbit(CelestialIds.MOON_ID)).code());
        assertTrue(plan(closedMoon, station, CelestialIds.SPACE_LEVEL.location(), EARTH).success());
    }

    @Test
    void sharedSpaceRemainsAHostEvenIfADataDefinitionClaimsItIsLandable() {
        var moon = moon(new CelestialCapabilities(true, true, false), true);
        var sharedHost = new CelestialBodyDefinition(moon.id(), moon.parentId(), Optional.of(CelestialIds.SPACE_LEVEL),
                moon.gravityMultiplier(), moon.atmosphere(), moon.orbit(), moon.visualProfile(), moon.capabilities(), 1, 0);
        assertFalse(sharedHost.supportsSurfaceArrival());
        assertEquals(RocketFlightPlanCode.UNSUPPORTED_ROUTE, plan(sharedHost, EARTH,
                Level.OVERWORLD.location(), MOON).code());
        assertEquals(RocketFlightPlanCode.UNSUPPORTED_ROUTE, plan(sharedHost, MOON,
                CelestialIds.SPACE_LEVEL.location(), EARTH).code());
    }

    @Test
    void gasGiantOrbitMayBeAStationSubjectWithoutGrantingASurface() {
        var gas = moon(new CelestialCapabilities(false, true, true), false);
        var station = new TravelTarget.Station(STATION);
        assertTrue(plan(gas, EARTH, Level.OVERWORLD.location(), station).success());
        assertTrue(plan(gas, station, CelestialIds.SPACE_LEVEL.location(), EARTH).success());
        assertEquals(RocketFlightPlanCode.UNSUPPORTED_ROUTE, plan(gas, EARTH,
                Level.OVERWORLD.location(), MOON).code());
    }

    private static CelestialBodyDefinition moon(CelestialCapabilities flags, boolean mapped) {
        var body = CelestialDefaults.definitions().get(1);
        return new CelestialBodyDefinition(body.id(), body.parentId(), mapped ? body.levelKey() : Optional.empty(),
                body.gravityMultiplier(), body.atmosphere(), body.orbit(), body.visualProfile(), flags, 1, 0);
    }

    private static RocketFlightPlanResult plan(CelestialBodyDefinition moon, TravelTarget source,
            ResourceLocation sourceDimension, TravelTarget destination) {
        var catalog = CelestialCatalog.create(List.of(CelestialDefaults.definitions().get(0), moon)).result().orElseThrow();
        var routes = RouteCatalog.create(List.of(
                new RouteDefinition(1, ModIdentity.id("test_surface"), RouteAnchor.bodySurface(CelestialIds.EARTH_ID),
                        RouteAnchor.bodySurface(CelestialIds.MOON_ID), 50, true),
                new RouteDefinition(1, ModIdentity.id("test_orbit"), RouteAnchor.bodySurface(CelestialIds.EARTH_ID),
                        RouteAnchor.orbit(CelestialIds.MOON_ID), 25, true)),
                catalog.definitions().stream().map(CelestialBodyDefinition::id).toList()).result().orElseThrow();
        var station = StationState.fromReservation(new StationReservation(STATION, UUID.randomUUID(),
                "Orbit", new StationGridCell(2, 3), CelestialIds.MOON_ID, 0));
        var fuel = RocketFuelState.empty(2000).fill(2000).state();
        var result = RocketTargetFlightPlanner.plan(new RocketStats(4, 200, 2000, 2000, 1, 1, 1, 0),
                fuel, source, sourceDimension, destination, catalog, routes,
                id -> id.equals(STATION) ? Optional.of(station) : Optional.empty(), UUID.randomUUID(), 0);
        assertEquals(2000, fuel.amount());
        if (!result.success()) {
            assertNull(result.plan());
        }
        return result;
    }
}
