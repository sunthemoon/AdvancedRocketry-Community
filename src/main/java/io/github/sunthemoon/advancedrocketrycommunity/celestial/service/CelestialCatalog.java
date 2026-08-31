package io.github.sunthemoon.advancedrocketrycommunity.celestial.service;

import com.mojang.serialization.DataResult;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.CelestialIds;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.model.CelestialBodyDefinition;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;

/** Immutable, graph-validated celestial definition snapshot. */
public final class CelestialCatalog {
    public static final int MAX_BODIES = 128;

    private final Map<ResourceLocation, CelestialBodyDefinition> definitions;

    private CelestialCatalog(Map<ResourceLocation, CelestialBodyDefinition> definitions) {
        this.definitions = Collections.unmodifiableMap(new LinkedHashMap<>(definitions));
    }

    public static DataResult<CelestialCatalog> create(Collection<CelestialBodyDefinition> values) {
        if (values.isEmpty()) {
            return DataResult.error(() -> "Celestial catalog cannot be empty");
        }
        if (values.size() > MAX_BODIES) {
            return DataResult.error(() -> "Celestial catalog exceeds " + MAX_BODIES + " bodies");
        }

        List<CelestialBodyDefinition> sorted = new ArrayList<>(values);
        sorted.sort((left, right) -> left.id().compareTo(right.id()));
        Map<ResourceLocation, CelestialBodyDefinition> byId = new LinkedHashMap<>();
        for (CelestialBodyDefinition definition : sorted) {
            if (byId.putIfAbsent(definition.id(), definition) != null) {
                return DataResult.error(() -> "Duplicate celestial body id: " + definition.id());
            }
        }
        for (CelestialBodyDefinition definition : sorted) {
            Optional<ResourceLocation> parent = definition.parentId();
            if (parent.isPresent() && !byId.containsKey(parent.get())) {
                return DataResult.error(() -> "Missing parent " + parent.get() + " for " + definition.id());
            }
        }

        String cycle = findCycle(byId);
        if (cycle != null) {
            return DataResult.error(() -> "Celestial parent cycle: " + cycle);
        }
        return DataResult.success(new CelestialCatalog(byId));
    }

    public DataResult<CelestialCatalog> requireFixedBaseline() {
        CelestialBodyDefinition earth = definitions.get(CelestialIds.EARTH_ID);
        CelestialBodyDefinition moon = definitions.get(CelestialIds.MOON_ID);
        CelestialBodyDefinition space = definitions.get(CelestialIds.SPACE_ID);
        if (earth == null || moon == null || space == null) {
            return DataResult.error(() -> "Catalog must define Earth, Moon, and Space");
        }
        if (!earth.isRoot() || !earth.levelKey().equals(Level.OVERWORLD)) {
            return DataResult.error(() -> "Earth must be a root mapped to minecraft:overworld");
        }
        if (!moon.parentId().filter(CelestialIds.EARTH_ID::equals).isPresent()
                || !moon.levelKey().equals(CelestialIds.MOON_LEVEL)) {
            return DataResult.error(() -> "Moon must orbit Earth and map to the fixed Moon Level");
        }
        if (!space.parentId().filter(CelestialIds.EARTH_ID::equals).isPresent()
                || !space.levelKey().equals(CelestialIds.SPACE_LEVEL)) {
            return DataResult.error(() -> "Space must map to the fixed Space Level under Earth");
        }
        return DataResult.success(this);
    }

    public int size() {
        return definitions.size();
    }

    public List<CelestialBodyDefinition> definitions() {
        return List.copyOf(definitions.values());
    }

    public Optional<CelestialBodyDefinition> get(ResourceLocation id) {
        return Optional.ofNullable(definitions.get(id));
    }

    public Optional<CelestialBodyDefinition> forLevel(ResourceKey<Level> levelKey) {
        return definitions.values().stream()
                .filter(definition -> definition.levelKey().equals(levelKey))
                .findFirst();
    }

    private static String findCycle(Map<ResourceLocation, CelestialBodyDefinition> definitions) {
        Map<ResourceLocation, VisitState> states = new HashMap<>();
        List<ResourceLocation> path = new ArrayList<>();
        for (ResourceLocation id : definitions.keySet()) {
            String cycle = visit(id, definitions, states, path);
            if (cycle != null) {
                return cycle;
            }
        }
        return null;
    }

    private static String visit(
            ResourceLocation id,
            Map<ResourceLocation, CelestialBodyDefinition> definitions,
            Map<ResourceLocation, VisitState> states,
            List<ResourceLocation> path
    ) {
        VisitState state = states.get(id);
        if (state == VisitState.DONE) {
            return null;
        }
        if (state == VisitState.VISITING) {
            int start = path.indexOf(id);
            List<ResourceLocation> cycle = new ArrayList<>(path.subList(start, path.size()));
            cycle.add(id);
            return String.join(" -> ", cycle.stream().map(ResourceLocation::toString).toList());
        }

        states.put(id, VisitState.VISITING);
        path.add(id);
        Optional<ResourceLocation> parent = definitions.get(id).parentId();
        if (parent.isPresent()) {
            String cycle = visit(parent.get(), definitions, states, path);
            if (cycle != null) {
                return cycle;
            }
        }
        path.remove(path.size() - 1);
        states.put(id, VisitState.DONE);
        return null;
    }

    private enum VisitState {
        VISITING,
        DONE
    }
}
