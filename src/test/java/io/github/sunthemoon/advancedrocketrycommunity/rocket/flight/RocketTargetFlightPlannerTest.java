package io.github.sunthemoon.advancedrocketrycommunity.rocket.flight;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.CelestialDefaults;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.CelestialIds;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.model.CelestialBodyDefinition;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.service.CelestialCatalog;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.stats.RocketStats;
import io.github.sunthemoon.advancedrocketrycommunity.station.model.StationGridCell;
import io.github.sunthemoon.advancedrocketrycommunity.station.model.StationReservation;
import io.github.sunthemoon.advancedrocketrycommunity.station.model.StationState;
import io.github.sunthemoon.advancedrocketrycommunity.travel.model.TravelTarget;
import io.github.sunthemoon.advancedrocketrycommunity.travel.route.model.RouteAnchor;
import io.github.sunthemoon.advancedrocketrycommunity.travel.route.model.RouteDefinition;
import io.github.sunthemoon.advancedrocketrycommunity.travel.route.service.RouteCatalog;
import io.github.sunthemoon.advancedrocketrycommunity.travel.route.service.RouteCatalogDecoder;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeAll;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;

class RocketTargetFlightPlannerTest {
    private static final UUID REQUEST = UUID.fromString("123e4567-e89b-42d3-a456-426614174710");
    private static final UUID STATION_ID = UUID.fromString("123e4567-e89b-42d3-a456-426614174711");

    @BeforeAll
    static void bootstrapMinecraftRegistries() {
        MinecraftBootstrap.initialize();
    }

    @Test
    void dataRoutesPreserveEarthMoonFuelAndResolveStationToItsOrbitBody() {
        CelestialCatalog celestial = CelestialCatalog.create(CelestialDefaults.definitions())
                .getOrThrow(false, message -> {
                    throw new AssertionError(message);
                });
        RouteCatalog routes = RouteCatalog.create(List.of(
                route("earth_moon", surface(CelestialIds.EARTH_ID), surface(CelestialIds.MOON_ID), 50),
                route("earth_orbit", surface(CelestialIds.EARTH_ID), orbit(CelestialIds.EARTH_ID), 25),
                route("moon_earth_orbit", surface(CelestialIds.MOON_ID), orbit(CelestialIds.EARTH_ID), 25),
                route("moon_orbit", surface(CelestialIds.MOON_ID), orbit(CelestialIds.MOON_ID), 25)
        ), List.of(CelestialIds.EARTH_ID, CelestialIds.MOON_ID, CelestialIds.SPACE_ID))
                .getOrThrow(false, message -> {
                    throw new AssertionError(message);
                });
        RocketStats stats = new RocketStats(4, 200L, 1_000L, 1_000L, 1, 1, 1, 0);
        RocketFuelState fuel = RocketFuelState.empty(1_000L).fill(1_000L).state();

        RocketFlightPlanResult moon = RocketTargetFlightPlanner.plan(
                stats,
                fuel,
                new TravelTarget.BodySurface(CelestialIds.EARTH_ID),
                net.minecraft.world.level.Level.OVERWORLD.location(),
                new TravelTarget.BodySurface(CelestialIds.MOON_ID),
                celestial,
                routes,
                ignored -> Optional.empty(),
                REQUEST,
                0L
        );
        assertTrue(moon.success());
        assertEquals(367L, moon.requiredFuel());

        StationState moonStation = StationState.fromReservation(new StationReservation(
                STATION_ID,
                UUID.fromString("123e4567-e89b-42d3-a456-426614174712"),
                "Moon Orbit",
                new StationGridCell(2, 3),
                CelestialIds.MOON_ID,
                0L
        ));
        TravelTarget stationTarget = new TravelTarget.Station(STATION_ID);
        RocketFlightPlanResult station = RocketTargetFlightPlanner.plan(
                stats,
                fuel,
                new TravelTarget.BodySurface(CelestialIds.EARTH_ID),
                net.minecraft.world.level.Level.OVERWORLD.location(),
                stationTarget,
                celestial,
                routes,
                id -> id.equals(STATION_ID) ? Optional.of(moonStation) : Optional.empty(),
                REQUEST,
                0L
        );
        assertTrue(station.success());
        assertEquals(375L, station.requiredFuel());
        assertEquals(CelestialIds.MOON_ID, station.plan().destinationBody());
        assertEquals(CelestialIds.SPACE_LEVEL.location(), station.plan().destinationDimension());
        assertEquals(stationTarget, station.plan().destinationTarget());
    }

    @Test
    void unknownInstancesMissionsAndDimensionSpoofingFailClosed() {
        CelestialCatalog celestial = CelestialCatalog.create(CelestialDefaults.definitions())
                .getOrThrow(false, message -> {
                    throw new AssertionError(message);
                });
        RouteCatalog routes = RouteCatalog.create(List.of(
                route("earth_moon", surface(CelestialIds.EARTH_ID), surface(CelestialIds.MOON_ID), 50)
        ), List.of(CelestialIds.EARTH_ID, CelestialIds.MOON_ID, CelestialIds.SPACE_ID))
                .getOrThrow(false, message -> {
                    throw new AssertionError(message);
                });
        RocketStats stats = new RocketStats(4, 200L, 1_000L, 1_000L, 1, 1, 1, 0);
        RocketFuelState fuel = RocketFuelState.empty(1_000L).fill(1_000L).state();
        TravelTarget source = new TravelTarget.BodySurface(CelestialIds.EARTH_ID);

        for (TravelTarget rejected : List.of(
                new TravelTarget.Station(STATION_ID),
                new TravelTarget.Mission(STATION_ID)
        )) {
            assertEquals(RocketFlightPlanCode.UNSUPPORTED_ROUTE, RocketTargetFlightPlanner.plan(
                    stats, fuel, source, net.minecraft.world.level.Level.OVERWORLD.location(), rejected,
                    celestial, routes, ignored -> Optional.empty(), REQUEST, 0L
            ).code());
        }
        assertEquals(RocketFlightPlanCode.UNSUPPORTED_ROUTE, RocketTargetFlightPlanner.plan(
                stats, fuel, source, CelestialIds.MOON_LEVEL.location(),
                new TravelTarget.BodySurface(CelestialIds.MOON_ID), celestial, routes,
                ignored -> Optional.empty(), REQUEST, 0L
        ).code());
    }

    @Test
    void testMarsDataDrivesTheTypedPlannerWithoutAProductionEnumBranch() throws IOException {
        // This test-only Level key proves data-driven planning, not physical world admission.
        JsonElement marsJson = resource(
                "data/advancedrocketrycommunity/test_celestial_bodies/test_mars.json"
        );
        CelestialBodyDefinition mars = CelestialBodyDefinition.CODEC.parse(JsonOps.INSTANCE, marsJson)
                .getOrThrow(false, message -> {
                    throw new AssertionError(message);
                });
        ArrayList<CelestialBodyDefinition> definitions = new ArrayList<>(CelestialDefaults.definitions());
        definitions.add(mars);
        CelestialCatalog celestial = CelestialCatalog.create(definitions)
                .getOrThrow(false, message -> {
                    throw new AssertionError(message);
                });

        Map<net.minecraft.resources.ResourceLocation, JsonElement> routeResources = new LinkedHashMap<>();
        routeResources.put(
                ModIdentity.id("test_earth_mars"),
                resource("data/advancedrocketrycommunity/test_travel_routes/test_earth_mars.json")
        );
        RouteCatalog routes = RouteCatalogDecoder.decode(
                routeResources,
                celestial.definitions().stream().map(CelestialBodyDefinition::id).toList()
        ).getOrThrow(false, message -> {
            throw new AssertionError(message);
        });

        RocketStats stats = new RocketStats(4, 200L, 2_000L, 2_000L, 1, 1, 1, 0);
        RocketFlightPlanResult result = RocketTargetFlightPlanner.plan(
                stats,
                RocketFuelState.empty(2_000L).fill(2_000L).state(),
                new TravelTarget.BodySurface(CelestialIds.EARTH_ID),
                net.minecraft.world.level.Level.OVERWORLD.location(),
                new TravelTarget.BodySurface(mars.id()),
                celestial,
                routes,
                ignored -> Optional.empty(),
                REQUEST,
                0L
        );

        assertTrue(result.success());
        assertEquals(mars.id(), result.plan().destinationBody());
        assertEquals(new TravelTarget.BodySurface(mars.id()), result.plan().destinationTarget());
    }

    private static RouteDefinition route(
            String id,
            RouteAnchor from,
            RouteAnchor to,
            int distance
    ) {
        return new RouteDefinition(1, ModIdentity.id(id), from, to, distance, true);
    }

    private static RouteAnchor surface(net.minecraft.resources.ResourceLocation body) {
        return RouteAnchor.bodySurface(body);
    }

    private static RouteAnchor orbit(net.minecraft.resources.ResourceLocation body) {
        return RouteAnchor.orbit(body);
    }

    private static JsonElement resource(String path) throws IOException {
        try (InputStream stream = RocketTargetFlightPlannerTest.class.getClassLoader()
                .getResourceAsStream(path)) {
            if (stream == null) {
                throw new IOException("Missing test resource " + path);
            }
            return JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8));
        }
    }
}
