package io.github.sunthemoon.advancedrocketrycommunity.satellite.resource;

import com.mojang.serialization.DataResult;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.model.CelestialBodyDefinition;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.service.CelestialCatalog;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.mission.MissionKind;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.model.SatelliteLimits;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.persistence.SatelliteRecordFit;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.resources.ResourceLocation;

/**
 * The active, immutable asteroid types and gas tables (ADR-052). Built only from a complete, cross-checked set:
 * systems are root bodies, a gas table names a gas giant and no body has two, and every type's and table's
 * worst-case record fits the ADR-050 bounds.
 */
public final class ResourceTables {
    public static final ResourceTables EMPTY = new ResourceTables(Map.of(), Map.of());

    private final Map<ResourceLocation, AsteroidType> asteroidTypes;
    private final Map<ResourceLocation, GasTable> gasByBody;

    private ResourceTables(Map<ResourceLocation, AsteroidType> asteroidTypes, Map<ResourceLocation, GasTable> gasByBody) {
        this.asteroidTypes = Collections.unmodifiableMap(new LinkedHashMap<>(asteroidTypes));
        this.gasByBody = Collections.unmodifiableMap(new LinkedHashMap<>(gasByBody));
    }

    public static DataResult<ResourceTables> create(Collection<AsteroidType> types, Collection<GasTable> gasTables,
                                                    CelestialCatalog celestial) {
        List<String> errors = new ArrayList<>();
        if (types.size() > ResourceTableCodec.MAX_ASTEROID_TYPES) {
            errors.add("More than " + ResourceTableCodec.MAX_ASTEROID_TYPES + " asteroid types");
        }
        if (gasTables.size() > ResourceTableCodec.MAX_GAS_TABLES) {
            errors.add("More than " + ResourceTableCodec.MAX_GAS_TABLES + " gas tables");
        }
        Map<ResourceLocation, AsteroidType> byId = new LinkedHashMap<>();
        for (AsteroidType type : types.stream().sorted(Comparator.comparing(type -> type.id().toString())).toList()) {
            if (byId.putIfAbsent(type.id(), type) != null) {
                errors.add("Asteroid type " + type.id() + " is defined twice");
            }
            for (ResourceLocation system : type.systems()) {
                if (!celestial.get(system).filter(CelestialBodyDefinition::isRoot).isPresent()) {
                    errors.add("Asteroid type " + type.id() + " names " + system + ", which is not a root body");
                }
            }
            List<ResourceLocation> items = new ArrayList<>();
            type.ores().forEach(ore -> items.add(ore.item()));
            items.add(type.baseItem());
            int instance = SatelliteRecordFit.instanceBytes(type.id(), items);
            int mission = SatelliteRecordFit.resourceMissionBytes(MissionKind.ASTEROID,
                    ResourceAlgorithms.ASTEROID_V1 + "/" + type.id() + "/" + type.tableVersion(), items);
            if (instance > SatelliteLimits.MAX_INSTANCE_RECORD_NBT_BYTES
                    || mission > SatelliteLimits.MAX_MISSION_RECORD_NBT_BYTES) {
                errors.add("Asteroid type " + type.id() + " would exceed a record bound (instance " + instance
                        + " B, mission " + mission + " B)");
            }
        }
        Map<ResourceLocation, GasTable> byBody = new LinkedHashMap<>();
        for (GasTable table : gasTables.stream().sorted(Comparator.comparing(table -> table.id().toString())).toList()) {
            boolean gasGiant = celestial.get(table.body()).map(body -> body.capabilities().gasGiant()).orElse(false);
            if (!gasGiant) {
                errors.add("Gas table " + table.id() + " names " + table.body() + ", which is not a gas giant");
            } else if (byBody.putIfAbsent(table.body(), table) != null) {
                errors.add("Gas giant " + table.body() + " has more than one gas table");
            }
            String version = ResourceAlgorithms.GAS_V1 + "/" + table.id() + "/" + table.tableVersion();
            for (GasTable.Product product : table.products()) {
                int mission = SatelliteRecordFit.resourceMissionBytes(MissionKind.GAS, version, List.of(product.item()));
                if (mission > SatelliteLimits.MAX_MISSION_RECORD_NBT_BYTES) {
                    errors.add("Gas table " + table.id() + " would exceed the mission record bound (" + mission + " B)");
                }
            }
        }
        if (!errors.isEmpty()) {
            return DataResult.error(() -> String.join("; ", errors.subList(0, Math.min(32, errors.size()))));
        }
        return DataResult.success(new ResourceTables(byId, byBody));
    }

    public Collection<AsteroidType> asteroidTypes() {
        return asteroidTypes.values();
    }

    public Optional<AsteroidType> asteroidType(ResourceLocation id) {
        return Optional.ofNullable(asteroidTypes.get(id));
    }

    public Optional<GasTable> gasTable(ResourceLocation body) {
        return Optional.ofNullable(gasByBody.get(body));
    }

    public Collection<GasTable> gasTables() {
        return gasByBody.values();
    }
}
