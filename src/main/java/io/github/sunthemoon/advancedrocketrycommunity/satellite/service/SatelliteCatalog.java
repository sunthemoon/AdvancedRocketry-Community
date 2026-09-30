package io.github.sunthemoon.advancedrocketrycommunity.satellite.service;

import com.mojang.serialization.DataResult;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.SatelliteIds;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.component.SatelliteComponentCatalog;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.component.SatelliteComponentDefinition;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.component.SatelliteComponentRole;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.model.SatelliteDefinition;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.model.SatelliteKindDefinition;
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
    private final Map<ResourceLocation, SatelliteKindDefinition> kindDefinitions;
    private final SatelliteComponentCatalog components;

    private SatelliteCatalog(
            Map<ResourceLocation, SatelliteDefinition> definitions,
            Map<ResourceLocation, SatelliteKindDefinition> kindDefinitions,
            SatelliteComponentCatalog components
    ) {
        this.definitions = Collections.unmodifiableMap(new LinkedHashMap<>(definitions));
        this.kindDefinitions = Collections.unmodifiableMap(new LinkedHashMap<>(kindDefinitions));
        this.components = components;
    }

    public static DataResult<SatelliteCatalog> create(
            Collection<SatelliteDefinition> values,
            Collection<ResourceLocation> knownTargets
    ) {
        return create(values, List.of(), knownTargets, SatelliteComponentCatalog.EMPTY);
    }

    /** Validates data (schema 1) and kind (schema 2) definitions together with the component catalog. */
    public static DataResult<SatelliteCatalog> create(
            Collection<SatelliteDefinition> values,
            Collection<SatelliteKindDefinition> kindValues,
            Collection<ResourceLocation> knownTargets,
            SatelliteComponentCatalog components
    ) {
        if (kindValues.size() > SatelliteLimits.MAX_KIND_DEFINITIONS) {
            return DataResult.error(() -> "Satellite catalog exceeds the kind definition limit");
        }
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
        List<SatelliteKindDefinition> sortedKinds = new ArrayList<>(kindValues);
        sortedKinds.sort((left, right) -> left.id().toString().compareTo(right.id().toString()));
        Map<ResourceLocation, SatelliteKindDefinition> kindsById = new LinkedHashMap<>();
        Set<ResourceLocation> primaries = new HashSet<>();
        for (SatelliteKindDefinition definition : sortedKinds) {
            if (byId.containsKey(definition.id()) || kindsById.putIfAbsent(definition.id(), definition) != null) {
                return DataResult.error(() -> "Duplicate satellite definition id: " + definition.id());
            }
            for (ResourceLocation target : definition.launchTargets()) {
                if (!targets.contains(target)) {
                    return DataResult.error(() -> "Unknown celestial target " + target
                            + " in satellite definition " + definition.id());
                }
            }
            SatelliteComponentDefinition primary = components.get(definition.primaryComponent()).orElse(null);
            if (primary == null || primary.role() != SatelliteComponentRole.PRIMARY
                    || primary.kind().orElseThrow() != definition.kind()) {
                return DataResult.error(() -> "Satellite definition " + definition.id()
                        + " names no primary component of its kind: " + definition.primaryComponent());
            }
            if (!primaries.add(definition.primaryComponent())) {
                return DataResult.error(() -> "Primary component " + definition.primaryComponent()
                        + " selects more than one definition");
            }
        }
        return DataResult.success(new SatelliteCatalog(byId, kindsById, components));
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

    public List<SatelliteKindDefinition> kindDefinitions() {
        return List.copyOf(kindDefinitions.values());
    }

    public Optional<SatelliteKindDefinition> kindDefinition(ResourceLocation id) {
        return Optional.ofNullable(kindDefinitions.get(id));
    }

    /** The definition selected by a primary component item (ADR-049 §3: at most one per component). */
    public Optional<SatelliteKindDefinition> kindDefinitionForPrimary(ResourceLocation primaryComponent) {
        return kindDefinitions.values().stream()
                .filter(definition -> definition.primaryComponent().equals(primaryComponent))
                .findFirst();
    }

    public SatelliteComponentCatalog components() {
        return components;
    }
}
