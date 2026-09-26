package io.github.sunthemoon.advancedrocketrycommunity.compat.atmosphere;

import io.github.sunthemoon.advancedrocketrycommunity.api.atmosphere.SuitOxygenProvider;
import java.util.Map;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Item;

/** Immutable loading metadata, without player/level state. */
public final class SuitEquipmentCatalog {
    private static final SuitEquipmentCatalog EMPTY = new SuitEquipmentCatalog(Map.of());
    private final Map<Item, Piece> pieces;

    SuitEquipmentCatalog(Map<Item, Piece> pieces) {
        this.pieces = Map.copyOf(pieces);
    }

    public static SuitEquipmentCatalog empty() {
        return EMPTY;
    }

    Piece find(Item item) {
        return pieces.get(item);
    }

    int size() {
        return pieces.size();
    }

    record Provider(ResourceLocation id, int version, SuitOxygenProvider callbacks) { }
    record Piece(EquipmentSlot slot, Provider provider) { }
}
