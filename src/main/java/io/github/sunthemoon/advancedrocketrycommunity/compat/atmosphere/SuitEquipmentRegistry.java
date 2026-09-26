package io.github.sunthemoon.advancedrocketrycommunity.compat.atmosphere;

import io.github.sunthemoon.advancedrocketrycommunity.api.atmosphere.SuitEquipmentRegistrar;
import io.github.sunthemoon.advancedrocketrycommunity.api.atmosphere.SuitOxygenProvider;
import io.github.sunthemoon.advancedrocketrycommunity.atmosphere.content.SpaceSuitArmorItem;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

/** Atomic owner-bound loading registration; provider code is never called here. */
public final class SuitEquipmentRegistry implements AutoCloseable {
    public static final int MAX_PROVIDERS = 256;
    public static final int MAX_ITEMS_PER_PROVIDER = 64;
    public static final int MAX_ITEMS = 1024;
    private final Thread loadingThread = Thread.currentThread();
    private final Function<ResourceLocation, Item> lookup;
    private final Set<ResourceLocation> providers = new HashSet<>();
    private final Map<Item, SuitEquipmentCatalog.Piece> pieces = new HashMap<>();
    private Object activeHandle;
    private boolean registering;
    private boolean closed;

    /** Unknown IDs must resolve to null, not a default item. */
    public SuitEquipmentRegistry(Function<ResourceLocation, Item> lookup) {
        this.lookup = Objects.requireNonNull(lookup, "lookup");
    }

    public SuitEquipmentRegistrar forOwner(String owner) {
        requireOpen();
        ResourceLocation probe = ResourceLocation.tryParse(Objects.requireNonNull(owner, "owner") + ":registration");
        if (probe == null || !probe.getNamespace().equals(owner) || owner.length() > 255) {
            throw new IllegalArgumentException("Invalid suit equipment owner namespace");
        }
        Object handle = new Object();
        activeHandle = handle;
        return (id, items, version, provider) -> {
            requireOpen();
            if (activeHandle != handle) {
                throw new IllegalStateException("Suit registration handle is no longer active");
            }
            registering = true;
            try {
                register(owner, id, items, version, provider);
            } finally {
                registering = false;
            }
        };
    }

    private void register(String owner, ResourceLocation id, Map<ResourceLocation, EquipmentSlot> items,
                          int version, SuitOxygenProvider provider) {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(items, "items");
        Objects.requireNonNull(provider, "provider");
        if (!owner.equals(id.getNamespace()) || id.toString().length() > 255 || version <= 0
                || providers.contains(id) || providers.size() >= MAX_PROVIDERS
                || items.isEmpty() || items.size() > MAX_ITEMS_PER_PROVIDER
                || pieces.size() + items.size() > MAX_ITEMS) {
            throw new IllegalArgumentException("Invalid, duplicate or over-capacity suit registration");
        }
        var definition = new SuitEquipmentCatalog.Provider(id, version, provider);
        Map<Item, SuitEquipmentCatalog.Piece> resolved = new HashMap<>();
        for (var entry : Map.copyOf(items).entrySet()) {
            ResourceLocation itemId = entry.getKey();
            EquipmentSlot slot = entry.getValue();
            Item item = lookup.apply(itemId);
            if (itemId.toString().length() > 255 || item == null || item == Items.AIR
                    || item.getMaxStackSize() != 1 || slot.getType() != EquipmentSlot.Type.ARMOR
                    || item instanceof SpaceSuitArmorItem || pieces.containsKey(item)
                    || item instanceof ArmorItem armor && armor.getEquipmentSlot() != slot
                    || resolved.putIfAbsent(item, new SuitEquipmentCatalog.Piece(slot, definition)) != null) {
                throw new IllegalArgumentException("Invalid or claimed suit item: " + itemId);
            }
        }
        pieces.putAll(resolved);
        providers.add(id);
    }

    public SuitEquipmentCatalog freeze() {
        requireOpen();
        SuitEquipmentCatalog catalog = new SuitEquipmentCatalog(pieces);
        close();
        return catalog;
    }

    @Override
    public void close() {
        requireThread();
        if (registering) {
            throw new IllegalStateException("Cannot close during suit registration");
        }
        closed = true;
        activeHandle = null;
        providers.clear();
        pieces.clear();
    }

    private void requireOpen() {
        requireThread();
        if (closed || registering) {
            throw new IllegalStateException("Suit registry is closed or registering");
        }
    }

    private void requireThread() {
        if (Thread.currentThread() != loadingThread) {
            throw new IllegalStateException("Suit registration requires its loading thread");
        }
    }
}
