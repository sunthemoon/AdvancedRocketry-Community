package io.github.sunthemoon.advancedrocketrycommunity.machine.combustion;

import io.github.sunthemoon.advancedrocketrycommunity.config.CommonConfig;
import io.github.sunthemoon.advancedrocketrycommunity.registry.ModBlockEntities;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.ForgeHooks;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.energy.IEnergyStorage;
import net.minecraftforge.items.IItemHandler;

/** Lifecycle bridge for a single, bounded fuel/energy snapshot. No station or multiblock ownership. */
public final class CombustionGeneratorBlockEntity extends BlockEntity implements MenuProvider {
    private CombustionBurn.State burn = new CombustionBurn.State(0, 0, 0);
    private ItemStack fuel = ItemStack.EMPTY;
    private Tag refusedRoot;
    private boolean oversized;
    private boolean operationLock;
    private long capEpoch;
    private long outputTick = Long.MIN_VALUE;
    private int outputThisTick;
    private CombustionBurn.Status status = CombustionBurn.Status.NO_FUEL;
    private LazyOptional<IItemHandler> itemCap;
    private LazyOptional<IEnergyStorage> energyCap;

    public CombustionGeneratorBlockEntity(BlockPos position, BlockState state) {
        super(ModBlockEntities.COMBUSTION_GENERATOR.get(), position, state);
        createCaps();
    }

    public static void serverTick(ServerLevel level, BlockPos position, BlockState blockState,
            CombustionGeneratorBlockEntity generator) {
        if (generator.repairRequired() || generator.isRemoved()) {
            generator.updateLit(false);
            return;
        }
        if (!CommonConfig.combustionGeneratorEnabled()) {
            generator.status = CombustionBurn.Status.DISABLED;
            generator.updateLit(false);
            return;
        }
        generator.burnTick();
        generator.updateLit(generator.status == CombustionBurn.Status.GENERATING);
        generator.push(level);
    }

    private void burnTick() {
        operationLock = true;
        try {
            int ticks = burn.remaining() == 0 ? fuelTicks(fuel) : 0;
            CombustionBurn.Step step = CombustionBurn.tick(burn, ticks);
            ItemStack after = fuel;
            if (step.consumeFuel()) {
                ItemStack remainder = fuel.getCraftingRemainingItem();
                if (!CombustionSave.safeStack(remainder) || (!remainder.isEmpty() && fuel.getCount() != 1)) {
                    status = CombustionBurn.Status.CONTAINER_BLOCKED;
                    return;
                }
                after = fuel.copy();
                after.shrink(1);
                if (after.isEmpty()) {
                    after = remainder;
                }
                if (!CombustionSave.fits(step.state(), after)) {
                    status = CombustionBurn.Status.CONTAINER_BLOCKED;
                    return;
                }
            }
            status = step.status();
            if (!step.state().equals(burn)) {
                burn = step.state();
                fuel = after.copy();
                setChanged();
            }
        } finally {
            operationLock = false;
        }
    }

    private void push(ServerLevel serverLevel) {
        for (Direction direction : Direction.values()) {
            int offer = Math.min(burn.energy(), remainingOutput());
            if (offer <= 0) {
                return;
            }
            BlockPos neighbour = worldPosition.relative(direction);
            if (!serverLevel.isInWorldBounds(neighbour)) {
                continue;
            }
            var chunk = serverLevel.getChunkSource().getChunkNow(neighbour.getX() >> 4, neighbour.getZ() >> 4);
            BlockEntity receiver = chunk == null ? null : chunk.getBlockEntity(neighbour);
            if (receiver == null || receiver.isRemoved() || receiver instanceof CombustionGeneratorBlockEntity) {
                continue;
            }
            // Guard both capability lookup and receive: third-party callbacks cannot extract twice or change fuel.
            operationLock = true;
            try {
                IEnergyStorage port = receiver.getCapability(ForgeCapabilities.ENERGY, direction.getOpposite())
                        .resolve().orElse(null);
                if (port != null && port.canReceive()) {
                    int accepted = Math.max(0, Math.min(offer, port.receiveEnergy(offer, false)));
                    if (accepted > 0) {
                        debit(accepted);
                    }
                }
            } finally {
                operationLock = false;
            }
        }
    }

    private void updateLit(boolean lit) {
        if (level != null && getBlockState().getBlock() instanceof CombustionGeneratorBlock
                && getBlockState().getValue(CombustionGeneratorBlock.LIT) != lit) {
            level.setBlock(worldPosition, getBlockState().setValue(CombustionGeneratorBlock.LIT, lit), Block.UPDATE_CLIENTS);
        }
    }

    public boolean repairRequired() {
        return refusedRoot != null;
    }

    boolean available() {
        if (repairRequired() || operationLock || isRemoved() || !(level instanceof ServerLevel serverLevel)) {
            return false;
        }
        var chunk = serverLevel.getChunkSource().getChunkNow(worldPosition.getX() >> 4, worldPosition.getZ() >> 4);
        return chunk != null && chunk.getBlockEntity(worldPosition) == this;
    }

    boolean currentPort(long epoch) {
        return epoch == capEpoch && available();
    }

    public CombustionBurn.State burnState() {
        return burn;
    }

    public CombustionBurn.Status status() {
        return repairRequired() ? CombustionBurn.Status.REPAIR_REQUIRED : status;
    }

    public ItemStack fuelStack() {
        return repairRequired() ? ItemStack.EMPTY : fuel.copy();
    }

    static int fuelTicks(ItemStack stack) {
        return stack.isEmpty() ? 0 : ForgeHooks.getBurnTime(stack.copy(), RecipeType.SMELTING);
    }

    public boolean canInsert(ItemStack stack) {
        if (!available() || !CombustionSave.safeStack(stack) || stack.isEmpty()) {
            return false;
        }
        operationLock = true;
        try {
            int ticks = fuelTicks(stack);
            return ticks > 0 && ticks <= CombustionBurn.MAX_BURN_TICKS && CombustionSave.fits(burn, stack);
        } finally {
            operationLock = false;
        }
    }

    public boolean setFuelFromMenu(ItemStack stack) {
        if (!available() || !CombustionSave.safeStack(stack) || !CombustionSave.fits(burn, stack)) {
            return false;
        }
        boolean reduction = !fuel.isEmpty() && ItemStack.isSameItemSameTags(fuel, stack)
                && stack.getCount() <= fuel.getCount();
        if (!stack.isEmpty() && !reduction && !canInsert(stack)) {
            return false;
        }
        fuel = stack.copy();
        setChanged();
        return true;
    }

    ItemStack insertFuel(ItemStack incoming, boolean simulate) {
        if (!canInsert(incoming) || (!fuel.isEmpty() && !ItemStack.isSameItemSameTags(fuel, incoming))) {
            return incoming;
        }
        int count = Math.min(incoming.getCount(), Math.min(64, incoming.getMaxStackSize()) - fuel.getCount());
        if (count <= 0) {
            return incoming;
        }
        ItemStack candidate = incoming.copyWithCount(fuel.getCount() + count);
        if (!CombustionSave.fits(burn, candidate)) {
            return incoming;
        }
        if (!simulate) {
            fuel = candidate;
            setChanged();
        }
        return incoming.copyWithCount(incoming.getCount() - count);
    }

    public ItemStack extractFuel(int amount, boolean simulate) {
        if (!available() || amount <= 0 || fuel.isEmpty()) {
            return ItemStack.EMPTY;
        }
        ItemStack result = fuel.copyWithCount(Math.min(amount, fuel.getCount()));
        if (!simulate) {
            fuel.shrink(result.getCount());
            setChanged();
        }
        return result;
    }

    int extractEnergy(int maximum, boolean simulate) {
        if (!available() || !CommonConfig.combustionGeneratorEnabled() || maximum <= 0) {
            return 0;
        }
        int amount = Math.min(maximum, Math.min(burn.energy(), remainingOutput()));
        if (!simulate && amount > 0) {
            debit(amount);
        }
        return amount;
    }

    private int remainingOutput() {
        if (level == null) {
            return 0;
        }
        if (outputTick != level.getGameTime()) {
            outputTick = level.getGameTime();
            outputThisTick = 0;
        }
        return CombustionBurn.OUTPUT_PER_TICK - outputThisTick;
    }

    private void debit(int amount) {
        burn = burn.withEnergy(burn.energy() - amount);
        outputThisTick += amount;
        setChanged();
    }

    /** Clear before spawning drops, so repeated removal callbacks cannot duplicate the slot. */
    ItemStack takeRemovalFuel() {
        if (repairRequired()) {
            return ItemStack.EMPTY;
        }
        ItemStack result = fuel.copy();
        fuel = ItemStack.EMPTY;
        setChanged();
        return result;
    }

    @Override
    protected void saveAdditional(CompoundTag outer) {
        super.saveAdditional(outer);
        // LevelChunk catches BE save exceptions and omits that BE. Preserve the raw reference instead (ADR-027);
        // CombustionProtection vetoes the complete outgoing chunk before storage writes oversized input.
        outer.put(CombustionSave.ROOT, refusedRoot != null ? (oversized ? refusedRoot : refusedRoot.copy())
                : CombustionSave.encode(burn, fuel));
    }

    @Override
    public void load(CompoundTag outer) {
        super.load(outer);
        burn = new CombustionBurn.State(0, 0, 0);
        fuel = ItemStack.EMPTY;
        refusedRoot = null;
        oversized = false;
        status = CombustionBurn.Status.NO_FUEL;
        if (outer.contains(CombustionSave.ROOT)) {
            Tag raw = outer.get(CombustionSave.ROOT);
            oversized = !CombustionSave.bounded(raw);
            if (oversized) {
                refusedRoot = raw; // Never copy this unbounded input or substitute an empty resource root.
            } else {
                try {
                    CombustionSave.Snapshot saved = CombustionSave.decode(raw);
                    burn = saved.burn();
                    fuel = saved.fuel();
                } catch (IllegalArgumentException refused) {
                    refusedRoot = raw.copy();
                }
            }
        }
    }

    private void createCaps() {
        long epoch = capEpoch;
        itemCap = LazyOptional.of(() -> new CombustionPorts.Items(this, epoch));
        energyCap = LazyOptional.of(() -> new CombustionPorts.Energy(this, epoch));
    }

    @Override
    public void invalidateCaps() {
        super.invalidateCaps();
        capEpoch++;
        itemCap.invalidate();
        energyCap.invalidate();
    }

    @Override
    public void reviveCaps() {
        super.reviveCaps();
        createCaps();
    }

    @Override
    @Nonnull
    public <T> LazyOptional<T> getCapability(@Nonnull Capability<T> capability, @Nullable Direction side) {
        if (repairRequired() || isRemoved() || (level != null && level.isClientSide)) {
            return LazyOptional.empty();
        }
        if (capability == ForgeCapabilities.ITEM_HANDLER) {
            return itemCap.cast();
        }
        if (capability == ForgeCapabilities.ENERGY) {
            return energyCap.cast();
        }
        return super.getCapability(capability, side);
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("block.advancedrocketrycommunity.combustion_generator");
    }

    @Override
    public AbstractContainerMenu createMenu(int id, Inventory inventory, Player player) {
        return new CombustionGeneratorMenu(id, inventory, this);
    }
}
