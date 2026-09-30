package io.github.sunthemoon.advancedrocketrycommunity.satellite.component;

import com.google.gson.JsonElement;
import com.mojang.serialization.DataResult;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.model.SatelliteLimits;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;
import net.minecraft.resources.ResourceLocation;

/** Immutable component catalog keyed by item, published only after complete validation (ADR-049 §2). */
public final class SatelliteComponentCatalog {
    public static final SatelliteComponentCatalog EMPTY = new SatelliteComponentCatalog(Map.of());
    private static final int MAX_REPORTED_ERRORS = 8;

    private final Map<ResourceLocation, SatelliteComponentDefinition> byItem;

    private SatelliteComponentCatalog(Map<ResourceLocation, SatelliteComponentDefinition> byItem) {
        this.byItem = Collections.unmodifiableMap(new LinkedHashMap<>(byItem));
    }

    public static DataResult<SatelliteComponentCatalog> create(
            Collection<SatelliteComponentDefinition> definitions,
            Predicate<ResourceLocation> itemExists
    ) {
        if (definitions.size() > SatelliteLimits.MAX_COMPONENT_DEFINITIONS) {
            return DataResult.error(() -> "Satellite component catalog exceeds the definition limit");
        }
        List<SatelliteComponentDefinition> sorted = new ArrayList<>(definitions);
        sorted.sort((left, right) -> left.item().toString().compareTo(right.item().toString()));
        Map<ResourceLocation, SatelliteComponentDefinition> byItem = new LinkedHashMap<>();
        for (SatelliteComponentDefinition definition : sorted) {
            if (!itemExists.test(definition.item())) {
                return DataResult.error(() -> "Satellite component item does not exist: " + definition.item());
            }
            if (byItem.putIfAbsent(definition.item(), definition) != null) {
                return DataResult.error(() -> "Satellite component item is defined twice: " + definition.item());
            }
        }
        return DataResult.success(new SatelliteComponentCatalog(byItem));
    }

    /** Decodes a whole resource set; any error rejects the set. */
    public static DataResult<SatelliteComponentCatalog> decode(
            Map<ResourceLocation, JsonElement> resources,
            Predicate<ResourceLocation> itemExists
    ) {
        if (resources.size() > SatelliteLimits.MAX_COMPONENT_DEFINITIONS) {
            return DataResult.error(() -> "Satellite component resource count exceeds the definition limit");
        }
        List<SatelliteComponentDefinition> definitions = new ArrayList<>(resources.size());
        List<String> errors = new ArrayList<>();
        resources.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(entry -> {
            if (errors.size() >= MAX_REPORTED_ERRORS) {
                return;
            }
            if (entry.getValue().toString().length() > SatelliteLimits.MAX_JSON_CHARS_PER_COMPONENT) {
                errors.add(entry.getKey() + " exceeds the JSON character limit");
                return;
            }
            try {
                definitions.add(SatelliteComponentDefinition.decode(entry.getValue()));
            } catch (RuntimeException exception) {
                String message = exception.getMessage() == null ? exception.getClass().getSimpleName()
                        : exception.getMessage();
                errors.add(entry.getKey() + ": " + (message.length() <= 256 ? message : message.substring(0, 253) + "..."));
            }
        });
        if (!errors.isEmpty()) {
            return DataResult.error(() -> String.join("; ", errors));
        }
        return create(definitions, itemExists);
    }

    public Optional<SatelliteComponentDefinition> get(ResourceLocation item) {
        return Optional.ofNullable(byItem.get(item));
    }

    public List<SatelliteComponentDefinition> definitions() {
        return List.copyOf(byItem.values());
    }

    public int size() {
        return byItem.size();
    }
}
