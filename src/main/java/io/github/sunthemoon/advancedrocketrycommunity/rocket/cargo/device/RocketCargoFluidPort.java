package io.github.sunthemoon.advancedrocketrycommunity.rocket.cargo.device;

import io.github.sunthemoon.advancedrocketrycommunity.rocket.fuel.loader.LoaderTank;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;
import org.jetbrains.annotations.NotNull;

/** All-side, epoch-bound local port: LOAD only fills; UNLOAD only drains. It never reaches rocket resources. */
final class RocketCargoFluidPort implements IFluidHandler {
    private final RocketFluidCargoBlockEntity owner;
    private final long epoch;

    RocketCargoFluidPort(RocketFluidCargoBlockEntity owner, long epoch) { this.owner = owner; this.epoch = epoch; }
    @Override public int getTanks() { return 1; }
    @Override public int getTankCapacity(int tank) { check(tank); return LoaderTank.CAPACITY; }
    @Override @NotNull public FluidStack getFluidInTank(int tank) {
        check(tank);
        return owner.currentPort(epoch) && owner.live() && owner.claimed() && !owner.localInProgress()
                ? owner.tank().fluidStack() : FluidStack.EMPTY;
    }
    @Override public boolean isFluidValid(int tank, @NotNull FluidStack offered) {
        check(tank);
        if (!owner.currentPort(epoch) || owner.role() != RocketCargoRole.LOAD || !owner.beginLocal()) { return false; }
        try { return RocketCargoLocalTank.intake(owner.tank(), offered) != null; }
        finally { owner.endLocal(); }
    }
    @Override public int fill(FluidStack offered, FluidAction action) {
        if (!owner.currentPort(epoch) || owner.role() != RocketCargoRole.LOAD || !owner.beginLocal()) { return 0; }
        try {
            LoaderTank before = owner.tank();
            LoaderTank unit = RocketCargoLocalTank.intake(before, offered);
            int amount = unit == null ? 0 : Math.min(offered.getAmount(), before.room());
            if (amount <= 0) { return 0; }
            if (action.execute()) { owner.writeTank(unit.withAmount(before.amount() + amount)); }
            return amount;
        } finally { owner.endLocal(); }
    }
    @Override @NotNull public FluidStack drain(FluidStack matching, FluidAction action) {
        return matching == null ? FluidStack.EMPTY : drain(0, matching, action);
    }
    @Override @NotNull public FluidStack drain(int amount, FluidAction action) { return drain(amount, null, action); }
    private FluidStack drain(int maximum, FluidStack matching, FluidAction action) {
        if (!owner.currentPort(epoch) || owner.role() != RocketCargoRole.UNLOAD || !owner.beginLocal()) { return FluidStack.EMPTY; }
        try {
            // Inspect caller-controlled stacks only after acquiring the same non-reentrant lock as fill.
            if (matching != null) { maximum = matching.getAmount(); }
            LoaderTank before = owner.tank();
            if (before.isEmpty() || maximum <= 0 || matching != null && !before.fluidStack().isFluidEqual(matching)) {
                return FluidStack.EMPTY;
            }
            int amount = Math.min(maximum, before.amount());
            FluidStack result = before.withAmount(amount).fluidStack();
            if (action.execute()) { owner.writeTank(before.withAmount(before.amount() - amount)); }
            return result;
        } finally { owner.endLocal(); }
    }
    private static void check(int tank) { if (tank != 0) { throw new IndexOutOfBoundsException("Tank index must be zero"); } }
}
