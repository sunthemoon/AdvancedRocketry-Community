package io.github.sunthemoon.advancedrocketrycommunity.equipment.suit;

import io.github.sunthemoon.advancedrocketrycommunity.persistence.BoundedNbt;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;

/** Preflight before any recursive copy, comparison or component deserialization. */
public final class EquipmentNbt {
    public static boolean bounded(Tag tag) { return BoundedNbt.fits(tag, 16_384, 16, 256); }
    public static boolean safe(ItemStack stack) {
        return stack.isEmpty() || stack.getCount() == 1 && stack.getMaxStackSize() == 1
                && (!stack.hasTag() || bounded(stack.getTag()))
                && bounded(stack.serializeNBT());
    }
    private EquipmentNbt() { }
}
