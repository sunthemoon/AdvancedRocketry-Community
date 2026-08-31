package io.github.sunthemoon.advancedrocketrycommunity.celestial;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.model.AtmosphereDefinition;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.model.CelestialBodyDefinition;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.model.OrbitDefinition;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import java.util.Optional;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class CelestialDefinitionCodecTest {
    @BeforeAll
    static void bootstrapMinecraftRegistries() {
        MinecraftBootstrap.initialize();
    }

    @Test
    void canonicalDefinitionRoundTrips() {
        CelestialBodyDefinition moon = moonDefinition();

        JsonElement encoded = CelestialBodyDefinition.CODEC.encodeStart(JsonOps.INSTANCE, moon)
                .getOrThrow(false, message -> {
                    throw new AssertionError(message);
                });
        CelestialBodyDefinition decoded = CelestialBodyDefinition.CODEC.parse(JsonOps.INSTANCE, encoded)
                .getOrThrow(false, message -> {
                    throw new AssertionError(message);
                });

        assertEquals(moon, decoded);
        assertTrue(encoded.getAsJsonObject().get("level").getAsString().contains("moon"));
    }

    @Test
    void invalidGravityIsRejected() {
        JsonElement json = CelestialBodyDefinition.CODEC.encodeStart(JsonOps.INSTANCE, moonDefinition())
                .getOrThrow(false, message -> {
                    throw new AssertionError(message);
                });
        json.getAsJsonObject().addProperty("gravity_multiplier", 4.01D);

        assertTrue(CelestialBodyDefinition.CODEC.parse(JsonOps.INSTANCE, json).error().isPresent());
    }

    @Test
    void invalidResourceLocationIsRejected() {
        JsonElement json = JsonParser.parseString("""
                {
                  "id": "Not A Resource Location",
                  "level": "minecraft:overworld",
                  "gravity_multiplier": 1.0,
                  "atmosphere": {
                    "pressure": 1.0,
                    "breathable": true,
                    "temperature_kelvin": 288.0,
                    "profile": "advancedrocketrycommunity:earth"
                  },
                  "orbit": {"distance": 0, "period_ticks": 0, "inclination_degrees": 0.0},
                  "visual_profile": "advancedrocketrycommunity:earth"
                }
                """);

        assertTrue(CelestialBodyDefinition.CODEC.parse(JsonOps.INSTANCE, json).error().isPresent());
    }

    @Test
    void breathableVacuumIsRejected() {
        JsonElement json = AtmosphereDefinition.CODEC.encodeStart(
                        JsonOps.INSTANCE,
                        new AtmosphereDefinition(1.0D, true, 288.0D, ModIdentity.id("earth"))
                )
                .getOrThrow(false, message -> {
                    throw new AssertionError(message);
                });
        json.getAsJsonObject().addProperty("pressure", 0.0D);

        assertTrue(AtmosphereDefinition.CODEC.parse(JsonOps.INSTANCE, json).error().isPresent());
    }

    @Test
    void zeroOrbitMustUseBothZeroFields() {
        JsonElement json = JsonParser.parseString("""
                {"distance": 0, "period_ticks": 200, "inclination_degrees": 0.0}
                """);

        assertTrue(OrbitDefinition.CODEC.parse(JsonOps.INSTANCE, json).error().isPresent());
    }

    static CelestialBodyDefinition earthDefinition() {
        return new CelestialBodyDefinition(
                CelestialIds.EARTH_ID,
                Optional.empty(),
                Level.OVERWORLD,
                1.0D,
                new AtmosphereDefinition(1.0D, true, 288.0D, ModIdentity.id("earth")),
                new OrbitDefinition(0L, 0L, 0.0D),
                ModIdentity.id("earth")
        );
    }

    static CelestialBodyDefinition moonDefinition() {
        return new CelestialBodyDefinition(
                CelestialIds.MOON_ID,
                Optional.of(CelestialIds.EARTH_ID),
                CelestialIds.MOON_LEVEL,
                0.165D,
                new AtmosphereDefinition(0.0D, false, 220.0D, ModIdentity.id("vacuum")),
                new OrbitDefinition(384_400L, 2_360_591L, 5.145D),
                ModIdentity.id("moon")
        );
    }

    static CelestialBodyDefinition spaceDefinition() {
        return new CelestialBodyDefinition(
                CelestialIds.SPACE_ID,
                Optional.of(CelestialIds.EARTH_ID),
                CelestialIds.SPACE_LEVEL,
                0.0D,
                new AtmosphereDefinition(0.0D, false, 3.0D, ModIdentity.id("vacuum")),
                new OrbitDefinition(1L, 1L, 0.0D),
                ModIdentity.id("space")
        );
    }
}
