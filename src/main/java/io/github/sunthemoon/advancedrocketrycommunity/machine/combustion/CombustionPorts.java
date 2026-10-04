package io.github.sunthemoon.advancedrocketrycommunity.machine.combustion;

import io.github.sunthemoon.advancedrocketrycommunity.config.CommonConfig;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.energy.IEnergyStorage;
import net.minecraftforge.items.IItemHandler;

/** Revocable views: retaining a resolved capability does not extend its lifecycle. */
final class CombustionPorts {
    record Items(CombustionGeneratorBlockEntity owner, long epoch) implements IItemHandler {
        private void slot(int slot) {
            if (slot != 0) {
                throw new IllegalArgumentException("Combustion fuel slot is zero");
            }
        }

        @Override public int getSlots() { return 1; }
        @Override public int getSlotLimit(int slot) { slot(slot); return 64; }
        @Override public ItemStack getStackInSlot(int slot) {
            slot(slot);
            return owner.currentPort(epoch) ? owner.fuelStack() : ItemStack.EMPTY;
        }
        @Override public boolean isItemValid(int slot, ItemStack stack) {
            slot(slot);
            return owner.currentPort(epoch) && owner.canInsert(stack);
        }
        @Override public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
            slot(slot);
            return owner.currentPort(epoch) ? owner.insertFuel(stack, simulate) : stack;
        }
        @Override public ItemStack extractItem(int slot, int amount, boolean simulate) {
            slot(slot);
            return owner.currentPort(epoch) ? owner.extractFuel(amount, simulate) : ItemStack.EMPTY;
        }
    }

    record Energy(CombustionGeneratorBlockEntity owner, long epoch) implements IEnergyStorage {
        @Override public int receiveEnergy(int maximum, boolean simulate) { return 0; }
        @Override public int extractEnergy(int maximum, boolean simulate) {
            return owner.currentPort(epoch) ? owner.extractEnergy(maximum, simulate) : 0;
        }
        @Override public int getEnergyStored() {
            return owner.currentPort(epoch) ? owner.burnState().energy() : 0;
        }
        @Override public int getMaxEnergyStored() { return CombustionBurn.CAPACITY; }
        @Override public boolean canExtract() {
            return owner.currentPort(epoch) && CommonConfig.combustionGeneratorEnabled();
        }
        @Override public boolean canReceive() { return false; }
    }

    private CombustionPorts() { }
}
