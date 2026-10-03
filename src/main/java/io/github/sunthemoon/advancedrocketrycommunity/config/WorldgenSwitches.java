package io.github.sunthemoon.advancedrocketrycommunity.config;

import java.util.Map;
import java.util.Set;
import java.util.function.BooleanSupplier;

/**
 * The server switches of the v1.8 world features (ADR-061 section 3.5, ADR-063 sections 4 to 6), by name. A disabled
 * feature places nothing and a disabled structure returns no generation point in new chunks; starts already saved
 * still finish. Until the COMMON config is loaded each switch reads its default (on).
 */
public final class WorldgenSwitches {
    public static final String OVERWORLD_ORES = "overworld_ores";
    public static final String PLANET_ORES = "planet_ores";
    public static final String CRATERS = "craters";
    public static final String VOLCANOES = "volcanoes";
    public static final String GEODES = "geodes";
    public static final String CHARRED_TREES = "charred_trees";
    public static final String LIGHTWOOD_TREES = "lightwood_trees";
    public static final String SWAMP_TREES = "swamp_trees";
    public static final String INVERTED_PILLARS = "inverted_pillars";
    public static final String CRYSTAL_CLUSTERS = "crystal_clusters";
    public static final String ELECTRIC_MUSHROOMS = "electric_mushrooms";

    private static final Map<String, BooleanSupplier> SWITCHES = Map.ofEntries(
            Map.entry(OVERWORLD_ORES, CommonConfig::overworldOresEnabled),
            Map.entry(PLANET_ORES, CommonConfig::planetOresEnabled),
            Map.entry(CRATERS, CommonConfig::cratersEnabled),
            Map.entry(VOLCANOES, CommonConfig::volcanoesEnabled),
            Map.entry(GEODES, CommonConfig::geodesEnabled),
            Map.entry(CHARRED_TREES, CommonConfig::charredTreesEnabled),
            Map.entry(LIGHTWOOD_TREES, CommonConfig::lightwoodTreesEnabled),
            Map.entry(SWAMP_TREES, CommonConfig::swampTreesEnabled),
            Map.entry(INVERTED_PILLARS, CommonConfig::invertedPillarsEnabled),
            Map.entry(CRYSTAL_CLUSTERS, CommonConfig::crystalClustersEnabled),
            Map.entry(ELECTRIC_MUSHROOMS, CommonConfig::electricMushroomsEnabled)
    );

    private WorldgenSwitches() {
    }

    public static boolean known(String name) {
        return SWITCHES.containsKey(name);
    }

    public static Set<String> names() {
        return SWITCHES.keySet();
    }

    public static boolean enabled(String name) {
        BooleanSupplier value = SWITCHES.get(name);
        if (value == null) {
            throw new IllegalArgumentException("Unknown server switch " + name);
        }
        return value.getAsBoolean();
    }
}
