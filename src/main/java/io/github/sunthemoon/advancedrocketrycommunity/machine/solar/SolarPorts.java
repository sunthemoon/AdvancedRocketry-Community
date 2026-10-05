package io.github.sunthemoon.advancedrocketrycommunity.machine.solar;

import net.minecraftforge.energy.IEnergyStorage;

/** Captured capability epochs never extend a removed/replaced owner's lifetime. */
final class SolarPorts {
    record Energy(SolarGeneratorBlockEntity owner, long epoch) implements IEnergyStorage {
        @Override public int receiveEnergy(int maximum, boolean simulate) { return 0; }
        @Override public int extractEnergy(int maximum, boolean simulate) {
            return owner.extractEnergy(epoch, maximum, simulate);
        }
        @Override public int getEnergyStored() { return owner.currentPort(epoch) ? owner.energyStored() : 0; }
        @Override public int getMaxEnergyStored() { return SolarGeneration.CAPACITY; }
        @Override public boolean canExtract() { return owner.enabled() && owner.currentPort(epoch); }
        @Override public boolean canReceive() { return false; }
    }
    private SolarPorts() { }
}
