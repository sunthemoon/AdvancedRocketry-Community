package io.github.sunthemoon.advancedrocketrycommunity.machine.rolling;

import java.util.Optional;
import javax.annotation.Nonnull;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.items.IItemHandler;

/** Two logical menu slots that resolve only the current loaded and formed port generation. */
final class RollingMachineMenuItemHandler implements IItemHandler {
    private final RollingMachineBlockEntity controller;

    RollingMachineMenuItemHandler(RollingMachineBlockEntity controller) {
        this.controller = java.util.Objects.requireNonNull(controller, "controller");
    }

    @Override
    public int getSlots() {
        return RollingMachineMenu.MACHINE_SLOT_COUNT;
    }

    @Nonnull
    @Override
    public ItemStack getStackInSlot(int slot) {
        return view(slot).map(handler -> handler.getStackInSlot(0)).orElse(ItemStack.EMPTY);
    }

    @Nonnull
    @Override
    public ItemStack insertItem(int slot, @Nonnull ItemStack stack, boolean simulate) {
        return view(slot).map(handler -> handler.insertItem(0, stack, simulate)).orElse(stack);
    }

    @Nonnull
    @Override
    public ItemStack extractItem(int slot, int amount, boolean simulate) {
        return view(slot).map(handler -> handler.extractItem(0, amount, simulate))
                .orElse(ItemStack.EMPTY);
    }

    @Override
    public int getSlotLimit(int slot) {
        return view(slot).map(handler -> handler.getSlotLimit(0))
                .orElse(RollingMachineRecipe.MAX_ITEM_COUNT);
    }

    @Override
    public boolean isItemValid(int slot, @Nonnull ItemStack stack) {
        return view(slot).map(handler -> handler.isItemValid(0, stack)).orElse(false);
    }

    private Optional<IItemHandler> view(int slot) {
        requireSlot(slot);
        if (!(controller.getLevel() instanceof ServerLevel level)) {
            return Optional.empty();
        }
        return RollingMachinePortSet.resolve(level, controller).flatMap(ports -> switch (slot) {
            case RollingMachineMenu.SLOT_INPUT -> ports.itemInput().menuItemView();
            case RollingMachineMenu.SLOT_OUTPUT -> ports.itemOutput().menuItemView();
            default -> Optional.empty();
        });
    }

    private static void requireSlot(int slot) {
        if (slot < 0 || slot >= RollingMachineMenu.MACHINE_SLOT_COUNT) {
            throw new IndexOutOfBoundsException("Rolling Machine menu slot is outside the fixed range");
        }
    }
}
