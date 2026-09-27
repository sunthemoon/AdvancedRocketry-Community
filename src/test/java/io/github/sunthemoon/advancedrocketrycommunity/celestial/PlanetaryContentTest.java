package io.github.sunthemoon.advancedrocketrycommunity.celestial;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.binding.PlanetaryBindings;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.content.PlanetaryContent;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.data.PlanetaryCatalog;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.model.CelestialBodyDefinition;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.model.CelestialCapabilities;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.service.CelestialCatalog;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.service.PlanetarySurfaceResolver;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import io.github.sunthemoon.advancedrocketrycommunity.travel.route.model.RouteAnchor;
import io.github.sunthemoon.advancedrocketrycommunity.travel.route.model.RouteDefinition;
import io.github.sunthemoon.advancedrocketrycommunity.travel.route.service.RouteCatalog;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class PlanetaryContentTest {
    @BeforeAll static void bootstrap() { MinecraftBootstrap.initialize(); }

    @Test void generatedBodiesDecodeToExactAuthoringInputs() throws Exception {
        for (var body : PlanetaryContent.definitions()) {
            try (var input = getClass().getResourceAsStream("/data/advancedrocketrycommunity/celestial_bodies/"
                    + body.id().getPath() + ".json")) {
                assertNotNull(input);
                var json = JsonParser.parseReader(new InputStreamReader(input, StandardCharsets.UTF_8));
                assertEquals(2, json.getAsJsonObject().get("schema_version").getAsInt());
                assertEquals(body, CelestialBodyDefinition.CODEC.parse(JsonOps.INSTANCE, json).result().orElseThrow());
            }
        }
    }

    @Test void routesAreBidirectionalAndGasHasOnlyAnOrbitAnchor() throws Exception {
        var catalog = catalog(PlanetaryContent.definitions());
        var routes = RouteCatalog.create(PlanetaryContent.routes(), catalog.definitions().stream()
                .map(CelestialBodyDefinition::id).toList()).result().orElseThrow();
        assertTrue(PlanetaryCatalog.create(catalog, routes).result().isPresent());
        var earth = RouteAnchor.bodySurface(CelestialIds.EARTH_ID);
        for (var body : PlanetaryContent.definitions()) {
            var target = body.supportsSurfaceArrival() ? RouteAnchor.bodySurface(body.id()) : RouteAnchor.orbit(body.id());
            assertTrue(routes.plan(earth, target).result().isPresent());
            assertTrue(routes.plan(target, earth).result().isPresent());
        }
        assertTrue(routes.plan(earth, RouteAnchor.bodySurface(PlanetaryContent.GAS_GIANT)).result().isEmpty());
        for (var route : PlanetaryContent.routes()) {
            try (var input = getClass().getResourceAsStream("/data/advancedrocketrycommunity/travel_routes/"
                    + route.id().getPath() + ".json")) {
                assertNotNull(input);
                assertEquals(route, RouteDefinition.CODEC.parse(JsonOps.INSTANCE,
                        JsonParser.parseReader(new InputStreamReader(input, StandardCharsets.UTF_8))).result().orElseThrow());
            }
        }
    }

    @Test void upgradeAddsThreeBindingsWithoutChangingExistingIdentities() {
        var old = PlanetaryBindings.adopt(catalog(List.of()));
        var expanded = old.reconcile(catalog(PlanetaryContent.definitions()));
        assertEquals(6, expanded.entries().size());
        assertTrue(expanded.entries().containsAll(old.entries()));
        assertSame(expanded, expanded.reconcile(catalog(List.of())));
        assertTrue(expanded.entries().stream().filter(entry -> entry.bodyId().equals(PlanetaryContent.GAS_GIANT))
                .findFirst().orElseThrow().level().isEmpty());
    }

    @Test void earlierCustomHostNamespaceReservationCannotBeReassignedByBuiltin() {
        var mars = PlanetaryContent.definitions().get(0);
        for (var mapping : List.of(Optional.of(PlanetaryContent.level(ModIdentity.id("earlier_mars"))),
                Optional.<net.minecraft.resources.ResourceKey<Level>>empty())) {
            var custom = new CelestialBodyDefinition(mars.id(), mars.parentId(), mapping,
                    mars.gravityMultiplier(), mars.atmosphere(), mars.orbit(), mars.visualProfile(),
                    new CelestialCapabilities(false, true, false), mars.solarIntensity(), mars.radiation());
            var old = PlanetaryBindings.adopt(catalog(List.of(custom)));
            var before = old.entries();
            assertThrows(IllegalArgumentException.class, () -> old.reconcile(catalog(PlanetaryContent.definitions())));
            assertEquals(before, old.entries());
        }
    }

    @Test void surfacesResolveByMappingNotAJavaDestinationEnum() {
        var catalog = catalog(PlanetaryContent.definitions());
        assertEquals(PlanetaryContent.MARS, PlanetarySurfaceResolver.find(catalog,
                PlanetaryContent.level(PlanetaryContent.MARS), true).orElseThrow().id());
        assertEquals(CelestialIds.EARTH_ID, PlanetarySurfaceResolver.find(catalog, Level.OVERWORLD, true).orElseThrow().id());
        assertTrue(PlanetarySurfaceResolver.find(catalog, CelestialIds.SPACE_LEVEL, false).isEmpty());
        assertTrue(PlanetarySurfaceResolver.find(catalog, PlanetaryContent.level(PlanetaryContent.GAS_GIANT), true).isEmpty());
    }

    @Test void closedMappedSourceCanDepartWhileArrivalRemainsDenied() {
        var mars = PlanetaryContent.definitions().get(0);
        var closed = new CelestialBodyDefinition(mars.id(), mars.parentId(), mars.levelKey(), mars.gravityMultiplier(),
                mars.atmosphere(), mars.orbit(), mars.visualProfile(), new CelestialCapabilities(false, false, false),
                mars.solarIntensity(), mars.radiation());
        var catalog = catalog(List.of(closed));
        assertTrue(PlanetarySurfaceResolver.find(catalog, mars.levelKey().orElseThrow(), false).isPresent());
        assertTrue(PlanetarySurfaceResolver.find(catalog, mars.levelKey().orElseThrow(), true).isEmpty());
    }

    @Test void ambiguousMappingCannotBecomeAFlightOrPlayerStationSource() {
        var mars = PlanetaryContent.definitions().get(0);
        var alias = new CelestialBodyDefinition(ModIdentity.id("alias"), mars.parentId(), mars.levelKey(), mars.gravityMultiplier(),
                mars.atmosphere(), mars.orbit(), mars.visualProfile(), mars.capabilities(), mars.solarIntensity(), mars.radiation());
        var catalog = catalog(List.of(mars, alias));
        assertTrue(PlanetarySurfaceResolver.find(catalog, mars.levelKey().orElseThrow(), false).isEmpty());
        assertTrue(PlanetarySurfaceResolver.find(catalog, mars.levelKey().orElseThrow(), true).isEmpty());
    }

    private static CelestialCatalog catalog(List<CelestialBodyDefinition> additions) {
        var bodies = new ArrayList<>(CelestialDefaults.definitions());
        bodies.addAll(additions);
        return CelestialCatalog.create(bodies).flatMap(CelestialCatalog::requireFixedBaseline).result().orElseThrow();
    }
}
