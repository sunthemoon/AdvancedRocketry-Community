package io.github.sunthemoon.advancedrocketrycommunity.celestial.environment;

import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/** Passive protection is server tag authority, independent of oxygen availability or adapters. */
public final class EnvironmentalProtection {
    public static final TagKey<Item> THERMAL = tag("thermal_protection");
    public static final TagKey<Item> PRESSURE = tag("pressure_protection");
    public static final TagKey<Item> SOLAR = tag("solar_protection");
    private static final EquipmentSlot[] SLOTS = {
            EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};

    private EnvironmentalProtection() { }

    public static EnvironmentalExposure.Protection read(LivingEntity entity) {
        boolean thermal = true;
        boolean pressure = true;
        boolean solar = true;
        for (EquipmentSlot slot : SLOTS) {
            ItemStack stack = entity.getItemBySlot(slot);
            if (stack.getCount() != 1 || !(stack.getItem() instanceof ArmorItem armor)
                    || armor.getEquipmentSlot() != slot) {
                return EnvironmentalExposure.Protection.NONE;
            }
            thermal &= stack.is(THERMAL);
            pressure &= stack.is(PRESSURE);
            solar &= stack.is(SOLAR);
        }
        return new EnvironmentalExposure.Protection(thermal, pressure, solar);
    }

    private static TagKey<Item> tag(String path) {
        return TagKey.create(Registries.ITEM, ModIdentity.id(path));
    }
}
