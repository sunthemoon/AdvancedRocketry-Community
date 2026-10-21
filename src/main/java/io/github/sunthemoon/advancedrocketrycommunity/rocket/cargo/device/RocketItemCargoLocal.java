package io.github.sunthemoon.advancedrocketrycommunity.rocket.cargo.device;

import io.github.sunthemoon.advancedrocketrycommunity.persistence.BoundedNbt;
import io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.EscrowItemStackCodec;
import javax.annotation.Nullable;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;

/**
 * Ordinary local storage of the four-slot buffer, outside cargo escrow. Every method but {@link #unit} runs under the
 * device's non-reentrant lock; a stack outside section 5.3's bounds, or one whose root would not fit, stays where it is.
 */
final class RocketItemCargoLocal {
    private RocketItemCargoLocal() { }

    /** Section 5.3's count bound, {@code min(64, getMaxStackSize())}. */
    static int limit(ItemStack stack) { return Math.min(64, stack.getMaxStackSize()); }

    /**
     * The saved form of {@code count} items of {@code offered}, or null outside section 5.3's bounds. The live tag is
     * measured before anything copies it, so an oversized tag is refused without unbounded work.
     */
    @Nullable static CompoundTag unit(ItemStack offered, int count) {
        if (offered == null || offered.isEmpty() || count < 1 || count > limit(offered)) { return null; }
        CompoundTag tag = offered.getTag();
        if (tag != null && !BoundedNbt.fits(tag, EscrowItemStackCodec.MAX_BYTES, EscrowItemStackCodec.MAX_DEPTH - 1,
                EscrowItemStackCodec.MAX_NODES - 3)) { return null; }
        try { return EscrowItemStackCodec.encode(offered.copyWithCount(count)); }
        catch (IllegalArgumentException refused) { return null; }
    }

    static CompoundTag withCount(CompoundTag saved, int count) {
        CompoundTag copy = saved.copy();
        copy.putByte("Count", (byte) count);
        return copy;
    }

    static ItemStack stack(CompoundTag saved) { return saved.isEmpty() ? ItemStack.EMPTY : ItemStack.of(saved); }

    /** Inserts into one slot, merging only the same item and tags (D-K's rule); returns the remainder. */
    static ItemStack insert(RocketItemCargoBlockEntity device, int slot, ItemStack offered, boolean simulate) {
        if (offered.isEmpty()) { return ItemStack.EMPTY; }
        CompoundTag current = device.buffer().slots().get(slot);
        int present = current.isEmpty() ? 0 : current.getByte("Count");
        int moved = Math.min(offered.getCount(), limit(offered) - present);
        if (moved <= 0) { return offered; }
        CompoundTag next;
        if (current.isEmpty()) {
            next = unit(offered, moved);
        } else {
            CompoundTag probe = unit(offered, 1);
            next = probe != null && merges(current, probe) ? withCount(current, present + moved) : null;
        }
        if (next == null || !device.slotFits(slot, next)) { return offered; }
        if (!simulate) { device.writeSlot(slot, next); }
        return moved == offered.getCount() ? ItemStack.EMPTY : offered.copyWithCount(offered.getCount() - moved);
    }

    /** Takes up to {@code amount} from one slot; the remainder keeps its exact saved form. */
    static ItemStack extract(RocketItemCargoBlockEntity device, int slot, int amount, boolean simulate) {
        CompoundTag current = device.buffer().slots().get(slot);
        if (amount <= 0 || current.isEmpty()) { return ItemStack.EMPTY; }
        int present = current.getByte("Count");
        int moved = Math.min(amount, present);
        if (!simulate) { device.writeSlot(slot, moved == present ? new CompoundTag() : withCount(current, present - moved)); }
        return stack(withCount(current, moved));
    }

    /** Whether {@code stack}, at a count the slot can hold, may replace the slot's content. */
    static boolean placeable(RocketItemCargoBlockEntity device, int slot, ItemStack stack) {
        CompoundTag next = unit(stack, Math.min(stack.getCount(), limit(stack)));
        return next != null && device.slotFits(slot, next);
    }

    /** Replaces the slot's content with exactly {@code stack}; false, with nothing changed, when it does not fit. */
    static boolean replace(RocketItemCargoBlockEntity device, int slot, ItemStack stack) {
        CompoundTag next = stack.isEmpty() ? new CompoundTag() : unit(stack, stack.getCount());
        if (next == null || !device.slotFits(slot, next)) { return false; }
        device.writeSlot(slot, next);
        return true;
    }

    private static boolean merges(CompoundTag present, CompoundTag offered) {
        try { return EscrowItemStackCodec.sameItemAndTags(present, offered); }
        catch (IllegalArgumentException refused) { return false; }
    }
}
