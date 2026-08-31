package io.github.sunthemoon.advancedrocketrycommunity.celestial;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.JsonOps;
import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.model.CelestialBodyDefinition;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.service.CelestialCatalog;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.service.CelestialCatalogDecoder;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.service.CelestialCatalogManager;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.service.CelestialEnvironmentService;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class CelestialCatalogDecoderTest {
    @BeforeAll
    static void bootstrapMinecraftRegistries() {
        MinecraftBootstrap.initialize();
    }

    @Test
    void canonicalResourceSetDecodesInStableOrder() {
        CelestialCatalog catalog = CelestialCatalogDecoder.decode(canonicalResources())
                .getOrThrow(false, message -> {
                    throw new AssertionError(message);
                });

        assertEquals(3, catalog.size());
        assertEquals(CelestialIds.EARTH_ID, catalog.definitions().get(0).id());
        assertEquals(CelestialIds.SPACE_ID, catalog.definitions().get(2).id());
    }

    @Test
    void resourceAndDeclaredIdentityMustMatch() {
        Map<ResourceLocation, JsonElement> resources = canonicalResources();
        JsonElement moon = resources.remove(CelestialIds.MOON_ID);
        resources.put(ModIdentity.id("wrong_name"), moon);

        DataResult<CelestialCatalog> result = CelestialCatalogDecoder.decode(resources);

        assertTrue(result.error().isPresent());
        assertTrue(result.error().orElseThrow().message().contains("mismatched id"));
    }

    @Test
    void individualJsonPayloadIsBounded() {
        Map<ResourceLocation, JsonElement> resources = canonicalResources();
        JsonObject earth = resources.get(CelestialIds.EARTH_ID).getAsJsonObject();
        earth.addProperty("ignored_padding", "x".repeat(CelestialCatalogDecoder.MAX_JSON_CHARS_PER_BODY));

        DataResult<CelestialCatalog> result = CelestialCatalogDecoder.decode(resources);

        assertTrue(result.error().isPresent());
        assertTrue(result.error().orElseThrow().message().contains("JSON characters"));
    }

    @Test
    void rejectedCandidateRetainsLastValidCatalog() {
        CelestialCatalogManager manager = new CelestialCatalogManager();
        assertTrue(manager.applyCandidate(CelestialCatalogDecoder.decode(canonicalResources())));
        CelestialCatalog accepted = manager.current().orElseThrow();

        assertFalse(manager.applyCandidate(DataResult.error(() -> "synthetic invalid reload")));

        assertSame(accepted, manager.current().orElseThrow());
        assertEquals(1L, manager.status().generation());
        assertEquals(3, manager.status().bodyCount());
        assertFalse(manager.status().lastReloadAccepted());
        assertEquals("synthetic invalid reload", manager.status().message());
    }

    @Test
    void diagnosticsAreBounded() {
        CelestialCatalogManager manager = new CelestialCatalogManager();

        assertFalse(manager.applyCandidate(DataResult.error(() -> "x".repeat(3_000))));

        assertEquals(CelestialCatalogManager.MAX_STATUS_MESSAGE_CHARS, manager.status().message().length());
        assertTrue(manager.status().message().endsWith("..."));
    }

    @Test
    void environmentProfilesResolveFromPublishedCatalogInConstantTime() {
        CelestialCatalogManager manager = new CelestialCatalogManager();
        assertTrue(manager.applyCandidate(CelestialCatalogDecoder.decode(canonicalResources())));
        CelestialEnvironmentService environments = new CelestialEnvironmentService(manager);

        CelestialEnvironmentService.EnvironmentProfile moon = environments
                .forLevel(CelestialIds.MOON_LEVEL)
                .orElseThrow();

        assertEquals(CelestialIds.MOON_ID, moon.bodyId());
        assertEquals(0.165D, moon.gravityMultiplier());
        assertTrue(moon.vacuum());
        assertFalse(moon.breathable());
    }

    private static Map<ResourceLocation, JsonElement> canonicalResources() {
        Map<ResourceLocation, JsonElement> resources = new LinkedHashMap<>();
        for (CelestialBodyDefinition definition : CelestialDefaults.definitions()) {
            JsonElement encoded = CelestialBodyDefinition.CODEC.encodeStart(JsonOps.INSTANCE, definition)
                    .getOrThrow(false, message -> {
                        throw new AssertionError(message);
                    });
            resources.put(definition.id(), encoded);
        }
        return resources;
    }
}
