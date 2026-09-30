package io.github.sunthemoon.advancedrocketrycommunity.station.forge;

import io.github.sunthemoon.advancedrocketrycommunity.registry.ModBlockEntities;
import io.github.sunthemoon.advancedrocketrycommunity.station.model.StationLimits;
import io.github.sunthemoon.advancedrocketrycommunity.station.warp.StationWarpRuntime;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.energy.IEnergyStorage;

/**
 * Stateless: stores only its schema version, holds no energy, identity or debt, and exposes a
 * receive-only Forge Energy view whose acceptance the warp service decides (ADR-044 §2).
 */
public final class WarpCoreBlockEntity extends BlockEntity {
    public static final int SCHEMA_VERSION = 1;

    private final IEnergyStorage energy = new WarpCoreEnergy();
    private LazyOptional<IEnergyStorage> energyCapability = LazyOptional.of(() -> energy);

    public WarpCoreBlockEntity(BlockPos position, BlockState state) {
        super(ModBlockEntities.WARP_CORE.get(), position, state);
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.putInt("schema_version", SCHEMA_VERSION);
    }

    @Override
    public <T> LazyOptional<T> getCapability(@Nonnull Capability<T> capability, @Nullable Direction side) {
        if (capability == ForgeCapabilities.ENERGY) {
            return energyCapability.cast();
        }
        return super.getCapability(capability, side);
    }

    @Override
    public void invalidateCaps() {
        super.invalidateCaps();
        energyCapability.invalidate();
    }

    @Override
    public void reviveCaps() {
        super.reviveCaps();
        energyCapability = LazyOptional.of(() -> energy);
    }

    private final class WarpCoreEnergy implements IEnergyStorage {
        @Override
        public int receiveEnergy(int maxReceive, boolean simulate) {
            if (isRemoved() || !(getLevel() instanceof ServerLevel level)) {
                return 0;
            }
            return StationWarpRuntime.receiveEnergy(level, getBlockPos(), maxReceive, simulate);
        }

        @Override
        public int extractEnergy(int maxExtract, boolean simulate) {
            return 0;
        }

        /** Always 0, so no station's balance is exposed to energy probes. */
        @Override
        public int getEnergyStored() {
            return 0;
        }

        @Override
        public int getMaxEnergyStored() {
            return StationLimits.MAX_WARP_ENERGY;
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
}
