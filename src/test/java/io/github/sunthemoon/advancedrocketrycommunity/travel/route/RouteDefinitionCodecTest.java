package io.github.sunthemoon.advancedrocketrycommunity.travel.route;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import io.github.sunthemoon.advancedrocketrycommunity.travel.route.model.RouteAnchor;
import io.github.sunthemoon.advancedrocketrycommunity.travel.route.model.RouteDefinition;
import io.github.sunthemoon.advancedrocketrycommunity.travel.route.model.RouteLimits;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class RouteDefinitionCodecTest {
    @BeforeAll
    static void bootstrapMinecraftRegistries() {
        MinecraftBootstrap.initialize();
    }

    @Test
    void canonicalDefinitionRoundTrips() {
        RouteDefinition definition = route("earth_moon", "earth", "moon", 50, true);

        JsonElement encoded = RouteDefinition.CODEC.encodeStart(JsonOps.INSTANCE, definition)
                .getOrThrow(false, message -> {
                    throw new AssertionError(message);
                });
        RouteDefinition decoded = RouteDefinition.CODEC.parse(JsonOps.INSTANCE, encoded)
                .getOrThrow(false, message -> {
                    throw new AssertionError(message);
                });

        assertEquals(definition, decoded);
        assertEquals(6, encoded.getAsJsonObject().size());
        assertEquals(2, encoded.getAsJsonObject().getAsJsonObject("from").size());
    }

    @Test
    void exactOuterAndAnchorShapesAreRequired() {
        assertRejected("""
                {"schema_version":1,"id":"advancedrocketrycommunity:r","from":{"type":"advancedrocketrycommunity:body_surface","body_id":"advancedrocketrycommunity:earth"},"to":{"type":"advancedrocketrycommunity:orbit","body_id":"advancedrocketrycommunity:earth"},"distance_units":25,"bidirectional":true,"extra":0}
                """);
        assertRejected("""
                {"schema_version":1,"id":"advancedrocketrycommunity:r","from":{"type":"advancedrocketrycommunity:body_surface","body_id":"advancedrocketrycommunity:earth","instance_id":"123e4567-e89b-42d3-a456-426614174000"},"to":{"type":"advancedrocketrycommunity:orbit","body_id":"advancedrocketrycommunity:earth"},"distance_units":25,"bidirectional":true}
                """);
    }

    @Test
    void futureSchemaUnknownAnchorAndOutOfRangeDistanceAreRejected() {
        assertRejected("""
                {"schema_version":2,"id":"advancedrocketrycommunity:r","from":{"type":"advancedrocketrycommunity:body_surface","body_id":"advancedrocketrycommunity:earth"},"to":{"type":"advancedrocketrycommunity:orbit","body_id":"advancedrocketrycommunity:earth"},"distance_units":25,"bidirectional":true}
                """);
        assertRejected("""
                {"schema_version":1,"id":"advancedrocketrycommunity:r","from":{"type":"advancedrocketrycommunity:station","body_id":"advancedrocketrycommunity:earth"},"to":{"type":"advancedrocketrycommunity:orbit","body_id":"advancedrocketrycommunity:earth"},"distance_units":25,"bidirectional":true}
                """);
        assertRejected("""
                {"schema_version":1,"id":"advancedrocketrycommunity:r","from":{"type":"advancedrocketrycommunity:body_surface","body_id":"advancedrocketrycommunity:earth"},"to":{"type":"advancedrocketrycommunity:orbit","body_id":"advancedrocketrycommunity:earth"},"distance_units":1000001,"bidirectional":true}
                """);
    }

    @Test
    void constructorEnforcesSchemaIdentifierAndDistanceBounds() {
        RouteDefinition valid = route("earth_moon", "earth", "moon", 50, true);
        ResourceLocation oversized = ResourceLocation.tryParse("a:" + "x".repeat(127));

        assertThrows(IllegalArgumentException.class, () -> new RouteDefinition(
                2, valid.id(), valid.from(), valid.to(), valid.distanceUnits(), valid.bidirectional()
        ));
        assertThrows(IllegalArgumentException.class, () -> new RouteDefinition(
                RouteLimits.SCHEMA_VERSION,
                oversized,
                valid.from(),
                valid.to(),
                valid.distanceUnits(),
                valid.bidirectional()
        ));
        assertThrows(IllegalArgumentException.class, () -> new RouteDefinition(
                RouteLimits.SCHEMA_VERSION,
                valid.id(),
                valid.from(),
                valid.to(),
                RouteLimits.MAX_DISTANCE_UNITS + 1,
                valid.bidirectional()
        ));
    }

    static RouteDefinition route(
            String id,
            String fromBody,
            String toBody,
            int distance,
            boolean bidirectional
    ) {
        return new RouteDefinition(
                RouteLimits.SCHEMA_VERSION,
                ModIdentity.id(id),
                RouteAnchor.bodySurface(ModIdentity.id(fromBody)),
                RouteAnchor.bodySurface(ModIdentity.id(toBody)),
                distance,
                bidirectional
        );
    }

    private static void assertRejected(String json) {
        assertTrue(RouteDefinition.CODEC.parse(
                JsonOps.INSTANCE,
                JsonParser.parseString(json)
        ).error().isPresent(), json);
    }
}
