package io.github.sunthemoon.advancedrocketrycommunity.atmosphere.instrument;

import static org.junit.jupiter.api.Assertions.*;
import java.util.Optional;
import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.Test;

class AtmosphereAnalyzerFeedbackTest {
    @Test void foreignBodyHasStableBoundedFallbackNotClientLookup() {
        var json = Component.Serializer.toJsonTree(AtmosphereAnalyzerFeedback.body("example:worlds/hostile")).getAsJsonObject();
        assertEquals("body.example.worlds.hostile", json.get("translate").getAsString());
        assertEquals("example:worlds/hostile", json.get("fallback").getAsString());
        assertThrows(IllegalArgumentException.class, () -> AtmosphereAnalyzerFeedback.body("unsafe\ntext"));
    }

    @Test void readingKeepsExactFiniteNumbersAndExplicitAmbientAndControlFields() {
        var reading = new AnalyzerReading(AnalyzerReading.State.BREATHABLE, Optional.of("test:earth"),
                Optional.of(AnalyzerReading.Locus.ORBIT), Optional.of("test:space"),
                Optional.of(new AnalyzerReading.Ambient(0.012345678901234D, 301.987654321D)), true);
        var json = Component.Serializer.toJsonTree(AtmosphereAnalyzerFeedback.message(reading)).getAsJsonObject();
        assertEquals(AtmosphereAnalyzerFeedback.PREFIX + "reading", json.get("translate").getAsString());
        var args = json.getAsJsonArray("with");
        assertEquals(6, args.size());
        assertEquals(Double.toString(reading.ambient().orElseThrow().pressure()), args.get(3).getAsString());
        assertEquals(Double.toString(reading.ambient().orElseThrow().temperatureKelvin()), args.get(4).getAsString());
        assertTrue(args.get(2).toString().contains("ambient_body"));
        assertTrue(args.get(5).toString().contains("supplied"));
    }

    @Test void disabledAndUnavailableHaveOnlyTheFixedLocalizedStateArgument() {
        for (var reading : new AnalyzerReading[] {AnalyzerReading.disabled(), AnalyzerReading.unavailable()}) {
            var json = Component.Serializer.toJsonTree(AtmosphereAnalyzerFeedback.message(reading)).getAsJsonObject();
            assertEquals(AtmosphereAnalyzerFeedback.PREFIX + "unavailable", json.get("translate").getAsString());
            assertEquals(1, json.getAsJsonArray("with").size());
            assertTrue(json.getAsJsonArray("with").get(0).toString().contains(reading.state().name().toLowerCase(java.util.Locale.ROOT)));
        }
    }
}
