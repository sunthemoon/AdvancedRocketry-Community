package io.github.sunthemoon.advancedrocketrycommunity.satellite.service;

import com.mojang.serialization.DataResult;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.SatelliteIds;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.model.SatelliteDefinition;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.model.SatelliteLimits;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import net.minecraft.resources.ResourceLocation;

/** Immutable definition snapshot published only after complete validation. */
public final class SatelliteCatalog {
    private final Map<ResourceLocation, SatelliteDefinition> definitions;

    private SatelliteCatalog(Map<ResourceLocation, SatelliteDefinition> definitions) {
        this.definitions = Collections.unmodifiableMap(new LinkedHashMap<>(definitions));
    }

    public static DataResult<SatelliteCatalog> create(
            Collection<SatelliteDefinition> values,
            Collection<ResourceLocation> knownTargets
    ) {
        if (values.isEmpty()) {
            return DataResult.error(() -> "Satellite catalog cannot be empty");
        }
        if (values.size() > SatelliteLimits.MAX_DEFINITIONS) {
            return DataResult.error(() -> "Satellite catalog exceeds the definition limit");
        }
        Set<ResourceLocation> targets = new HashSet<>(knownTargets);
        if (targets.isEmpty()) {
            return DataResult.error(() -> "Satellite catalog has no known celestial targets");
        }

        List<SatelliteDefinition> sorted = new ArrayList<>(values);
        sorted.sort((left, right) -> left.id().compareTo(right.id()));
        Map<ResourceLocation, SatelliteDefinition> byId = new LinkedHashMap<>();
        for (SatelliteDefinition definition : sorted) {
            if (byId.putIfAbsent(definition.id(), definition) != null) {
                return DataResult.error(() -> "Duplicate satellite definition id: " + definition.id());
            }
            for (ResourceLocation target : definition.allowedTargets()) {
                if (!targets.contains(target)) {
                    return DataResult.error(() -> "Unknown celestial target " + target
                            + " in satellite definition " + definition.id());
                }
            }
        }
        if (!byId.containsKey(SatelliteIds.DATA_SATELLITE)) {
            return DataResult.error(() -> "Catalog is missing required data_satellite definition");
        }
        return DataResult.success(new SatelliteCatalog(byId));
    }

    public int size() {
        return definitions.size();
    }

    public List<SatelliteDefinition> definitions() {
        return List.copyOf(definitions.values());
    }

    public Optional<SatelliteDefinition> get(ResourceLocation id) {
        return Optional.ofNullable(definitions.get(id));
    }
}
