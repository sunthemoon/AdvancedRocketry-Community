package io.github.sunthemoon.advancedrocketrycommunity.rocket.fuel;

import io.github.sunthemoon.advancedrocketrycommunity.registry.ModBlockEntities;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.entity.RocketEntity;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFlightLimits;
import java.util.Comparator;
import java.util.Optional;
import java.util.UUID;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
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
import net.minecraftforge.registries.ForgeRegistries;

/** One-slot, owner-bound loader with a frozen consumed batch and native queued/output items. */
public final class FuelLoaderBlockEntity extends BlockEntity {
    public static final int SLOT = 0;
    public static final int SLOT_COUNT = 1;
    public static final double MAX_RANGE = 6.0D;

    private LazyOptional<IItemHandler> itemCapability;
    private LoaderInventory inventory;
    private CompoundTag item = new CompoundTag();
    private FuelLoaderData.Role role = FuelLoaderData.Role.EMPTY;
    private FuelLoaderData.Batch batch;
    private long bufferedUnits;
    private UUID ownerId;
    private UUID targetRocketId;
    private FuelLoaderStatus status = FuelLoaderStatus.UNCLAIMED;
    private Tag preservedBlockedData;
    private boolean quarantined;
    private boolean future;

    public FuelLoaderBlockEntity(BlockPos position, BlockState state) {
        super(ModBlockEntities.FUEL_LOADER.get(), position, state);
        createCapabilityView();
    }

    public static void serverTick(Level level, BlockPos position, BlockState state, FuelLoaderBlockEntity loader) {
        if (level instanceof ServerLevel serverLevel && !loader.isRemoved()) { loader.tickServer(serverLevel); }
    }

    private void tickServer(ServerLevel level) {
        if (preservedBlockedData != null) {
            setStatus(future ? FuelLoaderStatus.UNSUPPORTED_DATA : FuelLoaderStatus.INVALID_DATA);
            return;
        }
        if (ownerId == null) { setStatus(FuelLoaderStatus.UNCLAIMED); return; }
        if (bufferedUnits == 0L) {
            if (role != FuelLoaderData.Role.INPUT) {
                setStatus(role == FuelLoaderData.Role.OUTPUT ? FuelLoaderStatus.OUTPUT_READY : FuelLoaderStatus.IDLE);
                return;
            }
            var definition = RocketFuelRuntime.find(ForgeRegistries.ITEMS.getValue(
                    ResourceLocation.tryParse(item.getString("id"))));
            if (definition == null) { setStatus(FuelLoaderStatus.UNSUPPORTED_FUEL); return; }
            Optional<RocketEntity> target = findTarget(level);
            if (target.isEmpty()) { setStatus(FuelLoaderStatus.WAITING_FOR_ROCKET); return; }
            // Prepare and validate everything before consuming the queued item.
            FuelLoaderData.Batch prepared;
            try {
                CompoundTag remainder = definition.remainder().isPresent()
                        ? FuelItemPayloads.captureOne(new ItemStack(definition.remainder().get(), 1)) : new CompoundTag();
                prepared = new FuelLoaderData.Batch(definition.id().toString(), definition.units(), remainder);
            } catch (RuntimeException exception) {
                setStatus(FuelLoaderStatus.INVALID_DATA);
                return;
            }
            item = new CompoundTag();
            role = FuelLoaderData.Role.EMPTY;
            batch = prepared;
            bufferedUnits = prepared.totalUnits();
            targetRocketId = target.get().getUUID();
            setChanged();
        }
        Optional<RocketEntity> selected = target(level).or(() -> findTarget(level));
        if (selected.isEmpty()) {
            if (targetRocketId != null) { targetRocketId = null; setChanged(); }
            setStatus(FuelLoaderStatus.WAITING_FOR_ROCKET);
            return;
        }
        RocketEntity rocket = selected.get();
        var flightData = rocket.flightData().orElseThrow();
        var mutation = flightData.fuel().fill(Math.min(RocketFlightLimits.FUEL_TRANSFER_PER_TICK, bufferedUnits));
        if (!mutation.success()) { setStatus(FuelLoaderStatus.WAITING_FOR_ROCKET); return; }
        rocket.updateFlightData(flightData.withFuel(mutation.state(), level.getGameTime()));
        targetRocketId = rocket.getUUID();
        bufferedUnits -= mutation.unitsChanged();
        if (bufferedUnits == 0L) {
            item = batch.remainder();
            role = item.isEmpty() ? FuelLoaderData.Role.EMPTY : FuelLoaderData.Role.OUTPUT;
            batch = null;
            targetRocketId = null;
            setStatus(item.isEmpty() ? FuelLoaderStatus.IDLE : FuelLoaderStatus.OUTPUT_READY);
        } else { setStatus(FuelLoaderStatus.TRANSFERRING); }
        setChanged();
    }

    private Optional<RocketEntity> target(ServerLevel level) {
        Entity entity = targetRocketId == null ? null : level.getEntity(targetRocketId);
        return entity instanceof RocketEntity rocket && eligible(rocket) ? Optional.of(rocket) : Optional.empty();
    }

    private Optional<RocketEntity> findTarget(ServerLevel level) {
        Comparator<RocketEntity> nearest = Comparator.comparingDouble((RocketEntity rocket) -> rocket.distanceToSqr(
                worldPosition.getX() + 0.5D, worldPosition.getY() + 0.5D, worldPosition.getZ() + 0.5D))
                .thenComparing(RocketEntity::getUUID);
        return level.getEntitiesOfClass(RocketEntity.class, new AABB(worldPosition).inflate(MAX_RANGE), this::eligible)
                .stream().min(nearest);
    }

    private boolean eligible(RocketEntity rocket) {
        return rocket.operational() && ownerId != null && rocket.ownerId().filter(ownerId::equals).isPresent()
                && rocket.distanceToSqr(worldPosition.getX() + 0.5D, worldPosition.getY() + 0.5D,
                        worldPosition.getZ() + 0.5D) <= MAX_RANGE * MAX_RANGE
                && rocket.flightData().filter(data -> data.state().acceptsFuel())
                        .filter(data -> data.fuel().remainingCapacity() > 0L).isPresent();
    }

    private boolean mutable() {
        return !isRemoved() && preservedBlockedData == null && level instanceof ServerLevel server
                && server.getServer().isSameThread();
    }

    public void assignOwner(UUID id) {
        if (mutable() && ownerId == null) {
            ownerId = java.util.Objects.requireNonNull(id, "ownerId");
            setStatus(FuelLoaderStatus.IDLE);
            setChanged();
        }
    }

    public boolean authorized(Player player) {
        return ownerId == null || ownerId.equals(player.getUUID()) || player.hasPermissions(2);
    }

    private boolean usableBy(Player player) {
        return mutable() && player.level() == level && player.isAlive() && !player.isSpectator() && authorized(player)
                && player.distanceToSqr(worldPosition.getX() + 0.5D, worldPosition.getY() + 0.5D,
                        worldPosition.getZ() + 0.5D) <= 64D;
    }

    public boolean insertFuelFromPlayer(Player player, InteractionHand hand) {
        if (!usableBy(player)) { return false; }
        ItemStack held = player.getItemInHand(hand);
        if (!canInsert(held)) { return false; }
        CompoundTag prepared;
        try { prepared = FuelItemPayloads.captureOne(held); }
        catch (RuntimeException exception) { return false; }
        assignOwner(player.getUUID());
        item = prepared;
        role = FuelLoaderData.Role.INPUT;
        if (!player.getAbilities().instabuild) { held.shrink(1); }
        setChanged();
        return true;
    }

    /** Inputs can also be recovered, including when their loading definition is absent. */
    public boolean takeOutput(Player player) {
        if (!usableBy(player) || bufferedUnits > 0L || item.isEmpty()) { return false; }
        ItemStack output;
        try { output = FuelItemPayloads.decode(item); }
        catch (RuntimeException exception) { return false; }
        item = new CompoundTag();
        role = FuelLoaderData.Role.EMPTY;
        if (!player.getInventory().add(output)) { player.drop(output, false); }
        setChanged();
        return true;
    }

    private boolean canInsert(ItemStack stack) {
        return bufferedUnits == 0L && item.isEmpty() && !stack.isEmpty() && RocketFuelRuntime.find(stack.getItem()) != null;
    }

    @Override
    protected void saveAdditional(CompoundTag parent) {
        super.saveAdditional(parent);
        parent.put(FuelLoaderStorage.DATA_KEY, savedRoot());
    }

    private Tag savedRoot() {
        if (preservedBlockedData != null) {
            // Do not throw here: native chunk saving would omit this block entity.
            return quarantined ? preservedBlockedData : preservedBlockedData.copy();
        }
        return FuelLoaderStorage.encode(new FuelLoaderData(role, item, bufferedUnits, ownerId, targetRocketId, batch));
    }

    public boolean canCarryData() { return !quarantined; }

    /** Caller uses only a real loader drop. Ownership is preserved even when empty. */
    public CompoundTag carriedData() {
        if (quarantined) { throw new IllegalStateException("Quarantined loader requires offline repair"); }
        CompoundTag parent = new CompoundTag();
        parent.put(FuelLoaderStorage.DATA_KEY, savedRoot());
        return parent;
    }

    @Override
    public void load(CompoundTag parent) {
        super.load(parent);
        item = new CompoundTag(); role = FuelLoaderData.Role.EMPTY; batch = null;
        bufferedUnits = 0L; ownerId = null; targetRocketId = null; preservedBlockedData = null;
        var decoded = FuelLoaderStorage.decode(parent);
        future = decoded.future(); quarantined = decoded.quarantined();
        if (!decoded.valid()) {
            preservedBlockedData = decoded.preserved();
        } else {
            var data = decoded.data();
            try {
                FuelItemPayloads.decode(data.item());
                if (data.batch() != null) { FuelItemPayloads.decode(data.batch().remainder()); }
                item = data.item(); role = data.role(); batch = data.batch(); bufferedUnits = data.bufferedUnits();
                ownerId = data.ownerId(); targetRocketId = data.targetRocketId();
            } catch (RuntimeException exception) {
                Tag raw = parent.get(FuelLoaderStorage.DATA_KEY);
                preservedBlockedData = raw == null ? FuelLoaderStorage.encode(data) : raw.copy();
            }
        }
        status = preservedBlockedData != null ? (future ? FuelLoaderStatus.UNSUPPORTED_DATA : FuelLoaderStatus.INVALID_DATA)
                : ownerId == null ? FuelLoaderStatus.UNCLAIMED : FuelLoaderStatus.IDLE;
    }

    private void setStatus(FuelLoaderStatus value) {
        if (status != value) { status = value; setChanged(); }
    }

    @Nonnull @Override
    public <T> LazyOptional<T> getCapability(@Nonnull Capability<T> capability, @Nullable Direction side) {
        return capability == ForgeCapabilities.ITEM_HANDLER ? itemCapability.cast() : super.getCapability(capability, side);
    }

    @Override public void invalidateCaps() { super.invalidateCaps(); inventory.active = false; itemCapability.invalidate(); }
    @Override public void reviveCaps() { super.reviveCaps(); createCapabilityView(); }
    private void createCapabilityView() {
        if (inventory != null) { inventory.active = false; }
        inventory = new LoaderInventory();
        LoaderInventory view = inventory;
        itemCapability = LazyOptional.of(() -> view);
    }

    public IItemHandler itemHandler() { return inventory; }
    public long bufferedUnits() { return bufferedUnits; }
    public Optional<UUID> ownerId() { return Optional.ofNullable(ownerId); }
    public Optional<UUID> targetRocketId() { return Optional.ofNullable(targetRocketId); }
    public FuelLoaderStatus status() { return status; }

    private final class LoaderInventory implements IItemHandler {
        private boolean active = true;
        private void check(int slot) { if (slot != SLOT) { throw new IllegalArgumentException("Unknown loader slot"); } }
        @Override public int getSlots() { return SLOT_COUNT; }
        @Override public int getSlotLimit(int slot) { check(slot); return 1; }
        @Override public boolean isItemValid(int slot, @Nonnull ItemStack stack) {
            check(slot);
            return active && mutable() && canInsert(stack);
        }
        @Nonnull @Override public ItemStack getStackInSlot(int slot) {
            check(slot);
            if (!active || !mutable()) { return ItemStack.EMPTY; }
            try { return FuelItemPayloads.decode(item); }
            catch (RuntimeException exception) { return ItemStack.EMPTY; }
        }
        @Nonnull @Override public ItemStack insertItem(int slot, @Nonnull ItemStack stack, boolean simulate) {
            check(slot);
            if (!isItemValid(slot, stack) || ownerId == null) { return stack; }
            CompoundTag prepared;
            try { prepared = FuelItemPayloads.captureOne(stack); }
            catch (RuntimeException exception) { return stack; }
            ItemStack remainder = stack.copy();
            remainder.shrink(1);
            if (!simulate) { item = prepared; role = FuelLoaderData.Role.INPUT; setChanged(); }
            return remainder;
        }
        @Nonnull @Override public ItemStack extractItem(int slot, int amount, boolean simulate) {
            check(slot);
            if (!active || !mutable() || bufferedUnits > 0L || amount <= 0 || item.isEmpty()) { return ItemStack.EMPTY; }
            ItemStack result;
            try { result = FuelItemPayloads.decode(item); }
            catch (RuntimeException exception) { return ItemStack.EMPTY; }
            if (!simulate) { item = new CompoundTag(); role = FuelLoaderData.Role.EMPTY; setChanged(); }
            return result;
        }
    }
}
