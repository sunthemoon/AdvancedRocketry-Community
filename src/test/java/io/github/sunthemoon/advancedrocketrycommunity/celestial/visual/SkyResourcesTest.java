package io.github.sunthemoon.advancedrocketrycommunity.celestial.visual;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import net.minecraft.core.registries.BuiltInRegistries;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class SkyResourcesTest {
    @BeforeAll static void bootstrap() { MinecraftBootstrap.initialize(); }

    @Test void historicalTypesChangeOnlyEffectsAndCurrentTypesAlsoChangeNatural() throws Exception {
        for (String type : List.of("moon", "space")) {
            String path = "data/advancedrocketrycommunity/dimension_type/" + type + ".json";
            var old = JsonParser.parseString(Files.readString(Path.of("src/generated/v0.3/resources/" + path))).getAsJsonObject();
            var actual = resource(path);
            var historical = JsonParser.parseString(Files.readString(Path.of("src/generated/v1.4/resources/" + path)))
                    .getAsJsonObject();
            assertEquals((type.equals("space") ? SkyProfiles.SPACE_EFFECTS : SkyProfiles.SURFACE_EFFECTS).toString(),
                    actual.get("effects").getAsString());
            old.remove("effects");
            actual.remove("effects");
            assertEquals((type.equals("space") ? SkyProfiles.SPACE_EFFECTS : SkyProfiles.SURFACE_EFFECTS).toString(),
                    historical.remove("effects").getAsString());
            assertEquals(old, historical, "The immutable v1.4 type must still change only effects");
            assertFalse(old.get("natural").getAsBoolean());
            assertTrue(actual.get("natural").getAsBoolean());
            old.addProperty("natural", true); // W37B: the current type additionally permits native bed rules.
            assertEquals(old, actual);
        }
        for (String type : List.of("mars", "venus")) {
            assertEquals(SkyProfiles.SURFACE_EFFECTS.toString(), resource("data/advancedrocketrycommunity/dimension_type/"
                    + type + ".json").get("effects").getAsString());
        }
    }

    @Test void generatedProfilesMatchOriginalInputsAndSoundReferencesResolve() throws Exception {
        for (var entry : SkyProfiles.builtins().entrySet()) {
            var json = resource("assets/" + entry.getKey().getNamespace() + "/" + SkyProfile.DIRECTORY + "/"
                    + entry.getKey().getPath() + ".json");
            assertEquals(entry.getValue(), SkyProfile.decode(json));
            entry.getValue().ambientSound().ifPresent(id -> assertTrue(BuiltInRegistries.SOUND_EVENT.containsKey(id), id.toString()));
        }
    }

    private static JsonObject resource(String path) throws Exception {
        try (var input = SkyResourcesTest.class.getClassLoader().getResourceAsStream(path)) {
            assertNotNull(input, path);
            return JsonParser.parseString(new String(input.readAllBytes(), StandardCharsets.UTF_8)).getAsJsonObject();
        }
    }
}
