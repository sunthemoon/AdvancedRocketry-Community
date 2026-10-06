package io.github.sunthemoon.advancedrocketrycommunity.datagen;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Committed resource composition only; not client readability or native interaction evidence. */
class V180SealDetectorResourcesTest {
    private static final Path ROOT = Path.of("src/generated/v1.8/resources");

    @Test
    void committedRecipeUnlockAndModelMatchTheirProvider() throws IOException {
        assertEquals(1, V180SealDetectorData.clientFiles().size());
        assertEquals(2, V180SealDetectorData.serverFiles().size());
        matches(V180SealDetectorData.clientFiles());
        matches(V180SealDetectorData.serverFiles());
    }

    @Test
    void committedOriginalIconMatchesDeterministicEncoder() throws IOException {
        byte[] bytes = Files.readAllBytes(ROOT.resolve(V180SealDetectorData.TEXTURE));
        assertTrue(bytes.length < 4096);
        assertArrayEquals(V180SealDetectorData.texture(), bytes);
    }

    @Test
    void bothCommittedLocalesContainAllTwelveProviderValues() throws IOException {
        for (boolean chinese : new boolean[] {false, true}) {
            String locale = chinese ? "zh_cn" : "en_us";
            JsonObject committed = json("assets/advancedrocketrycommunity_v180/lang/" + locale + ".json");
            Map<String, String> entries = V180SealDetectorLanguage.translations(chinese);
            assertEquals(12, entries.size());
            for (var entry : entries.entrySet()) {
                assertTrue(committed.has(entry.getKey()), entry.getKey());
                assertEquals(entry.getValue(), committed.get(entry.getKey()).getAsString(), entry.getKey());
            }
        }
    }

    private static void matches(Map<String, JsonObject> files) throws IOException {
        for (var file : files.entrySet()) {
            assertEquals(file.getValue(), json(file.getKey()), file.getKey());
        }
    }

    private static JsonObject json(String relative) throws IOException {
        return JsonParser.parseString(Files.readString(ROOT.resolve(relative))).getAsJsonObject();
    }
}
