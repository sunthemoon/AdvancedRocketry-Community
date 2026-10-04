package io.github.sunthemoon.advancedrocketrycommunity.machine.pump;

import net.minecraftforge.energy.IEnergyStorage;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;

/** Epoch-bound, defensive-copy automation ports. All accounting remains in the owning BE. */
final class PumpPorts {
    record Energy(PumpBlockEntity pump, long epoch) implements IEnergyStorage {
        private boolean live() { return pump.currentPort(epoch); }
        @Override public int receiveEnergy(int maximum, boolean simulate) { return live() ? pump.receiveEnergy(maximum, simulate) : 0; }
        @Override public int extractEnergy(int maximum, boolean simulate) { return 0; }
        @Override public int getEnergyStored() { return live() ? pump.energy() : 0; }
        @Override public int getMaxEnergyStored() { return live() ? PumpBudget.ENERGY : 0; }
        @Override public boolean canExtract() { return false; }
        @Override public boolean canReceive() { return live(); }
    }
    record Fluid(PumpBlockEntity pump, long epoch) implements IFluidHandler {
        private boolean live() { return pump.currentPort(epoch); }
        @Override public int getTanks() { return live() ? 1 : 0; }
        @Override public FluidStack getFluidInTank(int tank) { return live() && tank == 0 ? pump.fluid() : FluidStack.EMPTY; }
        @Override public int getTankCapacity(int tank) { return live() && tank == 0 ? PumpBudget.TANK : 0; }
        @Override public boolean isFluidValid(int tank, FluidStack stack) {
            return live() && tank == 0 && pump.validFluid(stack);
        }
        @Override public int fill(FluidStack stack, FluidAction action) { return live() ? pump.fill(stack, action.execute()) : 0; }
        @Override public FluidStack drain(FluidStack stack, FluidAction action) {
            return live() ? pump.drain(stack, action.execute()) : FluidStack.EMPTY;
        }
        @Override public FluidStack drain(int maximum, FluidAction action) {
            return live() ? pump.drain(maximum, action.execute()) : FluidStack.EMPTY;
        }
    }
    private PumpPorts() { }
}
