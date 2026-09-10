package io.github.sunthemoon.advancedrocketrycommunity.machine.port.forge;

import io.github.sunthemoon.advancedrocketrycommunity.machine.port.ProcessPortDefinition;
import io.github.sunthemoon.advancedrocketrycommunity.machine.port.ProcessPortKind;
import io.github.sunthemoon.advancedrocketrycommunity.machine.port.ProcessPortRevision;
import java.util.List;
import java.util.Objects;
import java.util.function.BooleanSupplier;
import javax.annotation.Nonnull;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.IFluidTank;
import net.minecraftforge.fluids.capability.IFluidHandler;

/** Precise tank-range Fluid capability view that cannot spill into hidden tanks. */
public final class ProcessFluidPortHandler implements IFluidHandler {
    private final List<? extends IFluidTank> tanks;
    private final ProcessPortDefinition port;
    private final BooleanSupplier processLocked;
    private final BooleanSupplier accessAllowed;
    private final ProcessPortRevision revision;

    public ProcessFluidPortHandler(
            List<? extends IFluidTank> tanks,
            ProcessPortDefinition port,
            BooleanSupplier processLocked,
            ProcessPortRevision revision
    ) {
        this(tanks, port, processLocked, () -> true, revision);
    }

    public ProcessFluidPortHandler(
            List<? extends IFluidTank> tanks,
            ProcessPortDefinition port,
            BooleanSupplier processLocked,
            BooleanSupplier accessAllowed,
            ProcessPortRevision revision
    ) {
        this.tanks = List.copyOf(Objects.requireNonNull(tanks, "tanks"));
        this.port = Objects.requireNonNull(port, "port");
        this.processLocked = Objects.requireNonNull(processLocked, "processLocked");
        this.accessAllowed = Objects.requireNonNull(accessAllowed, "accessAllowed");
        this.revision = Objects.requireNonNull(revision, "revision");
        if (port.kind() != ProcessPortKind.FLUID || port.range().endExclusive() > tanks.size()) {
            throw new IllegalArgumentException("fluid port does not fit its tank list");
        }
    }

    @Override
    public int getTanks() {
        return port.range().count();
    }

    @Nonnull
    @Override
    public FluidStack getFluidInTank(int tank) {
        return accessAllowed.getAsBoolean() ? tank(tank).getFluid().copy() : FluidStack.EMPTY;
    }

    @Override
    public int getTankCapacity(int tank) {
        return tank(tank).getCapacity();
    }

    @Override
    public boolean isFluidValid(int tank, @Nonnull FluidStack stack) {
        return accessAllowed.getAsBoolean()
                && port.canInsert(processLocked.getAsBoolean())
                && allows(stack)
                && tank(tank).isFluidValid(stack);
    }

    @Override
    public int fill(FluidStack resource, FluidAction action) {
        if (resource.isEmpty()
                || !accessAllowed.getAsBoolean()
                || !port.canInsert(processLocked.getAsBoolean())
                || !allows(resource)) {
            return 0;
        }
        int filled = 0;
        for (int index = 0; index < getTanks() && filled < resource.getAmount(); index++) {
            FluidStack remaining = resource.copy();
            remaining.setAmount(resource.getAmount() - filled);
            filled = Math.addExact(filled, tank(index).fill(remaining, action));
        }
        if (action.execute() && filled > 0) {
            revision.recordMutation();
        }
        return filled;
    }

    @Nonnull
    @Override
    public FluidStack drain(FluidStack resource, FluidAction action) {
        if (resource.isEmpty()
                || !accessAllowed.getAsBoolean()
                || !port.canExtract(processLocked.getAsBoolean())
                || !allows(resource)) {
            return FluidStack.EMPTY;
        }
        FluidStack drained = FluidStack.EMPTY;
        for (int index = 0; index < getTanks() && drained.getAmount() < resource.getAmount(); index++) {
            FluidStack request = resource.copy();
            request.setAmount(resource.getAmount() - drained.getAmount());
            FluidStack part = tank(index).drain(request, action);
            if (!part.isEmpty()) {
                if (drained.isEmpty()) {
                    drained = part.copy();
                } else if (drained.isFluidEqual(part)) {
                    drained.grow(part.getAmount());
                }
            }
        }
        recordDrain(action, drained);
        return drained;
    }

    @Nonnull
    @Override
    public FluidStack drain(int maxDrain, FluidAction action) {
        if (maxDrain <= 0
                || !accessAllowed.getAsBoolean()
                || !port.canExtract(processLocked.getAsBoolean())) {
            return FluidStack.EMPTY;
        }
        FluidStack drained = FluidStack.EMPTY;
        for (int index = 0; index < getTanks() && drained.getAmount() < maxDrain; index++) {
            FluidStack available = tank(index).getFluid();
            if (available.isEmpty() || !allows(available)
                    || (!drained.isEmpty() && !drained.isFluidEqual(available))) {
                continue;
            }
            FluidStack part = tank(index).drain(maxDrain - drained.getAmount(), action);
            if (drained.isEmpty()) {
                drained = part.copy();
            } else {
                drained.grow(part.getAmount());
            }
        }
        recordDrain(action, drained);
        return drained;
    }

    private IFluidTank tank(int index) {
        if (index < 0 || index >= getTanks()) {
            throw new IndexOutOfBoundsException("port tank is outside the exposed range");
        }
        return tanks.get(port.range().first() + index);
    }

    private boolean allows(FluidStack stack) {
        return port.filter().allows(BuiltInRegistries.FLUID.getKey(stack.getFluid()).toString());
    }

    private void recordDrain(FluidAction action, FluidStack drained) {
        if (action.execute() && !drained.isEmpty()) {
            revision.recordMutation();
        }
    }
}
