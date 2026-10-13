package io.github.sunthemoon.advancedrocketrycommunity.datagen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.mission.SatelliteOperationCode;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * W21b review F4: every satellite operation code has a translation in the committed DataGen output of the
 * version that added it (v0.8, v1.6 or v1.8), in both languages.
 */
class SatelliteOperationCodeLanguageTest {
    private static final List<Path> LANGUAGE_DIRECTORIES = List.of(
            Path.of("src/generated/v0.8/resources/assets/advancedrocketrycommunity_v080/lang"),
            Path.of("src/generated/v1.6/resources/assets/advancedrocketrycommunity_v160/lang"),
            Path.of("src/generated/v1.8/resources/assets/advancedrocketrycommunity_v180/lang"));

    @Test
    void everyCodeIsTranslatedOnceInBothLanguages() throws Exception {
        for (String locale : List.of("en_us", "zh_cn")) {
            List<JsonObject> files = new java.util.ArrayList<>();
            for (Path directory : LANGUAGE_DIRECTORIES) {
                files.add(JsonParser.parseString(Files.readString(directory.resolve(locale + ".json"),
                        StandardCharsets.UTF_8)).getAsJsonObject());
            }
            for (SatelliteOperationCode code : SatelliteOperationCode.values()) {
                String key = code.translationKey();
                long owners = files.stream().filter(file -> file.has(key)).count();
                assertEquals(1, owners, locale + " " + key);
                assertFalse(files.stream().filter(file -> file.has(key)).findFirst().orElseThrow()
                        .get(key).getAsString().isBlank(), locale + " " + key);
            }
            assertTrue(files.get(2).has(SatelliteOperationCode.OFFLINE_REPAIR_REQUIRED.translationKey()), locale);
        }
    }
}
