package io.github.sunthemoon.advancedrocketrycommunity.datagen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonParser;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.validation.RocketValidationCode;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/** W85: the DISASSEMBLY_PENDING status text exists in English and Chinese in the generated v1.8 language files. */
class V180LandedDisassemblyLanguageTest {
    private static final Path GENERATED = Path.of("src/generated/v1.8/resources/assets/advancedrocketrycommunity_v180/lang");

    @Test void coldPreparationHasItsOwnBilingualFeedback() {
        String key = "message.advancedrocketrycommunity.rocket.disassembly_preparing";
        var english = V180LandedDisassemblyLanguage.entries(false).get(key);
        var chinese = V180LandedDisassemblyLanguage.entries(true).get(key);
        assertTrue(english != null && !english.isBlank());
        assertTrue(chinese != null && !chinese.isBlank());
        assertNotEquals(english, chinese);
    }

    @Test
    void pendingStatusIsGeneratedInBothLanguages() throws Exception {
        String key = RocketValidationCode.DISASSEMBLY_PENDING.translationKey();
        assertEquals("validation.advancedrocketrycommunity.rocket.disassembly_pending", key);
        String english = V180LandedDisassemblyLanguage.entries(false).get(key);
        String chinese = V180LandedDisassemblyLanguage.entries(true).get(key);
        assertNotEquals(english, chinese);
        assertFalse(english.isBlank() || chinese.isBlank() || english.contains("%") || chinese.contains("%"));
        assertTrue(chinese.codePoints().anyMatch(c -> c >= 0x4E00 && c <= 0x9FFF));
        for (String locale : new String[] {"en_us", "zh_cn"}) {
            var lang = JsonParser.parseString(Files.readString(GENERATED.resolve(locale + ".json"),
                    StandardCharsets.UTF_8)).getAsJsonObject();
            assertEquals(locale.equals("zh_cn") ? chinese : english, lang.get(key).getAsString(), locale);
        }
    }
}
