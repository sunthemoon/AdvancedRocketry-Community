package io.github.sunthemoon.advancedrocketrycommunity.rocket.fuel;

import io.github.sunthemoon.advancedrocketrycommunity.registry.ModBlockEntities;
import io.github.sunthemoon.advancedrocketrycommunity.registry.ModItems;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.entity.RocketEntity;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFlightData;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFlightLimits;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFuelMutation;
import java.util.Comparator;
import java.util.Optional;
import java.util.UUID;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemStackHandler;

/** One-slot, owner-bound loader that never searches or loads chunks outside a fixed local box. */
public final class FuelLoaderBlockEntity extends BlockEntity {
    public static final int SLOT = 0;
    public static final int SLOT_COUNT = 1;
    public static final double MAX_RANGE = 6.0D;

    private final LoaderInventory inventory = new LoaderInventory();
    private LazyOptional<IItemHandler> itemCapability;

    private long bufferedUnits;
    private UUID ownerId;
    private UUID targetRocketId;
    private FuelLoaderStatus status = FuelLoaderStatus.UNCLAIMED;
    private boolean futureSchemaBlocked;
    private boolean invalidDataBlocked;
    private CompoundTag preservedBlockedData;

    public FuelLoaderBlockEntity(BlockPos position, BlockState state) {
        super(ModBlockEntities.FUEL_LOADER.get(), position, state);
        createCapabilityView();
    }

    public static void serverTick(
            Level level,
            BlockPos position,
            BlockState state,
            FuelLoaderBlockEntity loader
    ) {
        if (level instanceof ServerLevel serverLevel) {
            loader.tickServer(serverLevel);
        }
    }

    private void tickServer(ServerLevel level) {
        if (futureSchemaBlocked) {
            setStatus(FuelLoaderStatus.UNSUPPORTED_DATA);
            return;
        }
        if (invalidDataBlocked) {
            setStatus(FuelLoaderStatus.INVALID_DATA);
            return;
        }
        if (ownerId == null) {
            setStatus(FuelLoaderStatus.UNCLAIMED);
            return;
        }
        if (bufferedUnits == 0L) {
            ItemStack held = inventory.getStackInSlot(SLOT);
            if (held.isEmpty()) {
                targetRocketId = null;
                setStatus(FuelLoaderStatus.IDLE);
                return;
            }
            if (held.is(ModItems.EMPTY_CANISTER.get())) {
                targetRocketId = null;
                setStatus(FuelLoaderStatus.OUTPUT_READY);
                return;
            }
            Optional<RocketEntity> target = findTarget(level);
            if (target.isEmpty()) {
                setStatus(FuelLoaderStatus.WAITING_FOR_ROCKET);
                return;
            }
            inventory.setInternal(ItemStack.EMPTY);
            bufferedUnits = RocketFlightLimits.FUEL_CELL_UNITS;
            targetRocketId = target.get().getUUID();
            setChanged();
        }

        Optional<RocketEntity> selected = target(level).or(() -> findTarget(level));
        if (selected.isEmpty()) {
            targetRocketId = null;
            setStatus(FuelLoaderStatus.WAITING_FOR_ROCKET);
            return;
        }
        RocketEntity rocket = selected.get();
        targetRocketId = rocket.getUUID();
        RocketFlightData flightData = rocket.flightData().orElseThrow();
        long requested = Math.min(RocketFlightLimits.FUEL_TRANSFER_PER_TICK, bufferedUnits);
        RocketFuelMutation mutation = flightData.fuel().fill(requested);
        if (!mutation.success()) {
            targetRocketId = null;
            setStatus(FuelLoaderStatus.WAITING_FOR_ROCKET);
            return;
        }
        rocket.updateFlightData(flightData.withFuel(mutation.state(), level.getGameTime()));
        bufferedUnits -= mutation.unitsChanged();
        if (bufferedUnits == 0L) {
            targetRocketId = null;
            inventory.setInternal(new ItemStack(ModItems.EMPTY_CANISTER.get()));
            setStatus(FuelLoaderStatus.OUTPUT_READY);
        } else {
            setStatus(FuelLoaderStatus.TRANSFERRING);
        }
        setChanged();
    }

    private Optional<RocketEntity> target(ServerLevel level) {
        if (targetRocketId == null) {
            return Optional.empty();
        }
        Entity entity = level.getEntity(targetRocketId);
        if (!(entity instanceof RocketEntity rocket) || !eligible(rocket)) {
            return Optional.empty();
        }
        return Optional.of(rocket);
    }

    private Optional<RocketEntity> findTarget(ServerLevel level) {
        AABB search = new AABB(worldPosition).inflate(MAX_RANGE);
        Comparator<RocketEntity> nearest = Comparator
                .comparingDouble((RocketEntity rocket) -> rocket.distanceToSqr(
                        worldPosition.getX() + 0.5D,
                        worldPosition.getY() + 0.5D,
                        worldPosition.getZ() + 0.5D
                ))
                .thenComparing(RocketEntity::getUUID);
        return level.getEntitiesOfClass(RocketEntity.class, search, this::eligible)
                .stream()
                .min(nearest);
    }

    private boolean eligible(RocketEntity rocket) {
        if (!rocket.operational()
                || rocket.distanceToSqr(
                        worldPosition.getX() + 0.5D,
                        worldPosition.getY() + 0.5D,
                        worldPosition.getZ() + 0.5D
                ) > MAX_RANGE * MAX_RANGE
                || rocket.ownerId().filter(ownerId::equals).isEmpty()) {
            return false;
        }
        return rocket.flightData()
                .filter(data -> data.state().acceptsFuel())
                .filter(data -> data.fuel().remainingCapacity() > 0L)
                .isPresent();
    }

    public void assignOwner(UUID ownerId) {
        if (this.ownerId == null) {
            this.ownerId = java.util.Objects.requireNonNull(ownerId, "ownerId");
            setStatus(FuelLoaderStatus.IDLE);
            setChanged();
        }
    }

    public boolean authorized(Player player) {
        return ownerId == null || ownerId.equals(player.getUUID()) || player.hasPermissions(2);
    }

    public boolean insertFuelFromPlayer(Player player, InteractionHand hand) {
        if (futureSchemaBlocked || invalidDataBlocked || bufferedUnits > 0L || !authorized(player)) {
            return false;
        }
        assignOwner(player.getUUID());
        ItemStack held = player.getItemInHand(hand);
        if (!held.is(ModItems.ROCKET_FUEL_CELL.get()) || !inventory.getStackInSlot(SLOT).isEmpty()) {
            return false;
        }
        inventory.setInternal(new ItemStack(ModItems.ROCKET_FUEL_CELL.get()));
        if (!player.getAbilities().instabuild) {
            held.shrink(1);
        }
        setChanged();
        return true;
    }

    public boolean takeOutput(Player player) {
        if (!authorized(player) || !inventory.getStackInSlot(SLOT).is(ModItems.EMPTY_CANISTER.get())) {
            return false;
        }
        ItemStack output = inventory.extractInternal();
        if (!player.getInventory().add(output)) {
            player.drop(output, false);
        }
        setChanged();
        return true;
    }

    public void copyInventoryTo(Container target) {
        if (target.getContainerSize() > 0) {
            target.setItem(0, inventory.getStackInSlot(SLOT).copy());
        }
    }

    @Override
    protected void saveAdditional(CompoundTag parent) {
        super.saveAdditional(parent);
        if ((futureSchemaBlocked || invalidDataBlocked) && preservedBlockedData != null) {
            parent.put(FuelLoaderPersistence.DATA_KEY, preservedBlockedData.copy());
            return;
        }
        parent.put(FuelLoaderPersistence.DATA_KEY, FuelLoaderPersistence.encode(
                itemState(),
                bufferedUnits,
                ownerId,
                targetRocketId
        ));
    }

    @Override
    public void load(CompoundTag parent) {
        super.load(parent);
        resetLoadedState();
        FuelLoaderPersistence.DecodeResult decoded = FuelLoaderPersistence.decode(parent);
        if (decoded.status() != FuelLoaderPersistence.DecodeStatus.VALID) {
            futureSchemaBlocked = decoded.status() == FuelLoaderPersistence.DecodeStatus.FUTURE;
            invalidDataBlocked = decoded.status() == FuelLoaderPersistence.DecodeStatus.INVALID;
            preservedBlockedData = decoded.preservedData();
            status = futureSchemaBlocked
                    ? FuelLoaderStatus.UNSUPPORTED_DATA
                    : FuelLoaderStatus.INVALID_DATA;
            return;
        }
        inventory.setInternal(stackFor(decoded.itemState()));
        bufferedUnits = decoded.bufferedUnits();
        ownerId = decoded.ownerId();
        targetRocketId = decoded.targetRocketId();
        status = ownerId == null ? FuelLoaderStatus.UNCLAIMED : FuelLoaderStatus.IDLE;
    }

    private void resetLoadedState() {
        inventory.setInternal(ItemStack.EMPTY);
        bufferedUnits = 0L;
        ownerId = null;
        targetRocketId = null;
        status = FuelLoaderStatus.UNCLAIMED;
        futureSchemaBlocked = false;
        invalidDataBlocked = false;
        preservedBlockedData = null;
    }

    private FuelLoaderPersistence.ItemState itemState() {
        ItemStack stack = inventory.getStackInSlot(SLOT);
        if (stack.isEmpty()) {
            return FuelLoaderPersistence.ItemState.EMPTY;
        }
        return stack.is(ModItems.ROCKET_FUEL_CELL.get())
                ? FuelLoaderPersistence.ItemState.FUEL_CELL
                : FuelLoaderPersistence.ItemState.EMPTY_CANISTER;
    }

    private static ItemStack stackFor(FuelLoaderPersistence.ItemState state) {
        return switch (state) {
            case EMPTY -> ItemStack.EMPTY;
            case FUEL_CELL -> new ItemStack(ModItems.ROCKET_FUEL_CELL.get());
            case EMPTY_CANISTER -> new ItemStack(ModItems.EMPTY_CANISTER.get());
        };
    }

    private void setStatus(FuelLoaderStatus newStatus) {
        if (status != newStatus) {
            status = newStatus;
            setChanged();
        }
    }

    @Nonnull
    @Override
    public <T> LazyOptional<T> getCapability(@Nonnull Capability<T> capability, @Nullable Direction side) {
        if (capability == ForgeCapabilities.ITEM_HANDLER) {
            return itemCapability.cast();
        }
        return super.getCapability(capability, side);
    }

    @Override
    public void invalidateCaps() {
        super.invalidateCaps();
        itemCapability.invalidate();
    }

    @Override
    public void reviveCaps() {
        super.reviveCaps();
        createCapabilityView();
    }

    private void createCapabilityView() {
        itemCapability = LazyOptional.of(() -> inventory);
    }

    public IItemHandler itemHandler() {
        return inventory;
    }

    public long bufferedUnits() {
        return bufferedUnits;
    }

    public Optional<UUID> ownerId() {
        return Optional.ofNullable(ownerId);
    }

    public Optional<UUID> targetRocketId() {
        return Optional.ofNullable(targetRocketId);
    }

    public FuelLoaderStatus status() {
        return status;
    }

    private final class LoaderInventory extends ItemStackHandler {
        private boolean internal;

        private LoaderInventory() {
            super(SLOT_COUNT);
        }

        @Override
        public boolean isItemValid(int slot, @Nonnull ItemStack stack) {
            return slot == SLOT
                    && bufferedUnits == 0L
                    && stack.is(ModItems.ROCKET_FUEL_CELL.get());
        }

        @Override
        public int getSlotLimit(int slot) {
            return 1;
        }

        @Nonnull
        @Override
        public ItemStack insertItem(int slot, @Nonnull ItemStack stack, boolean simulate) {
            if (!internal && (futureSchemaBlocked || invalidDataBlocked || ownerId == null)) {
                return stack;
            }
            return super.insertItem(slot, stack, simulate);
        }

        @Nonnull
        @Override
        public ItemStack extractItem(int slot, int amount, boolean simulate) {
            if (!internal && bufferedUnits > 0L) {
                return ItemStack.EMPTY;
            }
            return super.extractItem(slot, amount, simulate);
        }

        @Override
        protected void onContentsChanged(int slot) {
            FuelLoaderBlockEntity.this.setChanged();
        }

        private void setInternal(ItemStack stack) {
            internal = true;
            try {
                setStackInSlot(SLOT, stack);
            } finally {
                internal = false;
            }
        }

        private ItemStack extractInternal() {
            internal = true;
            try {
                return extractItem(SLOT, 1, false);
            } finally {
                internal = false;
            }
        }
    }
}
