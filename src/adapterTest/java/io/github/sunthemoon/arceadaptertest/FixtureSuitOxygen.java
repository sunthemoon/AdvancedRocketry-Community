package io.github.sunthemoon.arceadaptertest;

import io.github.sunthemoon.advancedrocketrycommunity.api.atmosphere.SuitOxygenProvider;
import java.util.Map;
import java.util.OptionalInt;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;

/** An external integration over vanilla equipment, without new item assets or host imports. */
final class FixtureSuitOxygen implements SuitOxygenProvider {
    static final ResourceLocation ID = AdapterTestMod.id("suit_oxygen");
    static final Map<ResourceLocation, EquipmentSlot> ITEMS = Map.of(
            ResourceLocation.tryParse("minecraft:leather_helmet"), EquipmentSlot.HEAD,
            ResourceLocation.tryParse("minecraft:leather_chestplate"), EquipmentSlot.CHEST,
            ResourceLocation.tryParse("minecraft:leather_leggings"), EquipmentSlot.LEGS,
            ResourceLocation.tryParse("minecraft:leather_boots"), EquipmentSlot.FEET);

    @Override
    public OptionalInt readOxygen(CompoundTag data) {
        if (data.isEmpty()) { return OptionalInt.of(0); }
        if (!data.contains("oxygen", Tag.TAG_INT) || data.getInt("oxygen") < 0 || data.getInt("oxygen") > 2000) {
            return OptionalInt.empty();
        }
        return OptionalInt.of(data.getInt("oxygen"));
    }

    @Override
    public CompoundTag writeOxygen(CompoundTag data, int units) {
        data.putInt("oxygen", units);
        return data;
    }
}
