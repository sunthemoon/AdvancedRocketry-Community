package io.github.sunthemoon.advancedrocketrycommunity.celestial.service;

import com.google.gson.JsonElement;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.JsonOps;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.model.CelestialBodyDefinition;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.minecraft.resources.ResourceLocation;

/** Decodes a complete prepared resource set without publishing partial state. */
public final class CelestialCatalogDecoder {
    public static final int MAX_JSON_CHARS_PER_BODY = 32_768;
    private static final int MAX_REPORTED_ERRORS = 8;

    private CelestialCatalogDecoder() {
    }

    public static DataResult<CelestialCatalog> decode(
            Map<ResourceLocation, JsonElement> resources
    ) {
        if (resources.isEmpty()) {
            return DataResult.error(() -> "No celestial body definitions were found");
        }
        if (resources.size() > CelestialCatalog.MAX_BODIES) {
            return DataResult.error(() -> "Celestial resource count exceeds " + CelestialCatalog.MAX_BODIES);
        }

        List<CelestialBodyDefinition> definitions = new ArrayList<>(resources.size());
        List<String> errors = new ArrayList<>();
        resources.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(entry -> decodeEntry(entry.getKey(), entry.getValue(), definitions, errors));
        if (!errors.isEmpty()) {
            String message = String.join("; ", errors);
            return DataResult.error(() -> message);
        }
        return CelestialCatalog.create(definitions).flatMap(CelestialCatalog::requireFixedBaseline);
    }

    private static void decodeEntry(
            ResourceLocation resourceId,
            JsonElement json,
            List<CelestialBodyDefinition> definitions,
            List<String> errors
    ) {
        if (errors.size() >= MAX_REPORTED_ERRORS) {
            return;
        }
        if (json.toString().length() > MAX_JSON_CHARS_PER_BODY) {
            errors.add(resourceId + " exceeds " + MAX_JSON_CHARS_PER_BODY + " JSON characters");
            return;
        }

        DataResult<CelestialBodyDefinition> decoded = CelestialBodyDefinition.CODEC.parse(JsonOps.INSTANCE, json);
        if (decoded.error().isPresent()) {
            errors.add(resourceId + ": " + decoded.error().orElseThrow().message());
            return;
        }
        CelestialBodyDefinition definition = decoded.result().orElseThrow();
        if (!resourceId.equals(definition.id())) {
            errors.add(resourceId + " declares mismatched id " + definition.id());
            return;
        }
        definitions.add(definition);
    }
}
