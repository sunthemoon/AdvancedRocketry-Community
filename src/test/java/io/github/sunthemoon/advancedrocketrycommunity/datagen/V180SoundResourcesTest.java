package io.github.sunthemoon.advancedrocketrycommunity.datagen;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.sunthemoon.advancedrocketrycommunity.sound.ClassicSound;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * The committed v1.8 sound resources (ADR-066 sections 7.1 and 7.3): one {@code sounds.json} entry for every
 * classic sound event, each naming its own NEW file, and every committed file byte-identical to its generator.
 */
class V180SoundResourcesTest {
    private static final Path ASSETS = Path.of("src", "generated", "v1.8", "resources", "assets");
    private static final Path DEFINITIONS = ASSETS.resolve(ClassicSound.DEFINITION_NAMESPACE).resolve("sounds.json");
    private static final Path FILES = ASSETS.resolve("advancedrocketrycommunity").resolve("sounds");

    @Test
    void everyClassicEventHasExactlyOneDefinitionInTheV180Namespace() throws IOException {
        JsonObject definitions = JsonParser.parseString(Files.readString(DEFINITIONS, StandardCharsets.UTF_8))
                .getAsJsonObject();
        Set<String> expected = Arrays.stream(ClassicSound.values()).map(ClassicSound::id)
                .collect(Collectors.toCollection(TreeSet::new));
        assertEquals(expected, new TreeSet<>(definitions.keySet()));
        for (ClassicSound sound : ClassicSound.values()) {
            assertEquals(ClassicSound.DEFINITION_NAMESPACE, sound.location().getNamespace());
            assertEquals(sound.id(), sound.location().getPath());
            assertEquals("advancedrocketrycommunity", sound.registryId().getNamespace());
            assertEquals(sound.id(), sound.registryId().getPath());
            JsonObject definition = definitions.getAsJsonObject(sound.id());
            assertEquals(sound.subtitle(), definition.get("subtitle").getAsString());
            JsonArray sounds = definition.getAsJsonArray("sounds");
            assertEquals(1, sounds.size(), sound.id());
            JsonElement entry = sounds.get(0);
            String name = entry.isJsonPrimitive() ? entry.getAsString() : entry.getAsJsonObject().get("name").getAsString();
            assertEquals(sound.file().toString(), name);
            int distance = entry.isJsonObject() && entry.getAsJsonObject().has("attenuation_distance")
                    ? entry.getAsJsonObject().get("attenuation_distance").getAsInt() : 16;
            assertEquals(sound.range(), distance, sound.id());
            assertTrue(!entry.isJsonObject() || !entry.getAsJsonObject().has("stream")
                    || !entry.getAsJsonObject().get("stream").getAsBoolean(), sound.id());
        }
    }

    @Test
    void everyCommittedFileIsTheGeneratorsOutputAndNothingElseIsThere() throws IOException {
        Set<String> expected = Arrays.stream(ClassicSound.values()).map(sound -> sound.id() + ".ogg")
                .collect(Collectors.toCollection(TreeSet::new));
        try (Stream<Path> files = Files.list(FILES)) {
            assertEquals(expected, files.map(path -> path.getFileName().toString())
                    .collect(Collectors.toCollection(TreeSet::new)));
        }
        for (ClassicSound sound : ClassicSound.values()) {
            assertArrayEquals(V180SoundArt.ogg(sound), Files.readAllBytes(FILES.resolve(sound.id() + ".ogg")),
                    sound.id());
        }
    }
}
