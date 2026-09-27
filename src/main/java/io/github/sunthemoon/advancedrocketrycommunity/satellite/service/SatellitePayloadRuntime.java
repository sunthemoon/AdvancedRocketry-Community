package io.github.sunthemoon.advancedrocketrycommunity.satellite.service;

import io.github.sunthemoon.advancedrocketrycommunity.compat.satellite.SatellitePayloadCatalog;
import io.github.sunthemoon.advancedrocketrycommunity.persistence.BoundedNbt;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.model.SatelliteDefinition;
import java.util.List;
import java.util.Objects;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

/** Loading-lifetime immutable metadata only. Never retains world/session state. */
public final class SatellitePayloadRuntime {
    private static volatile SatellitePayloadCatalog catalog;

    private SatellitePayloadRuntime() { }

    public static void install(SatellitePayloadCatalog value) { catalog = Objects.requireNonNull(value, "catalog"); }

    public static ResourceLocation definitionFor(ItemStack stack) {
        SatellitePayloadCatalog current = catalog;
        if (current == null || stack.isEmpty() || stack.hasTag()) { return null; }
        ResourceLocation id = current.definitionFor(stack.getItem());
        if (id == null) { return null; }
        var nativeItem = stack.serializeNBT();
        return BoundedNbt.fits(nativeItem, 4096, 16, 256)
                && !nativeItem.contains("tag") && !nativeItem.contains("ForgeCaps")
                && nativeItem.equals(ItemStack.of(nativeItem).serializeNBT()) ? id : null;
    }

    public static List<SatelliteDefinition> defaults() {
        SatellitePayloadCatalog current = catalog;
        return current == null ? List.of() : current.defaults();
    }
}
