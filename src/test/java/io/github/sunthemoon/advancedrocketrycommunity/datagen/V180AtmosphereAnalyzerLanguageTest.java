package io.github.sunthemoon.advancedrocketrycommunity.datagen;

import static org.junit.jupiter.api.Assertions.*;
import io.github.sunthemoon.advancedrocketrycommunity.atmosphere.instrument.AnalyzerReading;
import io.github.sunthemoon.advancedrocketrycommunity.atmosphere.instrument.AtmosphereAnalyzerFeedback;
import java.util.Locale;
import org.junit.jupiter.api.Test;

class V180AtmosphereAnalyzerLanguageTest {
    @Test void bilingualMapsHaveTheSameCompleteFixedKeySet() {
        var english = V180AtmosphereAnalyzerLanguage.translations(false);
        var chinese = V180AtmosphereAnalyzerLanguage.translations(true);
        assertEquals(english.keySet(), chinese.keySet());
        assertEquals(15, english.size());
        assertEquals("Atmosphere Analyzer", english.get("item.advancedrocketrycommunity.atmosphere_analyzer"));
        assertEquals("Space", english.get("body.advancedrocketrycommunity.space"));
        for (var state : AnalyzerReading.State.values()) {
            String key = AtmosphereAnalyzerFeedback.PREFIX + "state." + state.name().toLowerCase(Locale.ROOT);
            assertFalse(english.get(key).isBlank()); assertFalse(chinese.get(key).isBlank());
        }
        for (boolean locale : new boolean[] {false, true}) {
            String format = V180AtmosphereAnalyzerLanguage.translations(locale).get(AtmosphereAnalyzerFeedback.PREFIX + "reading");
            assertEquals(6, format.split("%s", -1).length - 1);
            assertFalse(format.contains("%d")); assertFalse(format.contains("EXEMPT"));
        }
    }

    @Test void languageDoesNotRenameExistingBodiesOrInventOxygenPercentageOrPressureUnits() {
        for (boolean locale : new boolean[] {false, true}) {
            var keys = V180AtmosphereAnalyzerLanguage.translations(locale);
            assertFalse(keys.containsKey("body.advancedrocketrycommunity.earth"));
            assertFalse(keys.containsKey("body.advancedrocketrycommunity.moon"));
            assertTrue(keys.values().stream().noneMatch(value -> value.contains("atm") || value.contains("kPa") || value.contains("O2")));
        }
    }
}
