package io.github.sunthemoon.advancedrocketrycommunity.compat.satellite;

import io.github.sunthemoon.advancedrocketrycommunity.satellite.model.SatelliteDefinition;
import java.util.List;
import java.util.Map;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;

/** Immutable loading metadata. Contains no world, server or provider references. */
public final class SatellitePayloadCatalog {
    private final Map<Item, ResourceLocation> payloads;
    private final List<SatelliteDefinition> defaults;

    SatellitePayloadCatalog(Map<Item, ResourceLocation> payloads, List<SatelliteDefinition> defaults) {
        this.payloads = Map.copyOf(payloads);
        this.defaults = List.copyOf(defaults);
    }

    public ResourceLocation definitionFor(Item item) { return payloads.get(item); }
    public List<SatelliteDefinition> defaults() { return defaults; }
    public int size() { return payloads.size(); }
}
