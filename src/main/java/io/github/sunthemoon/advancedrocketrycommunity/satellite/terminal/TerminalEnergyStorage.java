package io.github.sunthemoon.advancedrocketrycommunity.satellite.terminal;

import java.util.function.BooleanSupplier;
import net.minecraftforge.energy.IEnergyStorage;

/** The terminal's receive-only FE buffer; a blocked root accepts nothing. */
final class TerminalEnergyStorage implements IEnergyStorage {
    private final BooleanSupplier blocked;
    private final Runnable changed;
    private int stored;

    TerminalEnergyStorage(BooleanSupplier blocked, Runnable changed) {
        this.blocked = blocked;
        this.changed = changed;
    }

    @Override
    public int receiveEnergy(int maximum, boolean simulate) {
        if (blocked.getAsBoolean()) {
            return 0;
        }
        int received = Math.min(Math.max(0, maximum), SatelliteTerminalBlockEntity.ENERGY_CAPACITY - stored);
        if (!simulate && received > 0) {
            stored += received;
            changed.run();
        }
        return received;
    }

    @Override
    public int extractEnergy(int maximum, boolean simulate) {
        return 0;
    }

    @Override
    public int getEnergyStored() {
        return stored;
    }

    @Override
    public int getMaxEnergyStored() {
        return SatelliteTerminalBlockEntity.ENERGY_CAPACITY;
    }

    @Override
    public boolean canExtract() {
        return false;
    }

    @Override
    public boolean canReceive() {
        return !blocked.getAsBoolean();
    }

    void add(int amount) {
        stored = Math.min(SatelliteTerminalBlockEntity.ENERGY_CAPACITY, Math.addExact(stored, amount));
        changed.run();
    }

    void consume(int amount) {
        if (amount < 0 || amount > stored) {
            throw new IllegalArgumentException("Invalid terminal energy consumption");
        }
        stored -= amount;
        changed.run();
    }

    void set(int value) {
        stored = value;
    }
}
