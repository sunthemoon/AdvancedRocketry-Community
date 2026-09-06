package io.github.sunthemoon.advancedrocketrycommunity.travel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import io.github.sunthemoon.advancedrocketrycommunity.travel.model.TravelTarget;
import io.github.sunthemoon.advancedrocketrycommunity.travel.persistence.TravelTargetNbtCodec;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class TravelTargetCodecTest {
    private static final UUID STATION_ID = UUID.fromString("123e4567-e89b-42d3-a456-426614174000");
    private static final UUID MISSION_ID = UUID.fromString("123e4567-e89b-42d3-b456-426614174001");

    @BeforeAll
    static void bootstrapMinecraftRegistries() {
        MinecraftBootstrap.initialize();
    }

    @Test
    void allTargetKindsRoundTripThroughJsonAndNbt() {
        List<TravelTarget> targets = List.of(
                new TravelTarget.BodySurface(ModIdentity.id("earth")),
                new TravelTarget.Orbit(ModIdentity.id("moon")),
                new TravelTarget.Station(STATION_ID),
                new TravelTarget.Mission(MISSION_ID)
        );

        for (TravelTarget target : targets) {
            JsonElement json = encodeJson(target);
            TravelTarget jsonDecoded = TravelTarget.CODEC.parse(JsonOps.INSTANCE, json)
                    .getOrThrow(false, message -> {
                        throw new AssertionError(message);
                    });
            assertEquals(target, jsonDecoded);

            CompoundTag nbt = TravelTargetNbtCodec.encode(target)
                    .getOrThrow(false, message -> {
                        throw new AssertionError(message);
                    });
            TravelTarget nbtDecoded = TravelTargetNbtCodec.decode(nbt)
                    .getOrThrow(false, message -> {
                        throw new AssertionError(message);
                    });
            assertEquals(target, nbtDecoded);
        }
    }

    @Test
    void encodedShapesContainOnlyTheIdentityForTheirKind() {
        JsonElement body = encodeJson(new TravelTarget.BodySurface(ModIdentity.id("earth")));
        JsonElement station = encodeJson(new TravelTarget.Station(STATION_ID));

        assertEquals(3, body.getAsJsonObject().size());
        assertTrue(body.getAsJsonObject().has("body_id"));
        assertEquals(3, station.getAsJsonObject().size());
        assertTrue(station.getAsJsonObject().has("instance_id"));
    }

    @Test
    void futureSchemaAndUnknownTypeAreRejected() {
        assertRejected("""
                {"schema_version":2,"type":"advancedrocketrycommunity:body_surface","body_id":"advancedrocketrycommunity:earth"}
                """);
        assertRejected("""
                {"schema_version":1,"type":"advancedrocketrycommunity:warp","body_id":"advancedrocketrycommunity:earth"}
                """);
    }

    @Test
    void missingAdditionalAndMixedIdentityFieldsAreRejected() {
        assertRejected("""
                {"schema_version":1,"type":"advancedrocketrycommunity:body_surface"}
                """);
        assertRejected("""
                {"schema_version":1,"type":"advancedrocketrycommunity:body_surface","body_id":"advancedrocketrycommunity:earth","extra":true}
                """);
        assertRejected("""
                {"schema_version":1,"type":"advancedrocketrycommunity:station","body_id":"advancedrocketrycommunity:earth","instance_id":"123e4567-e89b-42d3-a456-426614174000"}
                """);
    }

    @Test
    void malformedAndNonCanonicalUuidsAreRejected() {
        assertRejected("""
                {"schema_version":1,"type":"advancedrocketrycommunity:station","instance_id":"not-a-uuid"}
                """);
        assertRejected("""
                {"schema_version":1,"type":"advancedrocketrycommunity:station","instance_id":"123E4567-E89B-42D3-A456-426614174000"}
                """);
        assertRejected("""
                {"schema_version":1,"type":"advancedrocketrycommunity:station","instance_id":"00000000-0000-0000-0000-000000000000"}
                """);
    }

    @Test
    void identifierLengthBoundaryIsEnforced() {
        ResourceLocation maximum = ResourceLocation.tryParse("a:" + "x".repeat(126));
        assertEquals(128, maximum.toString().length());
        assertInstanceOf(TravelTarget.BodySurface.class, new TravelTarget.BodySurface(maximum));

        ResourceLocation oversized = ResourceLocation.tryParse("a:" + "x".repeat(127));
        assertEquals(129, oversized.toString().length());
        assertThrows(IllegalArgumentException.class, () -> new TravelTarget.BodySurface(oversized));
    }

    @Test
    void maximumCanonicalTargetFitsJsonAndNbtBounds() {
        TravelTarget target = new TravelTarget.BodySurface(
                ResourceLocation.tryParse("a:" + "x".repeat(126))
        );

        int jsonBytes = encodeJson(target).toString().getBytes(StandardCharsets.UTF_8).length;
        CompoundTag nbt = TravelTargetNbtCodec.encode(target)
                .getOrThrow(false, message -> {
                    throw new AssertionError(message);
                });

        assertTrue(jsonBytes <= 256, () -> "Canonical JSON used " + jsonBytes + " bytes");
        assertTrue(
                TravelTargetNbtCodec.encodedBytes(nbt) <= TravelTargetNbtCodec.MAX_TARGET_NBT_BYTES
        );
    }

    @Test
    void nbtRejectsWrongRootAdditionalFieldsAndOversizedPayloads() {
        assertTrue(TravelTargetNbtCodec.decode(StringTag.valueOf("target")).error().isPresent());

        CompoundTag additional = TravelTargetNbtCodec.encode(
                new TravelTarget.BodySurface(ModIdentity.id("earth"))
        ).getOrThrow(false, message -> {
            throw new AssertionError(message);
        });
        additional.putBoolean("extra", true);
        assertTrue(TravelTargetNbtCodec.decode(additional).error().isPresent());

        CompoundTag oversized = new CompoundTag();
        oversized.putInt("schema_version", 1);
        oversized.putString("type", TravelTarget.BODY_SURFACE_TYPE.toString());
        oversized.putString("body_id", ModIdentity.id("earth").toString());
        oversized.putString("padding", "x".repeat(256));
        assertTrue(TravelTargetNbtCodec.decode(oversized).error().isPresent());
    }

    private static JsonElement encodeJson(TravelTarget target) {
        return TravelTarget.CODEC.encodeStart(JsonOps.INSTANCE, target)
                .getOrThrow(false, message -> {
                    throw new AssertionError(message);
                });
    }

    private static void assertRejected(String json) {
        assertTrue(
                TravelTarget.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString(json)).error().isPresent(),
                json
        );
    }
}
