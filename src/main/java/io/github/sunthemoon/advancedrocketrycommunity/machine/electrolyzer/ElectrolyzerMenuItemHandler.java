package io.github.sunthemoon.advancedrocketrycommunity.machine.electrolyzer;

import java.util.Objects;
import java.util.function.Supplier;
import javax.annotation.Nonnull;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.items.IItemHandler;

/** Readable menu view whose mutations still pass through the shared revisioned port. */
final class ElectrolyzerMenuItemHandler implements IItemHandler {
    private final ElectrolyzerInventory inventory;
    private final Supplier<IItemHandler> mutations;

    ElectrolyzerMenuItemHandler(
            ElectrolyzerInventory inventory,
            Supplier<IItemHandler> mutations
    ) {
        this.inventory = Objects.requireNonNull(inventory, "inventory");
        this.mutations = Objects.requireNonNull(mutations, "mutations");
    }

    @Override
    public int getSlots() {
        return inventory.getSlots();
    }

    @Nonnull
    @Override
    public ItemStack getStackInSlot(int slot) {
        return inventory.getStackInSlot(slot).copy();
    }

    @Nonnull
    @Override
    public ItemStack insertItem(int slot, @Nonnull ItemStack stack, boolean simulate) {
        return mutations.get().insertItem(slot, stack, simulate);
    }

    @Nonnull
    @Override
    public ItemStack extractItem(int slot, int amount, boolean simulate) {
        return mutations.get().extractItem(slot, amount, simulate);
    }

    @Override
    public int getSlotLimit(int slot) {
        return inventory.getSlotLimit(slot);
    }

    @Override
    public boolean isItemValid(int slot, @Nonnull ItemStack stack) {
        return mutations.get().isItemValid(slot, stack);
    }
}
