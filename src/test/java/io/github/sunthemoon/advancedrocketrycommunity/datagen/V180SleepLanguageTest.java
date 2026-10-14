package io.github.sunthemoon.advancedrocketrycommunity.datagen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.sunthemoon.advancedrocketrycommunity.atmosphere.life.SleepRespawnRules.RespawnOutcome;
import io.github.sunthemoon.advancedrocketrycommunity.atmosphere.life.SleepRespawnRules.SleepRefusal;
import io.github.sunthemoon.advancedrocketrycommunity.atmosphere.server.SleepRespawnService;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** W37: every sleep refusal and planet-respawn fallback has a message in both languages, in the committed output. */
class V180SleepLanguageTest {
    private static final Path LANG = Path.of("src", "generated", "v1.8", "resources", "assets",
            "advancedrocketrycommunity_v180", "lang");

    @BeforeAll
    static void bootstrap() {
        MinecraftBootstrap.initialize();
    }

    @Test
    void everyRefusalAndFallbackHasBothLanguages() {
        Map<String, String> english = V180SleepLanguage.translations(false);
        Map<String, String> chinese = V180SleepLanguage.translations(true);
        assertEquals(english.keySet(), chinese.keySet());
        for (SleepRefusal refusal : SleepRefusal.values()) {
            assertEquals(refusal != SleepRefusal.NONE, english.containsKey(SleepRespawnService.sleepKey(refusal)),
                    refusal.name());
        }
        for (RespawnOutcome outcome : RespawnOutcome.values()) {
            assertEquals(!outcome.accepted(), english.containsKey(SleepRespawnService.respawnKey(outcome)),
                    outcome.name());
        }
        // W37H F6: one generic fallback message for a kept point whose stored reason this version does not know.
        assertTrue(english.containsKey(SleepRespawnService.genericRespawnKey()));
        assertEquals(SleepRefusal.values().length - 1 + RespawnOutcome.values().length - 1 + 1, english.size());
        english.forEach((key, value) -> {
            assertTrue(key.startsWith("message.advancedrocketrycommunity."), key);
            assertFalse(value.isBlank() || value.contains("%"), key);
            assertFalse(chinese.get(key).isBlank() || chinese.get(key).contains("%"), key);
        });
    }

    @Test
    void theCommittedLanguageFilesCarryEveryEntry() throws IOException {
        for (boolean chinese : new boolean[] {false, true}) {
            JsonObject file = JsonParser.parseString(Files.readString(
                    LANG.resolve(chinese ? "zh_cn.json" : "en_us.json"), StandardCharsets.UTF_8)).getAsJsonObject();
            V180SleepLanguage.translations(chinese).forEach((key, value) ->
                    assertEquals(value, file.has(key) ? file.get(key).getAsString() : null, key));
        }
    }
}
