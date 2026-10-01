package io.github.sunthemoon.advancedrocketrycommunity.endgame.blackhole;

import java.util.Map;
import java.util.Objects;
import net.minecraft.resources.ResourceLocation;

/**
 * ADR-057 section 2: how long one item burns. Listed items burn their own ticks, every other plain item the
 * table's default. The built-in {@code default} table reproduces the legacy values.
 */
public record BlackHoleFuelTable(ResourceLocation id, int defaultBurnTicks, Map<ResourceLocation, Integer> burnTicks,
                                 String version) {
    public static final int MAX_BURN_TICKS = 72_000;
    public static final int MAX_ENTRIES = 64;

    public BlackHoleFuelTable {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(version, "version");
        burnTicks = Map.copyOf(burnTicks);
        if (defaultBurnTicks < 1 || defaultBurnTicks > MAX_BURN_TICKS || burnTicks.size() > MAX_ENTRIES
                || burnTicks.values().stream().anyMatch(ticks -> ticks < 1 || ticks > MAX_BURN_TICKS)) {
            throw new IllegalArgumentException("A fuel table is outside its bounds");
        }
    }

    public int burnTicks(ResourceLocation item) {
        return burnTicks.getOrDefault(item, defaultBurnTicks);
    }
}
