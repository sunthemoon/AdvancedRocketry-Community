package io.github.sunthemoon.advancedrocketrycommunity.compat.rocket.fuel;

import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import io.github.sunthemoon.advancedrocketrycommunity.api.rocket.RocketFuelDefinition;
import io.github.sunthemoon.advancedrocketrycommunity.api.rocket.RocketFuelRegistrar;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

/** Atomic, owner/thread-bound registration of item fuels. No providers run in a world tick. */
public final class RocketFuelRegistry implements AutoCloseable {
    public static final int MAX_DEFINITIONS = 256;
    public static final int MAX_ITEMS_PER_DEFINITION = 64;
    public static final int MAX_ITEMS = 1024;
    public static final ResourceLocation BUILTIN_ID = ResourceLocation.tryParse(ModIdentity.MOD_ID + ":rocket_fuel_cell");
    private final Thread loadingThread = Thread.currentThread();
    private final Function<ResourceLocation, Item> lookup;
    private final Set<ResourceLocation> definitions = new HashSet<>();
    private final Map<Item, RocketFuelCatalog.Entry> entries = new HashMap<>();
    private Object activeHandle;
    private boolean registering;
    private boolean closed;

    /** Lookup must return null for an unknown ID, not the registry's default item. */
    public RocketFuelRegistry(Function<ResourceLocation, Item> lookup) {
        this.lookup = Objects.requireNonNull(lookup, "lookup");
        Item cell = resolve(BUILTIN_ID);
        Item canister = resolve(ResourceLocation.tryParse(ModIdentity.MOD_ID + ":empty_canister"));
        entries.put(cell, new RocketFuelCatalog.Entry(BUILTIN_ID, 500L, Optional.of(canister)));
    }

    public RocketFuelRegistrar forOwner(String owner) {
        requireOpen();
        ResourceLocation probe = ResourceLocation.tryParse(Objects.requireNonNull(owner, "owner") + ":registration");
        if (probe == null || !probe.getNamespace().equals(owner) || owner.length() > 255) {
            throw new IllegalArgumentException("Invalid rocket fuel owner namespace");
        }
        Object handle = new Object();
        activeHandle = handle;
        return (id, items, definition) -> {
            requireOpen();
            if (activeHandle != handle) { throw new IllegalStateException("Expired rocket fuel registration handle"); }
            registering = true;
            try { register(owner, id, items, definition); }
            finally { registering = false; }
        };
    }

    private void register(String owner, ResourceLocation id, Set<ResourceLocation> items, RocketFuelDefinition value) {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(items, "items");
        Objects.requireNonNull(value, "definition");
        if (!id.getNamespace().equals(owner) || id.toString().length() > 255 || id.equals(BUILTIN_ID)
                || definitions.contains(id) || definitions.size() >= MAX_DEFINITIONS
                || items.isEmpty() || items.size() > MAX_ITEMS_PER_DEFINITION
                || entries.size() - 1 + items.size() > MAX_ITEMS) {
            throw new IllegalArgumentException("Invalid, duplicate or over-capacity rocket fuel registration");
        }
        Optional<Item> remainder = value.remainderItem().map(this::resolve);
        var entry = new RocketFuelCatalog.Entry(id, value.units(), remainder);
        Map<Item, RocketFuelCatalog.Entry> resolved = new HashMap<>();
        for (ResourceLocation itemId : Set.copyOf(items)) {
            Item item = resolve(itemId);
            if (entries.containsKey(item) || resolved.putIfAbsent(item, entry) != null) {
                throw new IllegalArgumentException("Claimed rocket fuel input: " + itemId);
            }
        }
        entries.putAll(resolved);
        definitions.add(id);
    }

    private Item resolve(ResourceLocation id) {
        if (id.toString().length() > 255 || id.equals(ResourceLocation.tryParse("minecraft:air"))) {
            throw new IllegalArgumentException("Invalid rocket fuel item ID");
        }
        Item item = lookup.apply(id);
        if (item == null || item == Items.AIR) { throw new IllegalArgumentException("Unknown or air fuel item: " + id); }
        return item;
    }

    public RocketFuelCatalog freeze() {
        requireOpen();
        RocketFuelCatalog result = new RocketFuelCatalog(entries);
        close();
        return result;
    }

    @Override
    public void close() {
        requireThread();
        if (registering) { throw new IllegalStateException("Cannot close during fuel registration"); }
        closed = true;
        activeHandle = null;
        entries.clear();
        definitions.clear();
    }

    private void requireOpen() {
        requireThread();
        if (closed || registering) { throw new IllegalStateException("Rocket fuel registry is closed or registering"); }
    }

    private void requireThread() {
        if (Thread.currentThread() != loadingThread) {
            throw new IllegalStateException("Rocket fuel registration requires its loading thread");
        }
    }
}
