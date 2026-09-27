package io.github.sunthemoon.advancedrocketrycommunity.compat.satellite;

import io.github.sunthemoon.advancedrocketrycommunity.api.satellite.SatelliteMissionDefinition;
import io.github.sunthemoon.advancedrocketrycommunity.api.satellite.SatellitePayloadRegistrar;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.SatelliteIds;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.model.SatelliteDefinition;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

/** Finite owner/thread-bound payload registration, frozen before server datapack loading. */
public final class SatellitePayloadRegistry implements AutoCloseable {
    public static final int MAX_EXTERNAL_PAYLOADS = 15;
    private final Thread loadingThread = Thread.currentThread();
    private final Function<ResourceLocation, Item> lookup;
    private final Set<Item> reserved = new HashSet<>();
    private final Map<Item, ResourceLocation> payloads = new HashMap<>();
    private final Map<ResourceLocation, SatelliteDefinition> defaults = new HashMap<>();
    private Object activeHandle;
    private boolean registering;
    private boolean closed;

    /** Lookup must return null for absent IDs rather than a registry default. */
    public SatellitePayloadRegistry(Function<ResourceLocation, Item> lookup) {
        this.lookup = Objects.requireNonNull(lookup, "lookup");
        for (String path : List.of("satellite_chassis", "satellite_solar_module", "data_storage_unit",
                "satellite_control_chip", "data_satellite_package")) {
            reserved.add(resolve(ResourceLocation.tryParse("advancedrocketrycommunity:" + path)));
        }
        reserved.add(Items.REDSTONE);
        payloads.put(resolve(ResourceLocation.tryParse("advancedrocketrycommunity:data_storage_unit")),
                SatelliteIds.DATA_SATELLITE);
    }

    public SatellitePayloadRegistrar forOwner(String owner) {
        requireOpen();
        ResourceLocation probe = ResourceLocation.tryParse(Objects.requireNonNull(owner, "owner") + ":registration");
        if (probe == null || !probe.getNamespace().equals(owner) || owner.length() > 128) {
            throw new IllegalArgumentException("Invalid satellite payload owner namespace");
        }
        Object handle = new Object();
        activeHandle = handle;
        return (id, item, mission) -> {
            requireOpen();
            if (activeHandle != handle) { throw new IllegalStateException("Expired satellite registration handle"); }
            registering = true;
            try { register(owner, id, item, mission); }
            finally { registering = false; }
        };
    }

    private void register(String owner, ResourceLocation id, ResourceLocation itemId, SatelliteMissionDefinition mission) {
        Objects.requireNonNull(id, "definitionId");
        Objects.requireNonNull(mission, "mission");
        if (!id.getNamespace().equals(owner) || id.toString().length() > 128
                || id.equals(SatelliteIds.DATA_SATELLITE) || defaults.containsKey(id)
                || defaults.size() >= MAX_EXTERNAL_PAYLOADS) {
            throw new IllegalArgumentException("Invalid, duplicate or over-capacity satellite definition");
        }
        Item item = resolve(itemId);
        if (reserved.contains(item) || payloads.containsKey(item)) {
            throw new IllegalArgumentException("Reserved or claimed satellite payload: " + itemId);
        }
        SatelliteDefinition definition = new SatelliteDefinition(1, id, mission.durationTicks(),
                mission.researchYield(), mission.discoveryCost(), mission.allowedTargets());
        defaults.put(id, definition);
        payloads.put(item, id);
    }

    private Item resolve(ResourceLocation id) {
        Objects.requireNonNull(id, "payloadItem");
        if (id.toString().length() > 128 || id.equals(ResourceLocation.tryParse("minecraft:air"))) {
            throw new IllegalArgumentException("Invalid satellite payload item ID");
        }
        Item item = lookup.apply(id);
        if (item == null || item == Items.AIR) { throw new IllegalArgumentException("Unknown or air payload: " + id); }
        return item;
    }

    public SatellitePayloadCatalog freeze() {
        requireOpen();
        var result = new SatellitePayloadCatalog(payloads, defaults.values().stream()
                .sorted(java.util.Comparator.comparing(SatelliteDefinition::id)).toList());
        close();
        return result;
    }

    @Override public void close() {
        requireThread();
        if (registering) { throw new IllegalStateException("Cannot close during satellite registration"); }
        closed = true;
        activeHandle = null;
        payloads.clear(); defaults.clear(); reserved.clear();
    }

    private void requireOpen() {
        requireThread();
        if (closed || registering) { throw new IllegalStateException("Satellite registry is closed or registering"); }
    }

    private void requireThread() {
        if (Thread.currentThread() != loadingThread) {
            throw new IllegalStateException("Satellite registration requires its loading thread");
        }
    }
}
