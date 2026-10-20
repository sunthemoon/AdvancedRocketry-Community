package io.github.sunthemoon.advancedrocketrycommunity.rocket.cargo.device;

import io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.EscrowItemBuffer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.items.IItemHandler;
import org.jetbrains.annotations.NotNull;

/**
 * All-side, epoch-bound local port over the four slots: LOAD only inserts; UNLOAD only extracts. Caller-controlled
 * stacks are inspected only under the device lock. It never reaches rocket resources.
 */
final class RocketItemCargoPort implements IItemHandler {
    private final RocketItemCargoBlockEntity owner;
    private final long epoch;

    RocketItemCargoPort(RocketItemCargoBlockEntity owner, long epoch) { this.owner = owner; this.epoch = epoch; }
    @Override public int getSlots() { return EscrowItemBuffer.SLOTS; }
    @Override public int getSlotLimit(int slot) { check(slot); return 64; }
    @Override @NotNull public ItemStack getStackInSlot(int slot) {
        check(slot);
        return owner.currentPort(epoch) && owner.live() && owner.claimed() && !owner.localInProgress()
                ? owner.stack(slot) : ItemStack.EMPTY;
    }
    @Override public boolean isItemValid(int slot, @NotNull ItemStack offered) {
        check(slot);
        if (!owner.currentPort(epoch) || owner.role() != RocketCargoRole.LOAD || !owner.beginLocal()) { return false; }
        try { return RocketItemCargoLocal.unit(offered, Math.min(offered.getCount(), RocketItemCargoLocal.limit(offered))) != null; }
        finally { owner.endLocal(); }
    }
    @Override @NotNull public ItemStack insertItem(int slot, @NotNull ItemStack offered, boolean simulate) {
        check(slot);
        if (offered.isEmpty()) { return ItemStack.EMPTY; }
        if (!owner.currentPort(epoch) || owner.role() != RocketCargoRole.LOAD || !owner.beginLocal()) { return offered; }
        try { return RocketItemCargoLocal.insert(owner, slot, offered, simulate); }
        finally { owner.endLocal(); }
    }
    @Override @NotNull public ItemStack extractItem(int slot, int amount, boolean simulate) {
        check(slot);
        if (!owner.currentPort(epoch) || owner.role() != RocketCargoRole.UNLOAD || !owner.beginLocal()) { return ItemStack.EMPTY; }
        try { return RocketItemCargoLocal.extract(owner, slot, amount, simulate); }
        finally { owner.endLocal(); }
    }
    private static void check(int slot) {
        if (slot < 0 || slot >= EscrowItemBuffer.SLOTS) { throw new IndexOutOfBoundsException("Slot must be 0 to 3"); }
    }
}
