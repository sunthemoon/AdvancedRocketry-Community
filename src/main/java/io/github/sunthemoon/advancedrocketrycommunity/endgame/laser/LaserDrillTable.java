package io.github.sunthemoon.advancedrocketrycommunity.endgame.laser;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import net.minecraft.resources.ResourceLocation;

/**
 * ADR-055 section 2: one {@code laser_drill_tables} file. It applies to its {@code bodies}, or as the {@code default}
 * to bodies that support surface arrival and have no table of their own. The version is the first 16 hex characters
 * of the SHA-256 of the file's raw bytes.
 */
public record LaserDrillTable(ResourceLocation id, List<ResourceLocation> bodies, boolean isDefault,
                              List<Entry> entries, String version) {
    public static final int MAX_BODIES = 64;
    public static final int MAX_ENTRIES = 64;
    public static final int MAX_COUNT = 64;
    public static final int MAX_WEIGHT = 10_000;

    public LaserDrillTable {
        Objects.requireNonNull(id, "id");
        bodies = List.copyOf(bodies);
        entries = List.copyOf(entries);
        Objects.requireNonNull(version, "version");
        if (bodies.size() > MAX_BODIES || new HashSet<>(bodies).size() != bodies.size()) {
            throw new IllegalArgumentException("A laser drill table names at most " + MAX_BODIES + " unique bodies");
        }
        if (entries.isEmpty() || entries.size() > MAX_ENTRIES) {
            throw new IllegalArgumentException("A laser drill table has 1.." + MAX_ENTRIES + " entries");
        }
        Set<ResourceLocation> items = new HashSet<>();
        for (Entry entry : entries) {
            if (!items.add(entry.item())) {
                throw new IllegalArgumentException("Item " + entry.item() + " appears twice in a laser drill table");
            }
        }
        if (!version.matches("[0-9a-f]{16}")) {
            throw new IllegalArgumentException("A table version is 16 lowercase hex characters");
        }
    }

    /** Σ weight; at most 64 × 10,000. */
    public int totalWeight() {
        int total = 0;
        for (Entry entry : entries) {
            total += entry.weight();
        }
        return total;
    }

    public record Entry(ResourceLocation item, int count, int weight) {
        public Entry {
            Objects.requireNonNull(item, "item");
            if (count < 1 || count > MAX_COUNT) {
                throw new IllegalArgumentException("An entry count is 1.." + MAX_COUNT);
            }
            if (weight < 1 || weight > MAX_WEIGHT) {
                throw new IllegalArgumentException("An entry weight is 1.." + MAX_WEIGHT);
            }
        }
    }
}
