package io.github.sunthemoon.advancedrocketrycommunity.endgame.device;

import io.github.sunthemoon.advancedrocketrycommunity.endgame.model.EndgameNbt;
import java.util.Objects;
import java.util.function.Supplier;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraftforge.energy.IEnergyStorage;

/**
 * ADR-054 section 8: a device's own bounded Forge Energy buffer. It only receives, only on the server thread, at
 * most {@code maxInput} FE per tick; a simulation changes nothing, and nothing can be extracted.
 */
public final class EndgameEnergyBuffer implements IEnergyStorage {
    private final int capacity;
    private final int maxInput;
    private final Runnable changed;
    private final Supplier<Level> level;
    private int energy;
    private long receivedTick = Long.MIN_VALUE;
    private int receivedThisTick;

    public EndgameEnergyBuffer(int capacity, int maxInput, Runnable changed, Supplier<Level> level) {
        if (capacity < 1 || maxInput < 1) {
            throw new IllegalArgumentException("An energy buffer has a positive capacity and input");
        }
        this.capacity = capacity;
        this.maxInput = maxInput;
        this.changed = Objects.requireNonNull(changed, "changed");
        this.level = Objects.requireNonNull(level, "level");
    }

    public int energy() {
        return energy;
    }

    /** Takes {@code amount} FE when the buffer holds it; false leaves the buffer unchanged. */
    public boolean spend(int amount) {
        if (amount < 0) {
            throw new IllegalArgumentException("A negative payment");
        }
        if (energy < amount) {
            return false;
        }
        energy -= amount;
        changed.run();
        return true;
    }

    /** Test seam and strict read: a value within the buffer. */
    public void set(int value) {
        if (value < 0 || value > capacity) {
            throw new IllegalArgumentException("Energy is outside the buffer");
        }
        energy = value;
        changed.run();
    }

    public void read(CompoundTag root, String key) {
        int value = EndgameNbt.requireInt(root, key);
        if (value < 0 || value > capacity) {
            throw new IllegalArgumentException(key + " is outside its buffer");
        }
        energy = value;
    }

    public void write(CompoundTag root, String key) {
        root.putInt(key, energy);
    }

    @Override
    public int receiveEnergy(int maximum, boolean simulate) {
        if (maximum <= 0 || !(level.get() instanceof ServerLevel server) || !server.getServer().isSameThread()) {
            return 0;
        }
        long now = server.getGameTime();
        int usedThisTick = receivedTick == now ? receivedThisTick : 0;
        int accepted = Math.min(maximum, Math.min(capacity - energy, maxInput - usedThisTick));
        if (accepted <= 0) {
            return 0;
        }
        if (!simulate) {
            energy += accepted;
            receivedTick = now;
            receivedThisTick = usedThisTick + accepted;
            changed.run();
        }
        return accepted;
    }

    @Override
    public int extractEnergy(int maximum, boolean simulate) {
        return 0;
    }

    @Override
    public int getEnergyStored() {
        return energy;
    }

    @Override
    public int getMaxEnergyStored() {
        return capacity;
    }

    @Override
    public boolean canExtract() {
        return false;
    }

    @Override
    public boolean canReceive() {
        return true;
    }
}
