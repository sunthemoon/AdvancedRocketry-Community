package io.github.sunthemoon.advancedrocketrycommunity.travel.route.service;

import com.google.gson.JsonElement;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.JsonOps;
import io.github.sunthemoon.advancedrocketrycommunity.travel.route.model.RouteDefinition;
import io.github.sunthemoon.advancedrocketrycommunity.travel.route.model.RouteLimits;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import net.minecraft.resources.ResourceLocation;

/** Decodes one complete prepared route resource set without publishing partial state. */
public final class RouteCatalogDecoder {
    private RouteCatalogDecoder() {
    }

    public static DataResult<RouteCatalog> decode(
            Map<ResourceLocation, JsonElement> resources,
            Collection<ResourceLocation> availableBodies
    ) {
        if (resources.isEmpty()) {
            return DataResult.error(() -> "No route definitions were found");
        }
        if (resources.size() > RouteLimits.MAX_ROUTES) {
            return DataResult.error(() -> "Route resource count exceeds " + RouteLimits.MAX_ROUTES);
        }

        List<RouteDefinition> definitions = new ArrayList<>(resources.size());
        List<String> errors = new ArrayList<>();
        resources.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(entry -> decodeEntry(entry.getKey(), entry.getValue(), definitions, errors));
        if (!errors.isEmpty()) {
            return DataResult.error(() -> String.join("; ", errors));
        }
        return RouteCatalog.create(definitions, availableBodies);
    }

    private static void decodeEntry(
            ResourceLocation resourceId,
            JsonElement json,
            List<RouteDefinition> definitions,
            List<String> errors
    ) {
        if (errors.size() >= RouteLimits.MAX_REPORTED_ERRORS) {
            return;
        }
        if (json.toString().length() > RouteLimits.MAX_JSON_CHARS_PER_ROUTE) {
            errors.add(resourceId + " exceeds " + RouteLimits.MAX_JSON_CHARS_PER_ROUTE + " JSON characters");
            return;
        }

        DataResult<RouteDefinition> decoded = RouteDefinition.CODEC.parse(JsonOps.INSTANCE, json);
        if (decoded.error().isPresent()) {
            errors.add(resourceId + ": " + decoded.error().orElseThrow().message());
            return;
        }
        RouteDefinition definition = decoded.result().orElseThrow();
        if (!resourceId.equals(definition.id())) {
            errors.add(resourceId + " declares mismatched id " + definition.id());
            return;
        }
        definitions.add(definition);
    }
}
