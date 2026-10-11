package io.github.sunthemoon.advancedrocketrycommunity.equipment.suit;

import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentCategory;
import net.minecraft.world.item.ItemStack;

/** Chemical-process-only sealing; no enchanting table, book, trade or random loot entry. */
public final class SpaceBreathingEnchantment extends Enchantment {
    public SpaceBreathingEnchantment() {
        super(Rarity.VERY_RARE, EnchantmentCategory.ARMOR,
                new EquipmentSlot[]{EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET});
    }
    @Override public boolean isTreasureOnly() { return true; }
    @Override public boolean isTradeable() { return false; }
    @Override public boolean isDiscoverable() { return false; }
    @Override public boolean isAllowedOnBooks() { return false; }
    @Override public boolean canApplyAtEnchantingTable(ItemStack stack) { return false; }
}
