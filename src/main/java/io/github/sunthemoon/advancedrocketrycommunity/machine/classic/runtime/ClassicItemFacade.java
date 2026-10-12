package io.github.sunthemoon.advancedrocketrycommunity.machine.classic.runtime;

import io.github.sunthemoon.advancedrocketrycommunity.machine.classic.resource.ClassicBankKind;
import io.github.sunthemoon.advancedrocketrycommunity.machine.classic.resource.ClassicBankTransfers;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.items.IItemHandler;

/** Exact-instance, epoch-scoped facade; detached references cannot revive after a rebind. */
final class ClassicItemFacade implements IItemHandler {
    private final ClassicHatchBlockEntity hatch; private final long epoch;
    ClassicItemFacade(ClassicHatchBlockEntity hatch, long epoch) { this.hatch = hatch; this.epoch = epoch; }
    @Override public int getSlots() { return 4; }
    private void slot(int slot) { if (slot < 0 || slot >= 4) { throw new IllegalArgumentException("Item slot"); } }
    @Override public ItemStack getStackInSlot(int slot) {
        slot(slot); var owner = hatch.owner(epoch); if (owner == null) { return ItemStack.EMPTY; }
        return owner.access(hatch, epoch, resources -> new ClassicControllerBlockEntity.Edit<>(resources,
                resources.bank(hatch.bankKey()).orElseThrow().item(slot)), ItemStack.EMPTY);
    }
    @Override public ItemStack insertItem(int slot, ItemStack offered, boolean simulate) {
        slot(slot); var owner = hatch.owner(epoch);
        if (owner == null || hatch.bankKey().kind() != ClassicBankKind.ITEM_INPUT || offered.isEmpty()) { return offered.copy(); }
        return owner.access(hatch, epoch, resources -> {
            ItemStack unit = offered.copy(); unit.setCount(Math.min(64, unit.getCount()));
            var result = ClassicBankTransfers.insertItem(resources, hatch.bankKey(), slot, unit, simulate);
            ItemStack remainder = offered.copy(); remainder.shrink(result.accepted());
            return new ClassicControllerBlockEntity.Edit<>(result.resources(), remainder);
        }, offered.copy());
    }
    @Override public ItemStack extractItem(int slot, int amount, boolean simulate) {
        slot(slot); var owner = hatch.owner(epoch);
        if (owner == null || amount <= 0 || hatch.bankKey().kind() != ClassicBankKind.ITEM_OUTPUT) { return ItemStack.EMPTY; }
        return owner.access(hatch, epoch, resources -> {
            var result = ClassicBankTransfers.extractItem(resources, hatch.bankKey(), slot, amount, simulate);
            return new ClassicControllerBlockEntity.Edit<>(result.resources(), result.extracted());
        }, ItemStack.EMPTY);
    }
    @Override public int getSlotLimit(int slot) { slot(slot); return 64; }
    @Override public boolean isItemValid(int slot, ItemStack stack) {
        slot(slot); return hatch.owner(epoch) != null && hatch.bankKey().kind() == ClassicBankKind.ITEM_INPUT;
    }
}
