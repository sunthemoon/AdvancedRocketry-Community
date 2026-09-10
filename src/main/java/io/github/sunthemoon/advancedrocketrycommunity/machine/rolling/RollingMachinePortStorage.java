package io.github.sunthemoon.advancedrocketrycommunity.machine.rolling;

import java.util.Objects;
import javax.annotation.Nonnull;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.material.Fluids;
import net.minecraftforge.energy.EnergyStorage;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.templates.FluidTank;
import net.minecraftforge.items.ItemStackHandler;

final class RollingMachineItemStorage extends ItemStackHandler {
    private final Runnable changed;

    RollingMachineItemStorage(Runnable changed) {
        super(1);
        this.changed = Objects.requireNonNull(changed, "changed");
    }

    @Override
    public boolean isItemValid(int slot, @Nonnull ItemStack stack) {
        validateSlotIndex(slot);
        return RollingMachinePortPersistence.acceptsItem(stack);
    }

    @Override
    public void setStackInSlot(int slot, @Nonnull ItemStack stack) {
        if (!RollingMachinePortPersistence.acceptsItem(stack)) {
            throw new IllegalArgumentException("Rolling Machine port rejected invalid item data");
        }
        super.setStackInSlot(slot, stack.copy());
    }

    ItemStack storedCopy() {
        return getStackInSlot(0).copy();
    }

    void loadStored(ItemStack stack) {
        if (!RollingMachinePortPersistence.acceptsItem(stack)) {
            throw new IllegalArgumentException("Rolling Machine port rejected loaded item data");
        }
        stacks.set(0, stack.copy());
    }

    @Override
    protected void onContentsChanged(int slot) {
        changed.run();
    }
}

final class RollingMachineFluidStorage extends FluidTank {
    private final Runnable changed;

    RollingMachineFluidStorage(Runnable changed) {
        super(
                RollingMachinePortBlockEntity.FLUID_CAPACITY,
                stack -> stack.getFluid() == Fluids.WATER && !stack.hasTag()
        );
        this.changed = Objects.requireNonNull(changed, "changed");
    }

    FluidStack storedCopy() {
        return getFluid().copy();
    }

    void loadStored(FluidStack stack) {
        if (!stack.isEmpty()
                && (stack.getFluid() != Fluids.WATER
                || stack.hasTag()
                || stack.getAmount() > getCapacity())) {
            throw new IllegalArgumentException("Rolling Machine port rejected loaded fluid data");
        }
        fluid = stack.copy();
    }

    @Override
    protected void onContentsChanged() {
        changed.run();
    }
}

final class RollingMachineEnergyStorage extends EnergyStorage {
    private final Runnable changed;

    RollingMachineEnergyStorage(Runnable changed) {
        super(
                RollingMachinePortBlockEntity.ENERGY_CAPACITY,
                RollingMachinePortBlockEntity.ENERGY_RECEIVE_LIMIT,
                0,
                0
        );
        this.changed = Objects.requireNonNull(changed, "changed");
    }

    @Override
    public int receiveEnergy(int requested, boolean simulate) {
        if (requested <= 0) {
            return 0;
        }
        int received = super.receiveEnergy(requested, simulate);
        if (!simulate && received > 0) {
            changed.run();
        }
        return received;
    }

    void loadStored(int stored) {
        if (stored < 0 || stored > capacity) {
            throw new IllegalArgumentException("Rolling Machine port rejected loaded energy data");
        }
        energy = stored;
    }
}
