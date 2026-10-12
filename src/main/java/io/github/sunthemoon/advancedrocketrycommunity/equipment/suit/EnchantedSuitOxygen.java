package io.github.sunthemoon.advancedrocketrycommunity.equipment.suit;

import java.util.Set;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;

/** Private native-armor extension; an external provider always wins in the suit service. */
public final class EnchantedSuitOxygen {
    public static final String KEY = "arce_enchanted_suit_oxygen";
    public static boolean eligible(ItemStack stack, EquipmentSlot slot) {
        return EquipmentNbt.safe(stack) && stack.getItem() instanceof ArmorItem armor
                && armor.getEquipmentSlot() == slot && SuitEquipmentContent.SPACE_BREATHING.isPresent()
                && EnchantmentHelper.getItemEnchantmentLevel(SuitEquipmentContent.SPACE_BREATHING.get(), stack) == 1;
    }
    /** -1 means unusable and preserved; old enchanted chests start empty. */
    public static int read(CompoundTag armor) {
        if (armor == null || !armor.contains(KEY)) { return 0; }
        if (!(armor.get(KEY) instanceof CompoundTag root) || !EquipmentNbt.bounded(root)
                || !root.getAllKeys().equals(Set.of("schema_version", "oxygen_units"))
                || !root.contains("schema_version", Tag.TAG_INT) || root.getInt("schema_version") != 1
                || !root.contains("oxygen_units", Tag.TAG_INT)) { return -1; }
        int amount = root.getInt("oxygen_units");
        return amount >= 0 && amount <= 2_000 ? amount : -1;
    }
    public static boolean set(ItemStack stack, int units) {
        if (!eligible(stack, EquipmentSlot.CHEST) || read(stack.getTag()) < 0 || units < 0 || units > 2_000) { return false; }
        CompoundTag outer = stack.hasTag() ? stack.getTag().copy() : new CompoundTag();
        CompoundTag root = new CompoundTag(); root.putInt("schema_version", 1); root.putInt("oxygen_units", units);
        outer.put(KEY, root);
        if (!EquipmentNbt.bounded(outer)) { return false; }
        stack.setTag(outer); return true;
    }
    private EnchantedSuitOxygen() { }
}
