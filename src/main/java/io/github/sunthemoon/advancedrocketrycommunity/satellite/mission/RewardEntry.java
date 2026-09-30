package io.github.sunthemoon.advancedrocketrycommunity.satellite.mission;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import net.minecraft.resources.ResourceLocation;

/** One plain item and its count in a reward snapshot or an instance yield. */
public record RewardEntry(ResourceLocation item, int count) {
    public RewardEntry {
        Objects.requireNonNull(item, "item");
        if (count < 1) {
            throw new IllegalArgumentException("Reward entry count must be positive");
        }
    }

    /** Validates a bounded list with unique items and a total item bound. */
    public static List<RewardEntry> validated(List<RewardEntry> entries, int maxEntries, int maxItems) {
        Objects.requireNonNull(entries, "entries");
        List<RewardEntry> copy = List.copyOf(entries);
        if (copy.isEmpty() || copy.size() > maxEntries) {
            throw new IllegalArgumentException("Reward list size is outside its bound");
        }
        long total = 0;
        HashSet<ResourceLocation> seen = new HashSet<>();
        for (RewardEntry entry : copy) {
            if (!seen.add(entry.item())) {
                throw new IllegalArgumentException("Reward list repeats an item");
            }
            total += entry.count();
        }
        if (total > maxItems) {
            throw new IllegalArgumentException("Reward list exceeds its item bound");
        }
        return copy;
    }
}
