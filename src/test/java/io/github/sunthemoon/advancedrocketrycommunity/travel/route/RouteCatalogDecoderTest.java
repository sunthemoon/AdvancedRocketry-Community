package io.github.sunthemoon.advancedrocketrycommunity.travel.route;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.serialization.DataResult;
import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import io.github.sunthemoon.advancedrocketrycommunity.travel.route.model.RouteLimits;
import io.github.sunthemoon.advancedrocketrycommunity.travel.route.service.RouteCatalog;
import io.github.sunthemoon.advancedrocketrycommunity.travel.route.service.RouteCatalogDecoder;
import io.github.sunthemoon.advancedrocketrycommunity.travel.route.service.RouteCatalogManager;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class RouteCatalogDecoderTest {
    @BeforeAll
    static void bootstrapMinecraftRegistries() {
        MinecraftBootstrap.initialize();
    }

    @Test
    void builtInRoutesDecodeInStableOrder() throws IOException {
        RouteCatalog catalog = RouteCatalogDecoder.decode(
                builtInRoutes(),
                List.of(ModIdentity.id("earth"), ModIdentity.id("moon"), ModIdentity.id("space"))
        ).getOrThrow(false, message -> {
            throw new AssertionError(message);
        });

        assertEquals(3, catalog.routeCount());
        assertEquals(ModIdentity.id("earth_moon"), catalog.definitions().get(0).id());
    }

    @Test
    void resourceIdentityAndPayloadSizeAreValidated() throws IOException {
        Map<ResourceLocation, JsonElement> mismatched = builtInRoutes();
        JsonElement route = mismatched.remove(ModIdentity.id("earth_moon"));
        mismatched.put(ModIdentity.id("wrong"), route);
        assertTrue(RouteCatalogDecoder.decode(
                mismatched,
                List.of(ModIdentity.id("earth"), ModIdentity.id("moon"))
        ).error().orElseThrow().message().contains("mismatched id"));

        Map<ResourceLocation, JsonElement> oversized = builtInRoutes();
        oversized.get(ModIdentity.id("earth_moon")).getAsJsonObject()
                .addProperty("padding", "x".repeat(RouteLimits.MAX_JSON_CHARS_PER_ROUTE));
        assertTrue(RouteCatalogDecoder.decode(
                oversized,
                List.of(ModIdentity.id("earth"), ModIdentity.id("moon"))
        ).error().orElseThrow().message().contains("JSON characters"));
    }

    @Test
    void rejectedReloadRetainsTheLastValidCatalogAndBoundsDiagnostics() throws IOException {
        RouteCatalogManager manager = new RouteCatalogManager();
        assertTrue(manager.applyCandidate(RouteCatalogDecoder.decode(
                builtInRoutes(),
                List.of(ModIdentity.id("earth"), ModIdentity.id("moon"), ModIdentity.id("space"))
        )));
        RouteCatalog accepted = manager.current().orElseThrow();

        assertFalse(manager.applyCandidate(DataResult.error(() -> "x".repeat(3_000))));

        assertSame(accepted, manager.current().orElseThrow());
        assertEquals(1L, manager.status().generation());
        assertFalse(manager.status().lastReloadAccepted());
        assertEquals(RouteLimits.MAX_STATUS_MESSAGE_CHARS, manager.status().message().length());
    }

    @Test
    void testMarsIsAddedThroughDataWithoutAProductionEnum() throws IOException {
        Map<ResourceLocation, JsonElement> routes = new LinkedHashMap<>();
        routes.put(
                ModIdentity.id("test_earth_mars"),
                resource("data/advancedrocketrycommunity/test_travel_routes/test_earth_mars.json")
        );
        JsonElement body = resource("data/advancedrocketrycommunity/test_celestial_bodies/test_mars.json");
        ResourceLocation marsId = ResourceLocation.tryParse(body.getAsJsonObject().get("id").getAsString());

        RouteCatalog catalog = RouteCatalogDecoder.decode(
                routes,
                List.of(ModIdentity.id("earth"), marsId)
        ).getOrThrow(false, message -> {
            throw new AssertionError(message);
        });

        assertEquals(1, catalog.routeCount());
        assertEquals(225, catalog.definitions().get(0).distanceUnits());
    }

    private static Map<ResourceLocation, JsonElement> builtInRoutes() throws IOException {
        Map<ResourceLocation, JsonElement> resources = new LinkedHashMap<>();
        for (String id : List.of("earth_moon", "earth_surface_orbit", "moon_earth_orbit")) {
            resources.put(
                    ModIdentity.id(id),
                    resource("data/advancedrocketrycommunity/travel_routes/" + id + ".json")
            );
        }
        return resources;
    }

    private static JsonElement resource(String path) throws IOException {
        try (InputStream stream = RouteCatalogDecoderTest.class.getClassLoader().getResourceAsStream(path)) {
            if (stream == null) {
                throw new IOException("Missing test resource " + path);
            }
            return JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8));
        }
    }
}
