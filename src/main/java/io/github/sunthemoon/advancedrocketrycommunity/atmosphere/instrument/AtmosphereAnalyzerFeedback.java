package io.github.sunthemoon.advancedrocketrycommunity.atmosphere.instrument;

import java.util.Locale;
import net.minecraft.network.chat.Component;

/** Fixed localized chat fields; it never overrides the independent life-support hazard action bar. */
public final class AtmosphereAnalyzerFeedback {
    public static final String PREFIX = "message.advancedrocketrycommunity.atmosphere_analyzer.";

    public static Component message(AnalyzerReading reading) {
        Component state = Component.translatable(PREFIX + "state." + reading.state().name().toLowerCase(Locale.ROOT));
        if (reading.ambient().isEmpty()) { return Component.translatable(PREFIX + "unavailable", state); }
        var ambient = reading.ambient().orElseThrow();
        String body = reading.bodyId().orElseThrow();
        Component location = Component.translatable(PREFIX + "location", body(body),
                Component.translatable(PREFIX + "locus." + reading.locus().orElseThrow().name().toLowerCase(Locale.ROOT)));
        Component ambientLabel = body.equals(reading.ambientBodyId().orElseThrow()) ? body(body)
                : Component.translatable(PREFIX + "ambient_body", body(reading.ambientBodyId().orElseThrow()));
        return Component.translatable(PREFIX + "reading", location, state, ambientLabel,
                Double.toString(ambient.pressure()), Double.toString(ambient.temperatureKelvin()),
                Component.translatable(PREFIX + (reading.supplied() ? "supplied" : "not_supplied")));
    }

    public static Component body(String id) {
        AnalyzerReading.requireId(id);
        return Component.translatableWithFallback("body." + id.replace(':', '.').replace('/', '.'), id);
    }

    private AtmosphereAnalyzerFeedback() { }
}
