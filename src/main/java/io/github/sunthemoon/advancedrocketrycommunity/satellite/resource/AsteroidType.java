package io.github.sunthemoon.advancedrocketrycommunity.satellite.resource;

import io.github.sunthemoon.advancedrocketrycommunity.satellite.mission.MissionPayload;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import net.minecraft.resources.ResourceLocation;

/**
 * ADR-052 section 1: one asteroid type from {@code asteroid_types}, with the version of its raw resource bytes.
 * The bounds are checked here, so an instance can hold at most 4,096 items in at most 17 entries.
 */
public record AsteroidType(
        ResourceLocation id,
        int weight,
        List<ResourceLocation> systems,
        int mass,
        int massVariabilityPct,
        int richnessPct,
        int richnessVariabilityPct,
        ResourceLocation baseItem,
        List<Ore> ores,
        int timeMultiplierPct,
        String tableVersion
) {
    public static final int MAX_MASS = 4_096;
    public static final int MAX_ORES = 16;
    public static final int MAX_SYSTEMS = 16;

    public AsteroidType {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(baseItem, "baseItem");
        Objects.requireNonNull(tableVersion, "tableVersion");
        systems = List.copyOf(systems);
        ores = List.copyOf(ores);
        if (weight < 1 || weight > 1_000 || mass < 1 || mass > MAX_MASS
                || outside(massVariabilityPct) || outside(richnessPct) || outside(richnessVariabilityPct)
                || timeMultiplierPct < 10 || timeMultiplierPct > 1_000) {
            throw new IllegalArgumentException("Asteroid type " + id + " has a value outside its bound");
        }
        if (systems.size() > MAX_SYSTEMS || new HashSet<>(systems).size() != systems.size()) {
            throw new IllegalArgumentException("Asteroid type " + id + " systems must be unique and at most 16");
        }
        Set<ResourceLocation> items = new HashSet<>();
        if (ores.isEmpty() || ores.size() > MAX_ORES) {
            throw new IllegalArgumentException("Asteroid type " + id + " needs 1..16 ores");
        }
        for (Ore ore : ores) {
            if (!items.add(ore.item()) || ore.item().equals(baseItem)) {
                throw new IllegalArgumentException("Asteroid type " + id + " repeats an ore or its base item");
            }
        }
        if (!MissionPayload.HEX16.matcher(tableVersion).matches()) {
            throw new IllegalArgumentException("Asteroid type version must be 16 hex characters");
        }
    }

    private static boolean outside(int percent) {
        return percent < 0 || percent > 100;
    }

    public boolean inSystem(ResourceLocation system) {
        return systems.isEmpty() || systems.contains(system);
    }

    public long oreWeight() {
        long total = 0L;
        for (Ore ore : ores) {
            total += ore.weight();
        }
        return total;
    }

    /** One weighted ore of a type. */
    public record Ore(ResourceLocation item, int weight) {
        public Ore {
            Objects.requireNonNull(item, "item");
            if (weight < 1 || weight > 1_000) {
                throw new IllegalArgumentException("Ore weight is outside 1..1,000");
            }
        }
    }
}
