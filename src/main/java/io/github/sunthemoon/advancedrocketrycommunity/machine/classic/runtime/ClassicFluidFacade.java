package io.github.sunthemoon.advancedrocketrycommunity.machine.classic.runtime;

import io.github.sunthemoon.advancedrocketrycommunity.machine.classic.resource.ClassicBankKind;
import io.github.sunthemoon.advancedrocketrycommunity.machine.classic.resource.ClassicBankTransfers;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;

/** One controller bank, never a persistent FluidTank at the physical hatch. */
final class ClassicFluidFacade implements IFluidHandler {
    private final ClassicHatchBlockEntity hatch; private final long epoch;
    ClassicFluidFacade(ClassicHatchBlockEntity hatch, long epoch) { this.hatch = hatch; this.epoch = epoch; }
    @Override public int getTanks() { return 1; }
    private void tank(int tank) { if (tank != 0) { throw new IllegalArgumentException("Fluid tank"); } }
    @Override public FluidStack getFluidInTank(int tank) {
        tank(tank); var owner = hatch.owner(epoch); if (owner == null) { return FluidStack.EMPTY; }
        return owner.access(hatch, epoch, resources -> new ClassicControllerBlockEntity.Edit<>(resources,
                resources.bank(hatch.bankKey()).orElseThrow().fluid()), FluidStack.EMPTY);
    }
    @Override public int getTankCapacity(int tank) { tank(tank); return 16_000; }
    @Override public boolean isFluidValid(int tank, FluidStack stack) {
        tank(tank); return hatch.owner(epoch) != null && hatch.bankKey().kind() == ClassicBankKind.FLUID_INPUT;
    }
    @Override public int fill(FluidStack offered, FluidAction action) {
        var owner = hatch.owner(epoch);
        if (owner == null || offered.isEmpty() || hatch.bankKey().kind() != ClassicBankKind.FLUID_INPUT) { return 0; }
        return owner.access(hatch, epoch, resources -> {
            FluidStack unit = offered.copy(); unit.setAmount(Math.min(16_000, unit.getAmount()));
            var result = ClassicBankTransfers.fillFluid(resources, hatch.bankKey(), unit, action.simulate());
            return new ClassicControllerBlockEntity.Edit<>(result.resources(), result.accepted());
        }, 0);
    }
    @Override public FluidStack drain(FluidStack requested, FluidAction action) {
        var owner = hatch.owner(epoch);
        if (owner == null || requested.isEmpty() || hatch.bankKey().kind() != ClassicBankKind.FLUID_OUTPUT) { return FluidStack.EMPTY; }
        return owner.access(hatch, epoch, resources -> {
            FluidStack unit = requested.copy(); unit.setAmount(Math.min(16_000, unit.getAmount()));
            var result = ClassicBankTransfers.drainFluid(resources, hatch.bankKey(), unit, action.simulate());
            return new ClassicControllerBlockEntity.Edit<>(result.resources(), result.extracted());
        }, FluidStack.EMPTY);
    }
    @Override public FluidStack drain(int amount, FluidAction action) {
        if (amount <= 0) { return FluidStack.EMPTY; }
        FluidStack request = getFluidInTank(0);
        if (request.isEmpty()) { return FluidStack.EMPTY; }
        request.setAmount(Math.min(16_000, amount)); return drain(request, action);
    }
}
