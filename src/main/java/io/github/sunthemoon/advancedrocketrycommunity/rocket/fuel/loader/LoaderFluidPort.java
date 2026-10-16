package io.github.sunthemoon.advancedrocketrycommunity.rocket.fuel.loader;

import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;
import org.jetbrains.annotations.NotNull;

/**
 * The loader's one epoch-bound port, for every side. A handle from before a lifecycle event, a call off the server
 * thread and a call made while another is in progress all return zero or empty; simulation never mutates. The port
 * only reaches the device's own tank, never a rocket.
 */
final class LoaderFluidPort implements IFluidHandler {
    private final FluidFuelLoaderBlockEntity owner;
    private final long epoch;

    LoaderFluidPort(FluidFuelLoaderBlockEntity owner, long epoch) {
        this.owner = owner;
        this.epoch = epoch;
    }

    @Override
    public int getTanks() {
        return 1;
    }

    @Override
    @NotNull
    public FluidStack getFluidInTank(int tank) {
        check(tank);
        return owner.currentPort(epoch) && owner.live() && owner.claimed() ? owner.fluid() : FluidStack.EMPTY;
    }

    @Override
    public int getTankCapacity(int tank) {
        check(tank);
        return LoaderTank.CAPACITY;
    }

    @Override
    public boolean isFluidValid(int tank, @NotNull FluidStack resource) {
        check(tank);
        return owner.currentPort(epoch) && owner.validFluid(resource);
    }

    @Override
    public int fill(FluidStack resource, FluidAction action) {
        return owner.currentPort(epoch) ? owner.fill(resource, action) : 0;
    }

    @Override
    @NotNull
    public FluidStack drain(FluidStack resource, FluidAction action) {
        return owner.currentPort(epoch) && resource != null ? owner.drain(0, resource, action) : FluidStack.EMPTY;
    }

    @Override
    @NotNull
    public FluidStack drain(int maximum, FluidAction action) {
        return owner.currentPort(epoch) ? owner.drain(maximum, null, action) : FluidStack.EMPTY;
    }

    private static void check(int tank) {
        if (tank != 0) {
            throw new IndexOutOfBoundsException("Tank index must be zero");
        }
    }
}
