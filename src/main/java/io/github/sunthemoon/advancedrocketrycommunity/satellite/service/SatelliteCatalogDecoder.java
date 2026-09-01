package io.github.sunthemoon.advancedrocketrycommunity.satellite.service;

import com.google.gson.JsonElement;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.JsonOps;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.model.SatelliteDefinition;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.model.SatelliteLimits;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import net.minecraft.resources.ResourceLocation;

/** Decodes an entire prepared resource set without exposing partial definitions. */
public final class SatelliteCatalogDecoder {
    private static final int MAX_REPORTED_ERRORS = 8;

    private SatelliteCatalogDecoder() {
    }

    public static DataResult<SatelliteCatalog> decode(
            Map<ResourceLocation, JsonElement> resources,
            Collection<ResourceLocation> knownTargets
    ) {
        if (resources.size() > SatelliteLimits.MAX_DEFINITIONS) {
            return DataResult.error(() -> "Satellite resource count exceeds the definition limit");
        }
        List<SatelliteDefinition> definitions = new ArrayList<>(resources.size());
        List<String> errors = new ArrayList<>();
        resources.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(entry -> decodeEntry(entry.getKey(), entry.getValue(), definitions, errors));
        if (!errors.isEmpty()) {
            return DataResult.error(() -> String.join("; ", errors));
        }
        return SatelliteCatalog.create(definitions, knownTargets);
    }

    private static void decodeEntry(
            ResourceLocation resourceId,
            JsonElement json,
            List<SatelliteDefinition> definitions,
            List<String> errors
    ) {
        if (errors.size() >= MAX_REPORTED_ERRORS) {
            return;
        }
        if (json.toString().length() > SatelliteLimits.MAX_JSON_CHARS_PER_DEFINITION) {
            errors.add(resourceId + " exceeds the JSON character limit");
            return;
        }
        DataResult<SatelliteDefinition> decoded;
        try {
            decoded = SatelliteDefinition.CODEC.parse(JsonOps.INSTANCE, json);
        } catch (RuntimeException exception) {
            errors.add(resourceId + ": " + boundedMessage(exception));
            return;
        }
        if (decoded.error().isPresent()) {
            errors.add(resourceId + ": " + decoded.error().orElseThrow().message());
            return;
        }
        SatelliteDefinition definition = decoded.result().orElseThrow();
        if (!resourceId.equals(definition.id())) {
            errors.add(resourceId + " declares mismatched id " + definition.id());
            return;
        }
        definitions.add(definition);
    }

    private static String boundedMessage(RuntimeException exception) {
        String message = exception.getMessage();
        if (message == null || message.isBlank()) {
            message = exception.getClass().getSimpleName();
        }
        return message.length() <= 512 ? message : message.substring(0, 509) + "...";
    }
}
