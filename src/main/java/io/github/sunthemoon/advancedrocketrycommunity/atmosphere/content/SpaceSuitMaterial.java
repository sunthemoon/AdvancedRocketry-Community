package io.github.sunthemoon.advancedrocketrycommunity.atmosphere.content;

import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;

/** Project texture identity with exactly the numeric properties of the former iron suit. */
public enum SpaceSuitMaterial implements ArmorMaterial {
    INSTANCE;

    @Override public int getDurabilityForType(ArmorItem.Type type) {
        return switch (type) {
            case HELMET -> 165;
            case CHESTPLATE -> 240;
            case LEGGINGS -> 225;
            case BOOTS -> 195;
        };
    }

    @Override public int getDefenseForType(ArmorItem.Type type) {
        return switch (type) {
            case HELMET, BOOTS -> 2;
            case CHESTPLATE -> 6;
            case LEGGINGS -> 5;
        };
    }

    @Override public int getEnchantmentValue() { return 9; }
    @Override public SoundEvent getEquipSound() { return SoundEvents.ARMOR_EQUIP_IRON; }
    @Override public Ingredient getRepairIngredient() { return Ingredient.of(Items.IRON_INGOT); }
    @Override public String getName() { return ModIdentity.MOD_ID + ":space_suit"; }
    @Override public float getToughness() { return 0.0F; }
    @Override public float getKnockbackResistance() { return 0.0F; }
}
