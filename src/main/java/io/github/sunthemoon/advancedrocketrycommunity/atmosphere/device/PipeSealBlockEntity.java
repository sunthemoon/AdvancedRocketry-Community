package io.github.sunthemoon.advancedrocketrycommunity.atmosphere.device;

import io.github.sunthemoon.advancedrocketrycommunity.config.CommonConfig;
import io.github.sunthemoon.advancedrocketrycommunity.registry.ModBlockEntities;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Function;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.energy.IEnergyStorage;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.items.IItemHandler;

/** No inventory or cached foreign capability. Each method resolves one opposite loaded neighbour. */
public final class PipeSealBlockEntity extends BlockEntity {
    private final List<LazyOptional<?>> views = new ArrayList<>();
    private int generation;
    private boolean operating;
    public PipeSealBlockEntity(BlockPos pos, BlockState state) { super(ModBlockEntities.PIPE_SEAL.get(), pos, state); createViews(); }
    private record Forwarding<T>(T handler, BooleanSupplier live) {
        private <R> R inspect(R refused, Function<T, R> call) {
            if (!live.getAsBoolean()) { return refused; }
            R result = call.apply(handler);
            return live.getAsBoolean() ? result : refused;
        }
        private <R> R mutate(R refused, Function<T, R> call) {
            return live.getAsBoolean() ? call.apply(handler) : refused;
        }
    }
    private <T, R> R forward(int lease, Direction side, Capability<T> cap, R refused, Function<Forwarding<T>, R> call) {
        if (lease != generation || operating || !CommonConfig.classicDevicesEnabled() || !DeviceAccess.loaded(this)) { return refused; }
        var ownerLevel = level;
        var targetPos = worldPosition.relative(side.getOpposite());
        if (!level.hasChunkAt(targetPos)) { return refused; }
        operating = true;
        try {
            var target = level.getBlockEntity(targetPos);
            if (target == null || target instanceof PipeSealBlockEntity || !DeviceAccess.loaded(target)) { return refused; }
            T handler = target.getCapability(cap, side).orElse(null);
            BooleanSupplier live = () -> operating && lease == generation && level == ownerLevel
                    && CommonConfig.classicDevicesEnabled() && DeviceAccess.loaded(this)
                    && target.getLevel() == ownerLevel && DeviceAccess.loaded(target)
                    && ownerLevel.getBlockEntity(targetPos) == target;
            if (handler == null || !live.getAsBoolean()) { return refused; }
            return call.apply(new Forwarding<>(handler, live));
        } finally { operating = false; }
    }
    private void createViews() {
        views.clear(); int lease = generation;
        for (Direction face : Direction.values()) {
            views.add(LazyOptional.of(() -> new IItemHandler() {
                private <R> R call(R fallback, Function<Forwarding<IItemHandler>, R> action) { return forward(lease, face, ForgeCapabilities.ITEM_HANDLER, fallback, action); }
                @Override public int getSlots() { return call(0, f -> f.inspect(0, h -> Math.min(8, Math.max(0, h.getSlots())))); }
                private boolean slot(int slot) { return slot >= 0 && slot < 8; }
                @Override public ItemStack getStackInSlot(int slot) { return !slot(slot) ? ItemStack.EMPTY : call(ItemStack.EMPTY,
                        f -> slot < f.inspect(0, IItemHandler::getSlots) ? f.inspect(ItemStack.EMPTY, h -> h.getStackInSlot(slot).copy()) : ItemStack.EMPTY); }
                @Override public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) { return !slot(slot) ? stack : call(stack,
                        f -> slot < f.inspect(0, IItemHandler::getSlots) ? f.mutate(stack, h -> h.insertItem(slot, stack, simulate)) : stack); }
                @Override public ItemStack extractItem(int slot, int amount, boolean simulate) { return !slot(slot) || amount <= 0 ? ItemStack.EMPTY : call(ItemStack.EMPTY,
                        f -> slot < f.inspect(0, IItemHandler::getSlots) ? f.mutate(ItemStack.EMPTY, h -> h.extractItem(slot, amount, simulate)) : ItemStack.EMPTY); }
                @Override public int getSlotLimit(int slot) { return !slot(slot) ? 0 : call(0,
                        f -> slot < f.inspect(0, IItemHandler::getSlots) ? f.inspect(0, h -> h.getSlotLimit(slot)) : 0); }
                @Override public boolean isItemValid(int slot, ItemStack stack) { return slot(slot) && call(false,
                        f -> slot < f.inspect(0, IItemHandler::getSlots) && f.inspect(false, h -> h.isItemValid(slot, stack))); }
            }));
            views.add(LazyOptional.of(() -> new IFluidHandler() {
                private <R> R call(R fallback, Function<Forwarding<IFluidHandler>, R> action) { return forward(lease, face, ForgeCapabilities.FLUID_HANDLER, fallback, action); }
                @Override public int getTanks() { return call(0, f -> f.inspect(0, h -> Math.min(8, Math.max(0, h.getTanks())))); }
                @Override public FluidStack getFluidInTank(int tank) { return tank < 0 || tank >= 8 ? FluidStack.EMPTY : call(FluidStack.EMPTY,
                        f -> tank < f.inspect(0, IFluidHandler::getTanks) ? f.inspect(FluidStack.EMPTY, h -> h.getFluidInTank(tank).copy()) : FluidStack.EMPTY); }
                @Override public int getTankCapacity(int tank) { return tank < 0 || tank >= 8 ? 0 : call(0,
                        f -> tank < f.inspect(0, IFluidHandler::getTanks) ? f.inspect(0, h -> h.getTankCapacity(tank)) : 0); }
                @Override public boolean isFluidValid(int tank, FluidStack stack) { return tank >= 0 && tank < 8 && call(false,
                        f -> tank < f.inspect(0, IFluidHandler::getTanks) && f.inspect(false, h -> h.isFluidValid(tank, stack))); }
                @Override public int fill(FluidStack stack, FluidAction action) { return call(0, f -> f.mutate(0, h -> h.fill(stack, action))); }
                @Override public FluidStack drain(FluidStack stack, FluidAction action) { return call(FluidStack.EMPTY, f -> f.mutate(FluidStack.EMPTY, h -> h.drain(stack, action))); }
                @Override public FluidStack drain(int amount, FluidAction action) { return amount <= 0 ? FluidStack.EMPTY : call(FluidStack.EMPTY, f -> f.mutate(FluidStack.EMPTY, h -> h.drain(amount, action))); }
            }));
            views.add(LazyOptional.of(() -> new IEnergyStorage() {
                private <R> R call(R fallback, Function<Forwarding<IEnergyStorage>, R> action) { return forward(lease, face, ForgeCapabilities.ENERGY, fallback, action); }
                @Override public int receiveEnergy(int amount, boolean simulate) { return amount <= 0 ? 0 : call(0, f -> f.mutate(0, h -> h.receiveEnergy(amount, simulate))); }
                @Override public int extractEnergy(int amount, boolean simulate) { return amount <= 0 ? 0 : call(0, f -> f.mutate(0, h -> h.extractEnergy(amount, simulate))); }
                @Override public int getEnergyStored() { return call(0, f -> f.inspect(0, IEnergyStorage::getEnergyStored)); }
                @Override public int getMaxEnergyStored() { return call(0, f -> f.inspect(0, IEnergyStorage::getMaxEnergyStored)); }
                @Override public boolean canExtract() { return call(false, f -> f.inspect(false, IEnergyStorage::canExtract)); }
                @Override public boolean canReceive() { return call(false, f -> f.inspect(false, IEnergyStorage::canReceive)); }
            }));
        }
    }
    @Override public <T> LazyOptional<T> getCapability(Capability<T> cap, Direction side) {
        int type = cap == ForgeCapabilities.ITEM_HANDLER ? 0 : cap == ForgeCapabilities.FLUID_HANDLER ? 1 : cap == ForgeCapabilities.ENERGY ? 2 : -1;
        return side != null && type >= 0 && !isRemoved() ? views.get(side.ordinal() * 3 + type).cast() : super.getCapability(cap, side);
    }
    @Override public void invalidateCaps() { super.invalidateCaps(); generation++; views.forEach(LazyOptional::invalidate); }
    @Override public void reviveCaps() { super.reviveCaps(); createViews(); }
}
