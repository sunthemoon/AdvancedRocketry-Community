package io.github.sunthemoon.advancedrocketrycommunity.datagen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.sunthemoon.advancedrocketrycommunity.model.api.equipment.ModuleKind;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** ADR-066 section 7.3: every new presentation string exists in English and Chinese, in the committed output. */
class V180PresentationLanguageTest {
    private static final Path LANG = Path.of("src", "generated", "v1.8", "resources", "assets",
            "advancedrocketrycommunity_v180", "lang");

    @Test
    void bothLanguagesHaveTheSameKeysAndPlaceholders() {
        Map<String, String> english = V180PresentationLanguage.translations(false);
        Map<String, String> chinese = V180PresentationLanguage.translations(true);
        assertEquals(english.keySet(), chinese.keySet());
        for (String key : english.keySet()) {
            assertFalse(english.get(key).isBlank(), key);
            assertFalse(chinese.get(key).isBlank(), key);
            assertEquals(placeholders(english.get(key)), placeholders(chinese.get(key)), key);
        }
    }

    @Test
    void everyModuleKindHasAHudLabel() {
        Map<String, String> english = V180PresentationLanguage.translations(false);
        for (ModuleKind kind : ModuleKind.values()) {
            assertTrue(english.containsKey("hud.advancedrocketrycommunity.suit.module." + kind.stableId()), kind.name());
        }
        assertEquals(2, placeholders(english.get("hud.advancedrocketrycommunity.hydrogen")));
        assertEquals(3, placeholders(english.get("hud.advancedrocketrycommunity.rocket")));
        assertEquals(1, placeholders(english.get("hud.advancedrocketrycommunity.suit")));
    }

    @Test
    void theCommittedLanguageFilesCarryEveryEntry() throws IOException {
        for (boolean chinese : new boolean[] {false, true}) {
            JsonObject file = JsonParser.parseString(Files.readString(
                    LANG.resolve(chinese ? "zh_cn.json" : "en_us.json"), StandardCharsets.UTF_8)).getAsJsonObject();
            V180PresentationLanguage.translations(chinese).forEach((key, value) ->
                    assertEquals(value, file.has(key) ? file.get(key).getAsString() : null, key));
        }
    }

    private static int placeholders(String value) {
        int count = 0;
        for (int i = value.indexOf("%s"); i >= 0; i = value.indexOf("%s", i + 2)) {
            count++;
        }
        return count;
    }
}
