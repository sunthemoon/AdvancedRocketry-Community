package io.github.sunthemoon.advancedrocketrycommunity.machine.solar;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.energy.IEnergyStorage;

/** No resource/root of its own. Every facade captures a formation token and resolves only an already loaded owner. */
public final class SolarArrayOutputBlockEntity extends BlockEntity {
    private SolarArrayBlockEntity owner;
    private Object token;
    private LazyOptional<IEnergyStorage> energy = LazyOptional.empty();

    public SolarArrayOutputBlockEntity(BlockEntityType<?> type, BlockPos position, BlockState state) { super(type, position, state); }
    boolean physical() {
        if (isRemoved() || !(level instanceof ServerLevel server) || !server.getServer().isSameThread()
                || server.getServer().getLevel(server.dimension()) != server) { return false; }
        var chunk = server.getChunkSource().getChunkNow(worldPosition.getX() >> 4, worldPosition.getZ() >> 4);
        return chunk != null && chunk.getBlockEntities().get(worldPosition) == this;
    }
    boolean claimedByOther(SolarArrayBlockEntity candidate) { return owner != null && owner != candidate && owner.bindingValid(this, token); }
    boolean boundTo(SolarArrayBlockEntity controller, Object generation) { return owner == controller && token == generation; }
    void bind(SolarArrayBlockEntity controller, Object generation) {
        if (owner == controller && token == generation) { return; }
        clearBinding(); owner = controller; token = generation;
        energy = LazyOptional.of(() -> new Output(controller, generation));
    }
    void release(SolarArrayBlockEntity controller) { if (owner == controller) { clearBinding(); } }
    private void clearBinding() { owner = null; token = null; energy.invalidate(); energy = LazyOptional.empty(); }
    private boolean current(SolarArrayBlockEntity controller, Object generation) {
        return owner == controller && token == generation && controller.enabled() && controller.owns(this, generation);
    }
    private final class Output implements IEnergyStorage {
        private final SolarArrayBlockEntity controller;
        private final Object generation;
        private Output(SolarArrayBlockEntity controller, Object generation) { this.controller = controller; this.generation = generation; }
        @Override public int receiveEnergy(int maximum, boolean simulate) { return 0; }
        @Override public int extractEnergy(int maximum, boolean simulate) {
            return current(controller, generation) ? controller.extract(SolarArrayOutputBlockEntity.this, generation, maximum, simulate) : 0;
        }
        @Override public int getEnergyStored() { return current(controller, generation) ? controller.energyStored() : 0; }
        @Override public int getMaxEnergyStored() { return SolarArrayRules.CAPACITY; }
        @Override public boolean canExtract() { return current(controller, generation); }
        @Override public boolean canReceive() { return false; }
    }
    @Override public void onLoad() {
        super.onLoad(); clearBinding();
        if (level instanceof ServerLevel server) { SolarArrayRuntime.manager(server).changed(worldPosition); }
    }
    @Override public void invalidateCaps() { super.invalidateCaps(); clearBinding(); }
    @Override @Nonnull public <T> LazyOptional<T> getCapability(@Nonnull Capability<T> capability, @Nullable Direction side) {
        if (capability == ForgeCapabilities.ENERGY) { return energy.cast(); }
        return super.getCapability(capability, side);
    }
}
