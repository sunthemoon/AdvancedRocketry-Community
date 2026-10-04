package io.github.sunthemoon.advancedrocketrycommunity.machine.tank;

import java.util.ArrayList;
import java.util.Objects;
import java.util.function.DoubleSupplier;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** Native lifecycle and inventory bridge; resources are one bounded snapshot, scheduling is Level-owned. */
public final class PressurizedTankBlockEntity extends BlockEntity {
    private final DoubleSupplier multiplier;
    private int capacity;
    private FluidStack fluid = FluidStack.EMPTY;
    private Tag refusedRoot;
    private boolean missingRoot;
    private boolean oversized;
    private boolean operationLock;
    private boolean dirty;
    private long dirtySince;
    private long lastTransferTick = Long.MIN_VALUE;
    private long capEpoch;
    private LazyOptional<IFluidHandler> fluidCap;

    public PressurizedTankBlockEntity(BlockEntityType<?> type, BlockPos position, BlockState state,
            DoubleSupplier multiplier) {
        super(type, position, state);
        this.multiplier = Objects.requireNonNull(multiplier, "multiplier");
        capacity = TankCapacity.fromMultiplier(multiplier.getAsDouble());
        createCap();
    }

    public static void serverTick(ServerLevel level, BlockPos position, BlockState state,
            PressurizedTankBlockEntity tank) {
        if (tank.dirty && tank.available()) {
            TankTransfers.offer(level, tank, tank.dirtySince);
        }
    }

    public boolean repairRequired() { return missingRoot || refusedRoot != null; }
    public int capacity() { return capacity; }
    public FluidStack fluidState() { return repairRequired() ? FluidStack.EMPTY : fluid.copy(); }
    public boolean overCapacity() { return !repairRequired() && fluid.getAmount() > capacity; }
    public boolean transferDirty() { return dirty; }

    boolean available() {
        if (repairRequired() || operationLock || isRemoved() || !(level instanceof ServerLevel server)) { return false; }
        var chunk = server.getChunkSource().getChunkNow(worldPosition.getX() >> 4, worldPosition.getZ() >> 4);
        return chunk != null && chunk.getBlockEntity(worldPosition) == this;
    }

    public void markTransferDirty() {
        if (repairRequired() || isRemoved() || !(level instanceof ServerLevel server)) { return; }
        if (!dirty) { dirtySince = server.getGameTime(); }
        dirty = true;
    }

    private void resourceChanged() {
        setChanged();
        markTransferDirty();
        PressurizedTankBlockEntity below = loadedTank(worldPosition.below());
        if (below != null) { below.markTransferDirty(); }
    }

    private PressurizedTankBlockEntity loadedTank(BlockPos position) {
        if (!(level instanceof ServerLevel server) || !server.isInWorldBounds(position)) { return null; }
        var chunk = server.getChunkSource().getChunkNow(position.getX() >> 4, position.getZ() >> 4);
        return chunk != null && chunk.getBlockEntity(position) instanceof PressurizedTankBlockEntity tank
                && tank.available() ? tank : null;
    }

    private boolean canPull(PressurizedTankBlockEntity above) {
        return above != null && !above.fluid.isEmpty()
                && TankCapacity.accepted(fluid.getAmount(), capacity, 1,
                        fluid.isEmpty() || fluid.isFluidEqual(above.fluid)) > 0;
    }

    /** Scheduler-only: lock both native snapshots before preparing a single compatible neighbour move. */
    void pullFromAbove(long tick) {
        if (!dirty || !available()) { return; }
        dirty = false;
        PressurizedTankBlockEntity above = loadedTank(worldPosition.above());
        if (!canPull(above)) { return; }
        if (lastTransferTick == tick || above.lastTransferTick == tick) {
            markTransferDirty();
            return;
        }
        operationLock = true;
        above.operationLock = true;
        boolean changed = false;
        try {
            int amount = TankCapacity.accepted(fluid.getAmount(), capacity,
                    Math.min(TankTransferQueue.TRANSFER_MB, above.fluid.getAmount()), true);
            FluidStack target = fluid.isEmpty() ? above.fluid.copy() : fluid.copy();
            target.setAmount(fluid.getAmount() + amount);
            FluidStack source = above.fluid.copy(); source.shrink(amount);
            if (amount <= 0 || !TankSave.fits(target) || !TankSave.fits(source)) { return; }
            fluid = target;
            above.fluid = source;
            lastTransferTick = tick;
            above.lastTransferTick = tick;
            above.dirty = false;
            TankTransfers.cancel(above);
            setChanged(); above.setChanged();
            changed = true;
        } finally {
            above.operationLock = false;
            operationLock = false;
        }
        if (changed) {
            rearmIfCompatible();
            above.rearmIfCompatible();
            PressurizedTankBlockEntity below = loadedTank(worldPosition.below());
            if (below != null) { below.rearmIfCompatible(); }
        }
    }

    private void rearmIfCompatible() {
        if (available() && canPull(loadedTank(worldPosition.above()))) { markTransferDirty(); }
    }

    int fill(FluidStack offered, IFluidHandler.FluidAction action) {
        Objects.requireNonNull(action, "action");
        if (!available()) { return 0; }
        operationLock = true;
        try {
            if (offered.isEmpty() || !TankSave.safe(offered)) { return 0; }
            int amount = TankCapacity.accepted(fluid.getAmount(), capacity, offered.getAmount(),
                    fluid.isEmpty() || fluid.isFluidEqual(offered));
            if (amount == 0) { return 0; }
            FluidStack after = fluid.isEmpty() ? offered.copy() : fluid.copy();
            after.setAmount(fluid.getAmount() + amount);
            if (!TankSave.fits(after)) { return 0; }
            if (action.execute()) { fluid = after; resourceChanged(); }
            return amount;
        } finally { operationLock = false; }
    }

    FluidStack drain(int maximum, @Nullable FluidStack matching, IFluidHandler.FluidAction action) {
        Objects.requireNonNull(action, "action");
        if (!available() || fluid.isEmpty()) { return FluidStack.EMPTY; }
        operationLock = true;
        try {
            if (matching != null && (matching.isEmpty() || !TankSave.safe(matching) || !fluid.isFluidEqual(matching))) {
                return FluidStack.EMPTY;
            }
            if (matching != null) { maximum = matching.getAmount(); }
            if (maximum <= 0) { return FluidStack.EMPTY; }
            int amount = TankCapacity.drained(fluid.getAmount(), maximum);
            FluidStack result = fluid.copy(); result.setAmount(amount);
            if (action.execute()) { fluid = fluid.copy(); fluid.shrink(amount); resourceChanged(); }
            return result;
        } finally { operationLock = false; }
    }

    boolean validFluid(FluidStack offered) {
        if (!available()) { return false; }
        operationLock = true;
        try { return !offered.isEmpty() && TankSave.safe(offered) && (fluid.isEmpty() || fluid.isFluidEqual(offered)); }
        finally { operationLock = false; }
    }

    /** World and player writes happen only after the detached unit and affected storage slot have passed preflight. */
    public boolean exchange(Player player, InteractionHand hand) {
        if (!available() || player.isSpectator() || player.level() != level
                || !level.mayInteract(player, worldPosition)
                || player.distanceToSqr(worldPosition.getX() + 0.5, worldPosition.getY() + 0.5,
                        worldPosition.getZ() + 0.5) > 64) {
            return false;
        }
        operationLock = true;
        try {
            var inventory = player.getInventory();
            ItemStack held = player.getItemInHand(hand);
            if (!TankContainerExchange.safeItem(held)) { return false; }
            ItemStack heldBefore = held.copy();
            var storage = new ArrayList<ItemStack>();
            var slots = new ArrayList<Integer>();
            for (int slot = 0; slot < inventory.items.size(); slot++) {
                if (hand != InteractionHand.MAIN_HAND || slot != inventory.selected) {
                    storage.add(inventory.getItem(slot));
                    slots.add(slot);
                }
            }
            var plan = TankContainerExchange.plan(heldBefore, storage, fluid, capacity);
            if (plan.isEmpty()) { return false; }
            var prepared = plan.get();
            int destination = prepared.slots().destinationSlot() < 0 ? -1
                    : slots.get(prepared.slots().destinationSlot());
            ItemStack actualHeld = player.getItemInHand(hand);
            if (!TankContainerExchange.safeItem(actualHeld) || !ItemStack.matches(actualHeld, heldBefore)) { return false; }
            fluid = prepared.fluid();
            player.setItemInHand(hand, prepared.slots().held());
            if (destination >= 0) { inventory.setItem(destination, prepared.slots().destination()); }
            inventory.setChanged();
            resourceChanged();
            return true;
        } finally { operationLock = false; }
    }

    public CompoundTag resourceRoot() {
        if (repairRequired()) { throw new IllegalStateException("Tank data requires backup and repair"); }
        return TankSave.encode(fluid);
    }

    @Override protected void saveAdditional(CompoundTag outer) {
        super.saveAdditional(outer);
        if (!missingRoot) {
            outer.put(TankSave.ROOT, refusedRoot != null ? (oversized ? refusedRoot : refusedRoot.copy())
                    : TankSave.encode(fluid));
        }
    }

    @Override public void load(CompoundTag outer) {
        if (operationLock) { throw new IllegalStateException("Reentrant tank load"); }
        super.load(outer);
        invalidateCaps();
        fluid = FluidStack.EMPTY;
        refusedRoot = null;
        missingRoot = !outer.contains(TankSave.ROOT);
        oversized = false;
        dirty = false;
        lastTransferTick = Long.MIN_VALUE;
        if (!missingRoot) {
            Tag raw = outer.get(TankSave.ROOT);
            oversized = !TankSave.bounded(raw);
            if (oversized) { refusedRoot = raw; }
            else {
                try {
                    capacity = TankCapacity.fromMultiplier(multiplier.getAsDouble());
                    fluid = TankSave.decode(raw);
                } catch (IllegalArgumentException refused) { refusedRoot = raw.copy(); }
            }
        }
        createCap();
        markTransferDirty();
    }

    @Override public void onLoad() { super.onLoad(); markTransferDirty(); }
    @Override public void setRemoved() { TankTransfers.cancel(this); super.setRemoved(); }
    @Override public void onChunkUnloaded() { TankTransfers.cancel(this); super.onChunkUnloaded(); }
    private void createCap() { long epoch = capEpoch; fluidCap = LazyOptional.of(() -> new TankFluidPort(this, epoch)); }
    @Override public void invalidateCaps() {
        super.invalidateCaps();
        TankTransfers.cancel(this);
        capEpoch++;
        if (fluidCap != null) { fluidCap.invalidate(); }
    }
    @Override public void reviveCaps() { super.reviveCaps(); createCap(); }
    boolean currentPort(long epoch) { return epoch == capEpoch && available(); }
    @Override @NotNull
    public <T> LazyOptional<T> getCapability(@NotNull Capability<T> requested, @Nullable Direction side) {
        if (repairRequired() || isRemoved() || level != null && level.isClientSide) { return LazyOptional.empty(); }
        return requested == ForgeCapabilities.FLUID_HANDLER ? fluidCap.cast() : super.getCapability(requested, side);
    }
}
