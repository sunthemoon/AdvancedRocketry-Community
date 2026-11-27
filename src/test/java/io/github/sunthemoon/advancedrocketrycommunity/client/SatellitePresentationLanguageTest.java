package io.github.sunthemoon.advancedrocketrycommunity.client;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class SatellitePresentationLanguageTest {
    private static final String PREFIX = "screen.advancedrocketrycommunity.satellite.";

    private static JsonObject language(String locale) throws Exception {
        return JsonParser.parseString(Files.readString(Path.of(
                "src/generated/v1.8/resources/assets/advancedrocketrycommunity_v180/lang", locale + ".json"))).getAsJsonObject();
    }

    @Test void shortSelectorFitsAndActionCaseMatchesExistingControls() throws Exception {
        for (String locale : new String[] {"en_us", "zh_cn"}) {
            var language = language(locale);
            String selector = language.get(PREFIX + "delivery.none_short").getAsString();
            assertTrue(width(selector) <= 82, locale + " " + selector);
            if (locale.equals("en_us")) {
                assertEquals("BUILD", language.get(PREFIX + "assemble_short").getAsString());
                assertEquals("LAUNCH", language.get(PREFIX + "launch_short").getAsString());
            } else {
                assertEquals("组装", language.get(PREFIX + "assemble_short").getAsString());
                assertEquals("发射", language.get(PREFIX + "launch_short").getAsString());
            }
        }
    }

    private static int width(String value) {
        return value.codePoints().map(c -> c >= 0x2E80 ? 9 : "i!.,:;'|".indexOf(c) >= 0 ? 2 : "l`".indexOf(c) >= 0 ? 3
                : "tI[] ".indexOf(c) >= 0 ? 4 : "fk(){}<>\"*".indexOf(c) >= 0 ? 5 : 6).sum();
    }
}
