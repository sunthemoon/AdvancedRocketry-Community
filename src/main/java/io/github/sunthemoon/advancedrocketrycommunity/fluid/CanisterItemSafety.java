package io.github.sunthemoon.advancedrocketrycommunity.fluid;

import io.github.sunthemoon.advancedrocketrycommunity.persistence.BoundedNbt;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/** Bounded metadata preflight before a whole-unit canister swap or copy. */
final class CanisterItemSafety {
    static final int MAX_BYTES = 8_192;
    static final int MAX_DEPTH = 16;
    static final int MAX_NODES = 1_024;

    private CanisterItemSafety() { }

    static boolean safe(ItemStack stack) {
        if (stack.isEmpty()) { return false; }
        CompoundTag tag = stack.getTag();
        if (tag != null && !BoundedNbt.fits(tag, MAX_BYTES, MAX_DEPTH, MAX_NODES)) { return false; }
        // A gas canister has no private capability payload. Refuse attached serializable
        // capabilities instead of silently deleting them or copying an unknown resource.
        CompoundTag serialized = stack.save(new CompoundTag());
        return !serialized.contains("ForgeCaps")
                && (stack.getTag() == null || BoundedNbt.fits(stack.getTag(), MAX_BYTES, MAX_DEPTH, MAX_NODES))
                && BoundedNbt.fits(serialized, MAX_BYTES, MAX_DEPTH, MAX_NODES);
    }

    static ItemStack swap(ItemStack original, Item item) {
        ItemStack replacement = new ItemStack(item);
        CompoundTag tag = original.getTag();
        if (tag != null) { replacement.setTag(tag.copy()); }
        return replacement;
    }
}
