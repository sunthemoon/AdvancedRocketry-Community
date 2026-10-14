package io.github.sunthemoon.advancedrocketrycommunity.datagen;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import net.minecraft.world.level.dimension.DimensionType;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class V180NaturalDimensionTypesTest {
    private static final List<String> TYPES = List.of("moon", "space", "mars", "venus", "tau_ceti_f", "tau_ceti_g");

    @BeforeAll
    static void bootstrap() { MinecraftBootstrap.initialize(); }

    @Test
    void sixCurrentFilesDifferFromTheirOldDefinitionsOnlyInNatural() throws IOException {
        for (String name : TYPES) {
            JsonObject current = read("v1.8", name);
            // The original two exoplanet types have exactly the old Mars fields (cyclic, surface effects, no raids).
            JsonObject old = read("v1.4", name.startsWith("tau_ceti_") ? "mars" : name);
            assertFalse(old.get("natural").getAsBoolean(), name);
            assertTrue(current.get("natural").getAsBoolean(), name);
            old.addProperty("natural", true);
            assertEquals(old, current, "A field other than natural changed: " + name);
            DimensionType decoded = DimensionType.DIRECT_CODEC.parse(JsonOps.INSTANCE, current).result().orElseThrow();
            assertTrue(decoded.natural(), name);
            var encoded = DimensionType.DIRECT_CODEC.encodeStart(JsonOps.INSTANCE, decoded).result().orElseThrow();
            // Compare serialized JSON numbers, not Gson's Float-vs-LazilyParsedNumber in-memory equality (0.1F).
            assertEquals(current, JsonParser.parseString(encoded.toString()), name);
            assertEquals(decoded, DimensionType.DIRECT_CODEC.parse(JsonOps.INSTANCE, encoded).result().orElseThrow());
        }
    }

    private static JsonObject read(String version, String name) throws IOException {
        return JsonParser.parseString(Files.readString(Path.of("src/generated", version, "resources/data",
                "advancedrocketrycommunity/dimension_type", name + ".json"))).getAsJsonObject();
    }
}
