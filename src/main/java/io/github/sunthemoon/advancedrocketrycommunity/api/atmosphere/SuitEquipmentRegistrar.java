package io.github.sunthemoon.advancedrocketrycommunity.api.atmosphere;

import java.util.Map;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;

/** Owner-bound, loading-thread-only registration; handles expire when dispatch ends. */
@FunctionalInterface
public interface SuitEquipmentRegistrar {
    /**
     * Maps registered, unstackable items to HEAD/CHEST/LEGS/FEET. Provider ID uses
     * the receiving mod's namespace; foreign item IDs are permitted. ArmorItem slots
     * must match; other items require the integration's normal equipping behavior.
     * Limits: 256 providers, 64 items/registration, 1,024 items total, IDs <=255 chars.
     * Payload version is positive and exact-match, not an automatic migration.
     * Duplicate/reserved claims reject the entire registration. Only a worn CHEST
     * supplies oxygen; all four valid pieces (including mixed providers) are needed.
     */
    void register(ResourceLocation providerId, Map<ResourceLocation, EquipmentSlot> items,
                  int payloadVersion, SuitOxygenProvider oxygenProvider);
}
