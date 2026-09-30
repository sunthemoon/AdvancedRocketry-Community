package io.github.sunthemoon.advancedrocketrycommunity.celestial.data;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.DataResult;
import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.CelestialIds;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.network.CelestialSnapshotPacket;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import io.github.sunthemoon.advancedrocketrycommunity.travel.route.model.RouteAnchor;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.PathPackResources;
import net.minecraft.server.packs.resources.MultiPackResourceManager;
import net.minecraft.server.packs.resources.PreparableReloadListener;
import net.minecraft.util.profiling.InactiveProfiler;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Real files, pack priority and SimplePreparableReloadListener lifecycle, not pre-parsed JSON maps. */
class PlanetaryReloadTest {
    private static final String BODIES = PlanetaryDefinitionReloadListener.BODY_DIRECTORY;
    private static final String ROUTES = PlanetaryDefinitionReloadListener.ROUTE_DIRECTORY;
    private static final PreparableReloadListener.PreparationBarrier IMMEDIATE = new PreparableReloadListener.PreparationBarrier() {
        @Override public <T> CompletableFuture<T> wait(T prepared) { return CompletableFuture.completedFuture(prepared); }
    };
    @TempDir Path root;
    private Path pack;
    private PlanetaryCatalogManager manager;

    @BeforeAll
    static void bootstrap() {
        MinecraftBootstrap.initialize();
    }

    @BeforeEach
    void initialPack() throws IOException {
        pack = root.resolve("pack");
        for (String body : List.of("earth", "moon", "space")) {
            write(BODIES, body, builtIn(BODIES, body));
        }
        for (String route : List.of("earth_moon", "earth_surface_orbit", "moon_earth_orbit", "moon_surface_orbit")) {
            write(ROUTES, route, builtIn(ROUTES, route));
        }
        manager = new PlanetaryCatalogManager();
    }

    @Test
    void publishesOnePairAndGenerationWithReadOnlyCelestialView() throws IOException {
        reload();
        var first = manager.capture().orElseThrow();
        assertEquals(1, first.generation());
        assertEquals(3, first.catalog().celestial().size());
        assertEquals(4, first.catalog().routes().routeCount());
        var view = manager.celestialView().snapshot();
        assertSame(first.catalog().celestial(), view.catalog());
        assertEquals(first.generation(), view.status().generation());
        assertThrows(IllegalStateException.class, manager.celestialView()::clear);
        assertThrows(IllegalStateException.class, () -> manager.celestialView()
                .applyCandidate(DataResult.success(first.catalog().celestial())));

        JsonObject moon = json(BODIES, "moon");
        moon.addProperty("gravity_multiplier", 0.25);
        write(BODIES, "moon", moon.toString());
        JsonObject route = json(ROUTES, "earth_moon");
        route.addProperty("distance_units", 123);
        write(ROUTES, "earth_moon", route.toString());
        reload();
        var second = manager.capture().orElseThrow();
        assertEquals(2, second.generation());
        assertEquals(0.25, second.catalog().celestial().get(CelestialIds.MOON_ID).orElseThrow().gravityMultiplier());
        assertEquals(123, second.catalog().routes().definitions().get(0).distanceUnits());
        assertNotSame(first.catalog(), second.catalog());
        assertEquals(1, first.generation());
        assertNotEquals(0.25, first.catalog().celestial().get(CelestialIds.MOON_ID).orElseThrow().gravityMultiplier());
        manager.clear();
        assertTrue(manager.capture().isEmpty());
        assertTrue(manager.celestialView().current().isEmpty());
        assertEquals(0, manager.status().generation());
    }

    @Test
    void invalidRouteRetainsBothObjectsGenerationAndPopulatedCache() throws IOException {
        reload();
        var before = manager.capture().orElseThrow();
        var routes = before.catalog().routes();
        var plan = routes.plan(RouteAnchor.bodySurface(CelestialIds.EARTH_ID), RouteAnchor.bodySurface(CelestialIds.MOON_ID))
                .result().orElseThrow();
        assertEquals(1, routes.cachedPlanCount());
        var moon = json(BODIES, "moon");
        moon.addProperty("gravity_multiplier", 0.5);
        write(BODIES, "moon", moon.toString());
        var broken = json(ROUTES, "earth_moon");
        broken.getAsJsonObject("to").addProperty("body_id", ModIdentity.id("missing").toString());
        write(ROUTES, "earth_moon", broken.toString());
        reload();
        retained(before);
        assertSame(plan, routes.plan(RouteAnchor.bodySurface(CelestialIds.EARTH_ID), RouteAnchor.bodySurface(CelestialIds.MOON_ID))
                .result().orElseThrow());
        assertEquals(1, routes.cachedPlanCount());
        assertTrue(manager.status().message().contains("missing body"));
    }

    @Test
    void malformedExtraFileCannotDisappearBeforeValidation() throws IOException {
        reload();
        var before = manager.capture().orElseThrow();
        for (String directory : List.of(BODIES, ROUTES)) {
            write(directory, "broken", "{\"id\":");
            reload();
            retained(before);
            assertTrue(manager.status().message().contains(directory + "/broken.json"));
            Files.delete(file(pack, directory, "broken"));
        }
    }

    @Test
    void initialInvalidBodyOrRouteFailsWithoutPublishingHalfAStartup() throws IOException {
        for (String directory : List.of(BODIES, ROUTES)) {
            write(directory, "broken", "{");
            assertThrows(CompletionException.class, this::reload);
            assertTrue(manager.capture().isEmpty());
            assertTrue(manager.celestialView().current().isEmpty());
            assertFalse(manager.status().ready());
            assertEquals(0, manager.status().generation());
            Files.delete(file(pack, directory, "broken"));
        }
    }

    @Test
    void depthDuplicateAndRawByteFailuresRetainTheWholePair() throws IOException {
        reload();
        var before = manager.capture().orElseThrow();
        for (String invalid : List.of("{\"a\":1,\"\\u0061\":2}", "[".repeat(17) + "0" + "]".repeat(17),
                " ".repeat(BoundedDefinitionJson.MAX_BODY_BYTES) + "{}")) {
            write(BODIES, "broken", invalid);
            reload();
            retained(before);
        }
        Files.delete(file(pack, BODIES, "broken"));
        write(ROUTES, "broken", " ".repeat(BoundedDefinitionJson.MAX_ROUTE_BYTES) + "{}");
        reload();
        retained(before);
        assertTrue(manager.status().message().contains("4096 UTF-8 bytes"));
    }

    @Test
    void schemaAndParentFailuresRejectAValidRouteChange() throws IOException {
        reload();
        var before = manager.capture().orElseThrow();
        var route = json(ROUTES, "earth_moon");
        route.addProperty("distance_units", 999);
        write(ROUTES, "earth_moon", route.toString());
        var moon = json(BODIES, "moon");
        moon.addProperty("parent", ModIdentity.id("missing").toString());
        write(BODIES, "moon", moon.toString());
        reload();
        retained(before);
        assertTrue(manager.status().message().contains("Missing parent"));
        moon.addProperty("schema_version", 20);
        write(BODIES, "moon", moon.toString());
        reload();
        retained(before);
    }

    @Test
    void unmappedOrbitIsValidButItsSurfaceEndpointIsNot() throws IOException {
        write(BODIES, "gas", gas("gas").toString());
        write(ROUTES, "gas_route", orbitRoute("gas_route", "earth", "gas").toString());
        reload();
        var before = manager.capture().orElseThrow();
        assertEquals(4, before.catalog().celestial().size());
        assertEquals(5, before.catalog().routes().routeCount());
        var route = json(ROUTES, "gas_route");
        route.getAsJsonObject("to").addProperty("type", ModIdentity.id("body_surface").toString());
        write(ROUTES, "gas_route", route.toString());
        reload();
        retained(before);
        assertTrue(manager.status().message().contains("mapped non-gas surface"));
    }

    @Test
    void closedAdmissionDoesNotEraseReverseDepartureEdges() throws IOException {
        JsonObject moon = json(BODIES, "moon");
        moon.addProperty("schema_version", 2);
        moon.addProperty("solar_intensity", 1);
        moon.addProperty("radiation", 0);
        moon.add("capabilities", JsonParser.parseString("{\"landable\":false,\"orbitable\":false,\"gas_giant\":false}"));
        write(BODIES, "moon", moon.toString());
        reload();
        assertTrue(manager.status().lastReloadAccepted());
        assertTrue(manager.capture().orElseThrow().catalog().routes()
                .plan(RouteAnchor.bodySurface(CelestialIds.MOON_ID), RouteAnchor.bodySurface(CelestialIds.EARTH_ID)).result().isPresent());
    }

    @Test
    void removingBodyRequiresItsRoutesToBeRemovedInTheSameCandidate() throws IOException {
        write(BODIES, "gas", gas("gas").toString());
        write(ROUTES, "gas_route", orbitRoute("gas_route", "earth", "gas").toString());
        reload();
        var before = manager.capture().orElseThrow();
        Files.delete(file(pack, BODIES, "gas"));
        reload();
        retained(before);
        Files.delete(file(pack, ROUTES, "gas_route"));
        reload();
        assertEquals(2, manager.status().generation());
        assertEquals(3, manager.status().bodyCount());
        assertEquals(4, manager.status().routeCount());
        assertFalse(manager.capture().orElseThrow().catalog().celestial().get(ModIdentity.id("gas")).isPresent());
    }

    @Test
    void finiteHundredBodyPackReloadsAndSerializesWithinExistingBudgets() throws IOException {
        for (int index = 0; index < 97; index++) {
            String id = "gas_" + index;
            write(BODIES, id, gas(id).toString());
            write(ROUTES, id, orbitRoute(id, index == 0 ? "earth" : "gas_" + (index - 1), id).toString());
        }
        reload();
        var captured = manager.capture().orElseThrow();
        assertEquals(100, captured.catalog().celestial().size());
        assertEquals(101, captured.catalog().routes().routeCount());
        assertEquals(97, captured.catalog().routes().plan(RouteAnchor.orbit(CelestialIds.EARTH_ID),
                RouteAnchor.orbit(ModIdentity.id("gas_96"))).result().orElseThrow().totalDistanceUnits());
        assertTrue(CelestialSnapshotPacket.fromCatalog(captured.catalog().celestial(), captured.generation()).result().isPresent());
    }

    @Test
    void bothResourceCountLimitsRejectBeforeDecodingASubset() throws IOException {
        reload();
        var before = manager.capture().orElseThrow();
        for (int index = 0; index < 126; index++) {
            write(BODIES, "extra_" + index, "{}");
        }
        reload();
        retained(before);
        assertTrue(manager.status().message().contains("resource count exceeds 128"));
        for (int index = 0; index < 126; index++) {
            Files.delete(file(pack, BODIES, "extra_" + index));
        }
        for (int index = 0; index < 509; index++) {
            write(ROUTES, "extra_" + index, "{}");
        }
        reload();
        retained(before);
        assertTrue(manager.status().message().contains("resource count exceeds 512"));
    }

    @Test
    void diagnosticsAreBoundedAcrossBothDirectories() throws IOException {
        reload();
        var before = manager.capture().orElseThrow();
        for (int index = 0; index < 6; index++) {
            write(BODIES, "a" + index, "{");
            write(ROUTES, "a" + index, "{");
        }
        reload();
        retained(before);
        assertEquals(8, manager.status().message().split("\\.json:").length - 1);
        assertTrue(manager.status().message().length() <= ReloadDiagnostics.MAX_CHARS);
        manager.applyCandidate(DataResult.error(() -> "x".repeat(5_000)));
        assertEquals(ReloadDiagnostics.MAX_CHARS, manager.status().message().length());
    }

    @Test
    void winningPackResourceOverridesInvalidLowerPriorityDefinition() throws IOException {
        write(BODIES, "moon", "{");
        Path higher = root.resolve("higher");
        Path override = file(higher, BODIES, "moon");
        Files.createDirectories(override.getParent());
        Files.writeString(override, builtIn(BODIES, "moon"));
        try (var resources = new MultiPackResourceManager(PackType.SERVER_DATA,
                List.of(new PathPackResources("base", pack, false), new PathPackResources("higher", higher, false)))) {
            reload(resources);
        }
        assertEquals(1, manager.status().generation());
        assertEquals(3, manager.status().bodyCount());
    }

    @Test
    void preparationCannotPublishUntilTheReloadBarrierCompletes() throws IOException {
        reload();
        var before = manager.capture().orElseThrow();
        write(BODIES, "gas", gas("gas").toString());
        write(ROUTES, "gas_route", orbitRoute("gas_route", "earth", "gas").toString());
        var gate = new CompletableFuture<Void>();
        var barrier = new PreparableReloadListener.PreparationBarrier() {
            @Override public <T> CompletableFuture<T> wait(T prepared) { return gate.thenApply(ignored -> prepared); }
        };
        try (var resources = new MultiPackResourceManager(PackType.SERVER_DATA,
                List.of(new PathPackResources("fixture", pack, false)))) {
            var reload = new PlanetaryDefinitionReloadListener(manager).reload(barrier, resources,
                    InactiveProfiler.INSTANCE, InactiveProfiler.INSTANCE, Runnable::run, Runnable::run);
            assertFalse(reload.isDone());
            assertSame(before.catalog(), manager.capture().orElseThrow().catalog());
            assertEquals(1, manager.celestialView().status().generation());
            gate.complete(null);
            reload.join();
            assertEquals(2, manager.status().generation());
            assertEquals(4, manager.status().bodyCount());
            assertEquals(5, manager.status().routeCount());
        }
    }

    @Test
    void streamReadFailureClosesResourceAndRetainsPair() {
        reload();
        var before = manager.capture().orElseThrow();
        var closes = new AtomicInteger();
        var failingPack = new PathPackResources("read-failure", pack, false) {
            @Override
            public void listResources(PackType type, String namespace, String directory, ResourceOutput output) {
                super.listResources(type, namespace, directory, output);
                if (directory.equals(BODIES)) {
                    output.accept(ModIdentity.id(BODIES + "/broken.json"), () -> new InputStream() {
                        @Override public int read() throws IOException { throw new IOException("fixture read failure"); }
                        @Override public void close() { closes.incrementAndGet(); }
                    });
                }
            }
        };
        try (var resources = new MultiPackResourceManager(PackType.SERVER_DATA, List.of(failingPack))) {
            reload(resources);
        }
        retained(before);
        assertEquals(1, closes.get());
        assertTrue(manager.status().message().contains("broken.json: fixture read failure"));
    }

    @Test
    void starSystemsDeriveFromRootsAndCrossSystemRoutesRejectTheWholeCandidate() throws IOException {
        write(BODIES, "far_star", star("far_star").toString());
        write(BODIES, "far_planet", child("far_planet", "far_star").toString());
        write(ROUTES, "far_local", orbitRoute("far_local", "far_star", "far_planet").toString());
        reload();
        var accepted = manager.capture().orElseThrow();
        var celestial = accepted.catalog().celestial();
        assertEquals(List.of(CelestialIds.EARTH_ID, ModIdentity.id("far_star")), celestial.systems());
        assertEquals(ModIdentity.id("far_star"), celestial.systemOf(ModIdentity.id("far_planet")).orElseThrow());
        assertEquals(CelestialIds.EARTH_ID, celestial.systemOf(CelestialIds.MOON_ID).orElseThrow());

        // A route between systems rejects the whole reload; the previous pair stays active.
        write(ROUTES, "interstellar", orbitRoute("interstellar", "earth", "far_planet").toString());
        reload();
        retained(accepted);
        assertTrue(manager.status().message().contains("connects systems"), manager.status().message());
    }

    @Test
    void exactlySixteenStarSystemsAreAccepted() throws IOException {
        reload();
        int roots = manager.capture().orElseThrow().catalog().celestial().systems().size();
        for (int index = 0; index < 16 - roots; index++) {
            write(BODIES, "star_" + index, star("star_" + index).toString());
        }
        reload();
        assertEquals(16, manager.capture().orElseThrow().catalog().celestial().systems().size(),
                manager.status().message());
        write(BODIES, "star_extra", star("star_extra").toString());
        var sixteen = manager.capture().orElseThrow();
        reload();
        retained(sixteen);
        assertTrue(manager.status().message().contains("too many root bodies"), manager.status().message());
    }

    @Test
    void tooManyStarSystemsRejectTheReload() throws IOException {
        reload();
        var before = manager.capture().orElseThrow();
        for (int index = 0; index < 16; index++) {
            write(BODIES, "star_" + index, star("star_" + index).toString());
        }
        reload();
        retained(before);
        assertTrue(manager.status().message().contains("too many root bodies"), manager.status().message());
    }

    @Test
    void invalidInitialStarSystemDataRefusesTheFirstLoad() throws IOException {
        write(BODIES, "far_star", star("far_star").toString());
        write(BODIES, "far_planet", child("far_planet", "far_star").toString());
        write(ROUTES, "interstellar", orbitRoute("interstellar", "earth", "far_planet").toString());
        var failure = assertThrows(CompletionException.class, this::reload);
        assertTrue(failure.getCause() instanceof IllegalStateException, String.valueOf(failure.getCause()));
        assertTrue(failure.getCause().getMessage().contains("Initial planetary catalog is invalid")
                && failure.getCause().getMessage().contains("connects systems"), failure.getCause().getMessage());
        assertTrue(manager.capture().isEmpty());
    }

    private void retained(PlanetaryCatalogManager.Generation previous) {
        var current = manager.capture().orElseThrow();
        assertSame(previous.catalog(), current.catalog());
        assertEquals(previous.generation(), current.generation());
        assertFalse(manager.status().lastReloadAccepted());
        assertSame(previous.catalog().celestial(), manager.celestialView().current().orElseThrow());
        assertEquals(previous.generation(), manager.celestialView().status().generation());
    }

    private void reload() {
        try (var resources = new MultiPackResourceManager(PackType.SERVER_DATA,
                List.of(new PathPackResources("fixture", pack, false)))) {
            reload(resources);
        }
    }

    private void reload(MultiPackResourceManager resources) {
        new PlanetaryDefinitionReloadListener(manager).reload(IMMEDIATE, resources,
                InactiveProfiler.INSTANCE, InactiveProfiler.INSTANCE, Runnable::run, Runnable::run).join();
    }

    private void write(String directory, String id, String text) throws IOException {
        Path path = file(pack, directory, id);
        Files.createDirectories(path.getParent());
        Files.writeString(path, text, StandardCharsets.UTF_8);
    }

    private JsonObject json(String directory, String id) throws IOException {
        return JsonParser.parseString(Files.readString(file(pack, directory, id))).getAsJsonObject();
    }

    private static Path file(Path pack, String directory, String id) {
        return pack.resolve("data/" + ModIdentity.MOD_ID + "/" + directory + "/" + id + ".json");
    }

    private static String builtIn(String directory, String id) throws IOException {
        try (var input = PlanetaryReloadTest.class.getClassLoader()
                .getResourceAsStream("data/" + ModIdentity.MOD_ID + "/" + directory + "/" + id + ".json")) {
            assertNotNull(input);
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static JsonObject gas(String id) throws IOException {
        var body = JsonParser.parseString(builtIn(BODIES, "moon")).getAsJsonObject();
        body.addProperty("id", ModIdentity.id(id).toString());
        body.addProperty("schema_version", 2);
        body.remove("level");
        body.addProperty("solar_intensity", 0.1);
        body.addProperty("radiation", 0.2);
        body.add("capabilities", JsonParser.parseString("{\"landable\":false,\"orbitable\":true,\"gas_giant\":true}"));
        return body;
    }

    /** A root body (star system) with no Level; not landable or orbitable. */
    private static JsonObject star(String id) throws IOException {
        var body = gas(id);
        body.remove("parent");
        body.add("orbit", JsonParser.parseString("{\"distance\":0,\"period_ticks\":0,\"inclination_degrees\":0.0}"));
        body.add("capabilities", JsonParser.parseString("{\"landable\":false,\"orbitable\":false,\"gas_giant\":false}"));
        return body;
    }

    private static JsonObject child(String id, String parent) throws IOException {
        var body = gas(id);
        body.addProperty("parent", ModIdentity.id(parent).toString());
        return body;
    }

    private static JsonObject orbitRoute(String id, String from, String to) throws IOException {
        var route = JsonParser.parseString(builtIn(ROUTES, "earth_moon")).getAsJsonObject();
        route.addProperty("id", ModIdentity.id(id).toString());
        route.addProperty("distance_units", 1);
        for (String endpoint : List.of("from", "to")) {
            route.getAsJsonObject(endpoint).addProperty("type", ModIdentity.id("orbit").toString());
            route.getAsJsonObject(endpoint).addProperty("body_id", ModIdentity.id(endpoint.equals("from") ? from : to).toString());
        }
        return route;
    }
}
