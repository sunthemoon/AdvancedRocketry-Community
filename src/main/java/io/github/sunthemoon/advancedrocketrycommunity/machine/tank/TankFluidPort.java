package io.github.sunthemoon.advancedrocketrycommunity.machine.tank;

import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;
import org.jetbrains.annotations.NotNull;

/** Epoch-bound all-side port; simulate never mutates, and views cannot alias the native snapshot. */
final class TankFluidPort implements IFluidHandler {
    private final PressurizedTankBlockEntity owner;
    private final long epoch;
    TankFluidPort(PressurizedTankBlockEntity owner, long epoch) { this.owner = owner; this.epoch = epoch; }
    @Override public int getTanks() { return 1; }
    @Override @NotNull public FluidStack getFluidInTank(int tank) {
        check(tank); return owner.currentPort(epoch) ? owner.fluidState() : FluidStack.EMPTY;
    }
    @Override public int getTankCapacity(int tank) { check(tank); return owner.capacity(); }
    @Override public boolean isFluidValid(int tank, @NotNull FluidStack resource) {
        check(tank);
        return owner.currentPort(epoch) && owner.validFluid(resource);
    }
    @Override public int fill(FluidStack resource, FluidAction action) {
        return owner.currentPort(epoch) ? owner.fill(resource, action) : 0;
    }
    @Override @NotNull public FluidStack drain(FluidStack resource, FluidAction action) {
        return owner.currentPort(epoch) ? owner.drain(0, resource, action) : FluidStack.EMPTY;
    }
    @Override @NotNull public FluidStack drain(int maximum, FluidAction action) {
        return owner.currentPort(epoch) ? owner.drain(maximum, null, action) : FluidStack.EMPTY;
    }
    private static void check(int tank) { if (tank != 0) { throw new IndexOutOfBoundsException("Tank index must be zero"); } }
}
