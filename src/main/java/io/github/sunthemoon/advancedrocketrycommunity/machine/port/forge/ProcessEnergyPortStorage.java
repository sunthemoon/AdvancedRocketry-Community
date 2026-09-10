package io.github.sunthemoon.advancedrocketrycommunity.machine.port.forge;

import io.github.sunthemoon.advancedrocketrycommunity.machine.port.ProcessPortDefinition;
import io.github.sunthemoon.advancedrocketrycommunity.machine.port.ProcessPortKind;
import io.github.sunthemoon.advancedrocketrycommunity.machine.port.ProcessPortRevision;
import java.util.Objects;
import java.util.function.BooleanSupplier;
import net.minecraftforge.energy.IEnergyStorage;

/** Mode-enforcing Energy capability view with shared process revision tracking. */
public final class ProcessEnergyPortStorage implements IEnergyStorage {
    private final IEnergyStorage delegate;
    private final ProcessPortDefinition port;
    private final BooleanSupplier processLocked;
    private final BooleanSupplier accessAllowed;
    private final ProcessPortRevision revision;

    public ProcessEnergyPortStorage(
            IEnergyStorage delegate,
            ProcessPortDefinition port,
            BooleanSupplier processLocked,
            ProcessPortRevision revision
    ) {
        this(delegate, port, processLocked, () -> true, revision);
    }

    public ProcessEnergyPortStorage(
            IEnergyStorage delegate,
            ProcessPortDefinition port,
            BooleanSupplier processLocked,
            BooleanSupplier accessAllowed,
            ProcessPortRevision revision
    ) {
        this.delegate = Objects.requireNonNull(delegate, "delegate");
        this.port = Objects.requireNonNull(port, "port");
        this.processLocked = Objects.requireNonNull(processLocked, "processLocked");
        this.accessAllowed = Objects.requireNonNull(accessAllowed, "accessAllowed");
        this.revision = Objects.requireNonNull(revision, "revision");
        if (port.kind() != ProcessPortKind.ENERGY) {
            throw new IllegalArgumentException("energy view requires an energy port");
        }
    }

    @Override
    public int receiveEnergy(int maxReceive, boolean simulate) {
        if (maxReceive <= 0
                || !accessAllowed.getAsBoolean()
                || !port.canInsert(processLocked.getAsBoolean())) {
            return 0;
        }
        int received = delegate.receiveEnergy(maxReceive, simulate);
        if (!simulate && received > 0) {
            revision.recordMutation();
        }
        return received;
    }

    @Override
    public int extractEnergy(int maxExtract, boolean simulate) {
        if (maxExtract <= 0
                || !accessAllowed.getAsBoolean()
                || !port.canExtract(processLocked.getAsBoolean())) {
            return 0;
        }
        int extracted = delegate.extractEnergy(maxExtract, simulate);
        if (!simulate && extracted > 0) {
            revision.recordMutation();
        }
        return extracted;
    }

    @Override
    public int getEnergyStored() {
        return accessAllowed.getAsBoolean() ? delegate.getEnergyStored() : 0;
    }

    @Override
    public int getMaxEnergyStored() {
        return delegate.getMaxEnergyStored();
    }

    @Override
    public boolean canExtract() {
        return accessAllowed.getAsBoolean()
                && port.canExtract(processLocked.getAsBoolean())
                && delegate.canExtract();
    }

    @Override
    public boolean canReceive() {
        return accessAllowed.getAsBoolean()
                && port.canInsert(processLocked.getAsBoolean())
                && delegate.canReceive();
    }
}
