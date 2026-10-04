package io.github.sunthemoon.advancedrocketrycommunity.classiccomponent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import com.google.gson.JsonParser;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

class MotorLanguageResourcesTest {
    @Test
    void casingAndMotorLabelsUseUnambiguousBilingualKeys() throws Exception {
        for (String locale : List.of("en_us", "zh_cn")) {
            var language = JsonParser.parseString(Files.readString(Path.of(
                    "src/generated/v1.8/resources/assets/advancedrocketrycommunity_v180/lang/" + locale + ".json")))
                    .getAsJsonObject();
            assertFalse(language.has("block.advancedrocketrycommunity.endgame_casing"));
            assertEquals(locale.equals("en_us") ? "Advanced Machine Casing" : "高级机器机壳",
                    language.get("block.advancedrocketrycommunity.advanced_machine_casing").getAsString());
            for (MotorDefinition motor : MotorDefinition.values()) {
                assertEquals(locale.equals("en_us") ? motor.english() : motor.chinese(),
                        language.get("block.advancedrocketrycommunity." + motor.id()).getAsString());
            }
        }
    }
}
