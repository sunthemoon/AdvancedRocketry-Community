package io.github.sunthemoon.advancedrocketrycommunity.rocket.forge;

import com.google.gson.JsonParser;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.validation.RocketValidationCode;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import net.minecraft.network.chat.Component;

/** Localized components with packaged English fallbacks for servers without client-only language namespaces. */
public final class RocketFeedback {
    private static final String ENTITY_KEY = "entity.advancedrocketrycommunity.rocket";
    private static final Map<String, String> ENGLISH = loadEnglish();

    private RocketFeedback() { }

    public static Component entityName() {
        return Component.translatableWithFallback(ENTITY_KEY, ENGLISH.get(ENTITY_KEY));
    }

    public static Component reason(RocketValidationCode code) {
        return Component.translatableWithFallback(code.translationKey(), ENGLISH.get(code.translationKey()));
    }

    private static Map<String, String> loadEnglish() {
        var keys = new java.util.HashSet<String>();
        keys.add(ENTITY_KEY);
        for (var code : RocketValidationCode.values()) { keys.add(code.translationKey()); }
        var result = new HashMap<String, String>();
        // Fixed, trusted mod resources, not world data or a resource-pack scan. Keep only the bounded rocket table.
        for (String namespace : new String[]{"advancedrocketrycommunity_v050", "advancedrocketrycommunity_v130",
                "advancedrocketrycommunity_v180"}) {
            String path = "/assets/" + namespace + "/lang/en_us.json";
            try (var input = Objects.requireNonNull(RocketFeedback.class.getResourceAsStream(path), path);
                    var reader = new InputStreamReader(input, StandardCharsets.UTF_8)) {
                var json = JsonParser.parseReader(reader).getAsJsonObject();
                for (String key : keys) {
                    if (json.has(key)) { result.put(key, json.get(key).getAsString()); }
                }
            } catch (IOException exception) {
                throw new UncheckedIOException("Cannot read packaged rocket language " + path, exception);
            }
        }
        for (String key : keys) {
            String value = result.get(key);
            if (value == null || value.isBlank() || value.length() > 256) {
                throw new IllegalStateException("Missing or oversized packaged rocket translation: " + key);
            }
        }
        return Map.copyOf(result);
    }
}
