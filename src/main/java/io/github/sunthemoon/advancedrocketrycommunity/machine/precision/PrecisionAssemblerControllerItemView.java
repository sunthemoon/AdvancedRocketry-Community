package io.github.sunthemoon.advancedrocketrycommunity.machine.precision;

import java.util.Objects;
import javax.annotation.Nonnull;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.items.IItemHandler;

/** One-slot delegate that follows the loaded, current controller generation. */
final class PrecisionAssemblerControllerItemView implements IItemHandler {
    private final PrecisionAssemblerPortBlockEntity port;

    PrecisionAssemblerControllerItemView(PrecisionAssemblerPortBlockEntity port) {
        this.port = Objects.requireNonNull(port, "port");
    }

    @Override
    public int getSlots() {
        return 1;
    }

    @Nonnull
    @Override
    public ItemStack getStackInSlot(int slot) {
        requireSingleSlot(slot);
        return port.itemOwner().map(owner -> owner.controller().storedItemCopy(owner.index()))
                .orElse(ItemStack.EMPTY);
    }

    @Nonnull
    @Override
    public ItemStack insertItem(int slot, @Nonnull ItemStack stack, boolean simulate) {
        requireSingleSlot(slot);
        return port.itemOwner().map(owner ->
                owner.controller().itemBank().insertItem(owner.index(), stack, simulate))
                .orElse(stack);
    }

    @Nonnull
    @Override
    public ItemStack extractItem(int slot, int amount, boolean simulate) {
        requireSingleSlot(slot);
        return port.itemOwner().map(owner ->
                owner.controller().itemBank().extractItem(owner.index(), amount, simulate))
                .orElse(ItemStack.EMPTY);
    }

    @Override
    public int getSlotLimit(int slot) {
        requireSingleSlot(slot);
        return port.itemOwner().map(owner ->
                owner.controller().itemBank().getSlotLimit(owner.index()))
                .orElse(PrecisionAssemblerRecipe.MAX_ITEM_COUNT);
    }

    @Override
    public boolean isItemValid(int slot, @Nonnull ItemStack stack) {
        requireSingleSlot(slot);
        return port.itemOwner().map(owner ->
                owner.controller().itemBank().isItemValid(owner.index(), stack))
                .orElse(false);
    }

    private static void requireSingleSlot(int slot) {
        if (slot != 0) {
            throw new IndexOutOfBoundsException("Precision Item port exposes one slot");
        }
    }
}
