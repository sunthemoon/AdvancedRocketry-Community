package io.github.sunthemoon.advancedrocketrycommunity.machine.precision;

import java.util.Optional;
import javax.annotation.Nonnull;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.items.IItemHandler;

/** Seven menu Item slots resolved against the current loaded controller generation. */
final class PrecisionAssemblerMenuItemHandler implements IItemHandler {
    private final PrecisionAssemblerBlockEntity controller;

    PrecisionAssemblerMenuItemHandler(PrecisionAssemblerBlockEntity controller) {
        this.controller = java.util.Objects.requireNonNull(controller, "controller");
    }

    @Override
    public int getSlots() {
        return PrecisionAssemblerMenu.MACHINE_SLOT_COUNT;
    }

    @Nonnull
    @Override
    public ItemStack getStackInSlot(int slot) {
        return view(slot).map(value -> value.getStackInSlot(0)).orElse(ItemStack.EMPTY);
    }

    @Nonnull
    @Override
    public ItemStack insertItem(int slot, @Nonnull ItemStack stack, boolean simulate) {
        return view(slot).map(value -> value.insertItem(0, stack, simulate)).orElse(stack);
    }

    @Nonnull
    @Override
    public ItemStack extractItem(int slot, int amount, boolean simulate) {
        return view(slot).map(value -> value.extractItem(0, amount, simulate))
                .orElse(ItemStack.EMPTY);
    }

    @Override
    public int getSlotLimit(int slot) {
        return view(slot).map(value -> value.getSlotLimit(0))
                .orElse(PrecisionAssemblerRecipe.MAX_ITEM_COUNT);
    }

    @Override
    public boolean isItemValid(int slot, @Nonnull ItemStack stack) {
        return view(slot).map(value -> value.isItemValid(0, stack)).orElse(false);
    }

    private Optional<IItemHandler> view(int slot) {
        if (slot < 0 || slot >= PrecisionAssemblerMenu.MACHINE_SLOT_COUNT) {
            throw new IndexOutOfBoundsException("Precision Assembler menu slot is outside the fixed range");
        }
        if (!(controller.getLevel() instanceof ServerLevel level)) {
            return Optional.empty();
        }
        return PrecisionAssemblerPortSet.resolve(level, controller).flatMap(ports -> {
            PrecisionAssemblerPortBlockEntity port = slot < PrecisionAssemblerChannels.INPUT_COUNT
                    ? ports.inputs().get(slot)
                    : ports.outputs().get(slot - PrecisionAssemblerChannels.INPUT_COUNT);
            return port.menuItemView();
        });
    }
}
