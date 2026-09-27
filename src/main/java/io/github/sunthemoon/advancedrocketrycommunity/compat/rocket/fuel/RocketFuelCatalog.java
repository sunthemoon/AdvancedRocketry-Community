package io.github.sunthemoon.advancedrocketrycommunity.compat.rocket.fuel;

import java.util.Map;
import java.util.Optional;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;

/** Immutable loading metadata, never world or inventory state. */
public final class RocketFuelCatalog {
    private final Map<Item, Entry> entries;

    RocketFuelCatalog(Map<Item, Entry> entries) { this.entries = Map.copyOf(entries); }

    public Entry find(Item item) { return entries.get(item); }

    public int size() { return entries.size(); }

    public record Entry(ResourceLocation id, long units, Optional<Item> remainder) { }
}
