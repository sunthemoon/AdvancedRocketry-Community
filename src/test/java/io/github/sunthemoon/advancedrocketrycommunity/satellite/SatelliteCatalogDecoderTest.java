package io.github.sunthemoon.advancedrocketrycommunity.satellite;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.JsonOps;
import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.model.SatelliteDefinition;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.model.SatelliteLimits;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.service.SatelliteCatalog;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.service.SatelliteCatalogDecoder;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.service.SatelliteCatalogManager;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

final class SatelliteCatalogDecoderTest {
    private static final List<net.minecraft.resources.ResourceLocation> TARGETS = List.of(
            ModIdentity.id("earth"),
            ModIdentity.id("moon"),
            ModIdentity.id("space")
    );

    @BeforeAll
    static void bootstrapMinecraftRegistries() {
        MinecraftBootstrap.initialize();
    }

    @Test
    void canonicalDataSatelliteRoundTripsAndPublishes() {
        SatelliteDefinition definition = definition(SatelliteIds.DATA_SATELLITE);
        JsonElement encoded = SatelliteDefinition.CODEC.encodeStart(JsonOps.INSTANCE, definition)
                .getOrThrow(false, message -> {
                    throw new AssertionError(message);
                });

        SatelliteCatalog catalog = SatelliteCatalogDecoder.decode(
                Map.of(definition.id(), encoded),
                TARGETS
        ).getOrThrow(false, message -> {
            throw new AssertionError(message);
        });

        assertEquals(1, catalog.size());
        assertEquals(definition, catalog.get(SatelliteIds.DATA_SATELLITE).orElseThrow());
    }

    @Test
    void requiredTypeMayNotBeAbsentButOptionalTypeMayBePresent() {
        SatelliteDefinition optional = definition(ModIdentity.id("optional_scanner"));
        JsonElement optionalJson = SatelliteDefinition.CODEC.encodeStart(JsonOps.INSTANCE, optional)
                .result().orElseThrow();
        assertTrue(SatelliteCatalogDecoder.decode(Map.of(optional.id(), optionalJson), TARGETS)
                .error().orElseThrow().message().contains("data_satellite"));

        SatelliteDefinition required = definition(SatelliteIds.DATA_SATELLITE);
        Map<net.minecraft.resources.ResourceLocation, JsonElement> resources = new LinkedHashMap<>();
        resources.put(required.id(), SatelliteDefinition.CODEC.encodeStart(JsonOps.INSTANCE, required)
                .result().orElseThrow());
        resources.put(optional.id(), optionalJson);

        assertEquals(2, SatelliteCatalogDecoder.decode(resources, TARGETS)
                .result().orElseThrow().size());
    }

    @Test
    void duplicateTargetsAndYieldBelowDiscoveryCostAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> new SatelliteDefinition(
                SatelliteLimits.DEFINITION_SCHEMA_VERSION,
                SatelliteIds.DATA_SATELLITE,
                200,
                120,
                100,
                List.of(ModIdentity.id("moon"), ModIdentity.id("moon"))
        ));
        assertThrows(IllegalArgumentException.class, () -> new SatelliteDefinition(
                SatelliteLimits.DEFINITION_SCHEMA_VERSION,
                SatelliteIds.DATA_SATELLITE,
                200,
                99,
                100,
                TARGETS
        ));

        JsonObject malformed = canonicalJson().getAsJsonObject();
        malformed.addProperty("discovery_cost", 121);
        DataResult<SatelliteCatalog> decoded = SatelliteCatalogDecoder.decode(
                Map.of(SatelliteIds.DATA_SATELLITE, malformed), TARGETS
        );
        assertTrue(decoded.error().isPresent());
        assertTrue(decoded.error().orElseThrow().message().contains("Research yield"));
    }

    @Test
    void unknownTargetMismatchedIdentityAndOversizedJsonAreRejected() {
        SatelliteDefinition unknownTarget = new SatelliteDefinition(
                SatelliteLimits.DEFINITION_SCHEMA_VERSION,
                SatelliteIds.DATA_SATELLITE,
                200,
                120,
                100,
                List.of(ModIdentity.id("missing_body"))
        );
        JsonElement encoded = SatelliteDefinition.CODEC.encodeStart(JsonOps.INSTANCE, unknownTarget)
                .result().orElseThrow();
        assertTrue(SatelliteCatalogDecoder.decode(Map.of(unknownTarget.id(), encoded), TARGETS)
                .error().orElseThrow().message().contains("Unknown celestial target"));

        assertTrue(SatelliteCatalogDecoder.decode(
                Map.of(ModIdentity.id("wrong_name"), canonicalJson()), TARGETS
        ).error().orElseThrow().message().contains("mismatched id"));

        JsonObject oversized = canonicalJson().getAsJsonObject();
        oversized.addProperty("ignored_padding", "x".repeat(SatelliteLimits.MAX_JSON_CHARS_PER_DEFINITION));
        assertTrue(SatelliteCatalogDecoder.decode(
                Map.of(SatelliteIds.DATA_SATELLITE, oversized), TARGETS
        ).error().orElseThrow().message().contains("JSON character limit"));
    }

    @Test
    void rejectedReloadRetainsLastValidCatalogAndBoundsDiagnostics() {
        SatelliteCatalogManager manager = new SatelliteCatalogManager();
        assertTrue(manager.applyCandidate(SatelliteCatalogDecoder.decode(
                Map.of(SatelliteIds.DATA_SATELLITE, canonicalJson()), TARGETS
        )));
        SatelliteCatalog accepted = manager.current().orElseThrow();

        assertFalse(manager.applyCandidate(DataResult.error(() -> "x".repeat(3_000))));

        assertSame(accepted, manager.current().orElseThrow());
        assertEquals(1L, manager.status().generation());
        assertEquals(SatelliteCatalogManager.MAX_STATUS_MESSAGE_CHARS,
                manager.status().message().length());
    }

    private static SatelliteDefinition definition(net.minecraft.resources.ResourceLocation id) {
        return new SatelliteDefinition(
                SatelliteLimits.DEFINITION_SCHEMA_VERSION,
                id,
                200,
                120,
                100,
                TARGETS
        );
    }

    private static JsonElement canonicalJson() {
        return SatelliteDefinition.CODEC.encodeStart(
                JsonOps.INSTANCE,
                definition(SatelliteIds.DATA_SATELLITE)
        ).result().orElseThrow();
    }
}
