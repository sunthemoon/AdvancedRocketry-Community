package io.github.sunthemoon.advancedrocketrycommunity.rocket.bay;

import com.mojang.logging.LogUtils;
import io.github.sunthemoon.advancedrocketrycommunity.persistence.GuardedChunkSaves;
import io.github.sunthemoon.advancedrocketrycommunity.registry.ModBlockEntities;
import io.github.sunthemoon.advancedrocketrycommunity.registry.ModBlocks;
import io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.BayReceipts;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketPosition;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.persistence.RocketTransactionSavedData;
import java.util.Optional;
import java.util.UUID;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.items.IItemHandler;
import org.slf4j.Logger;

/**
 * One-slot rocket satellite bay (W27A-DESIGN section 6). It is neither a {@code Container} nor a
 * {@code WorldlyContainerHolder}: its menu and its {@code ITEM_HANDLER} capability are its only surfaces, and both
 * go through one handler, so one lock covers hoppers, droppers and the menu. Deployment is disabled until W27d.
 */
public final class SatelliteBayBlockEntity extends BlockEntity implements MenuProvider {
    public static final double MAX_MENU_DISTANCE = 8.0D;
    public static final String SAVE_REFUSAL =
            "Refusing chunk save with a satellite bay root that native serialization cannot keep; back up and repair offline";
    private static final Logger LOGGER = LogUtils.getLogger();

    /** The root exactly as loaded or last written; null for an empty bay that has no owner. */
    @Nullable private Tag held;
    /** Present exactly when {@link #held} decodes as a schema-1 root. */
    @Nullable private SatelliteBayRoot.Contents contents;
    /** Present exactly when the decoded root holds a package. */
    @Nullable private SatelliteBayPackage.Decoded packageState;
    private boolean future;
    /** The held root cannot be written unchanged; the chunk-save guard refuses its terrain chunk. */
    private boolean unkeepable;
    /** No recursive copy when native validation failed or exhausted its work budget. */
    private boolean retainedByReference;
    /** Shared by the loot and removal callbacks, so one removal emits the package at most once. */
    private boolean extracted;
    private BayInventory inventory;
    private LazyOptional<IItemHandler> itemCapability;

    public SatelliteBayBlockEntity(BlockPos position, BlockState state) {
        super(ModBlockEntities.ROCKET_SATELLITE_BAY.get(), position, state);
        createCapabilityView();
    }

    /** The owner, an identity and guarded roots must never load from a non-operator's item NBT. */
    @Override
    public boolean onlyOpCanSetNbt() {
        return true;
    }

    public Optional<UUID> ownerId() {
        return contents == null ? Optional.empty() : Optional.of(contents.owner());
    }

    public Optional<SatelliteBayRoot.Contents> contents() {
        return Optional.ofNullable(contents);
    }

    public Optional<SatelliteBayPackage.Decoded> packageState() {
        return Optional.ofNullable(packageState);
    }

    /** A detached copy when preflight permits one, otherwise the retained raw reference; empty for no root. */
    public Optional<Tag> heldRoot() {
        return held == null ? Optional.empty() : Optional.of(retainedByReference ? held : held.copy());
    }

    public boolean unkeepable() {
        return unkeepable;
    }

    /** The data guard of the held root and package, ignoring transaction claims. */
    public SatelliteBayStatus.Guard dataGuard() {
        if (held != null && contents == null) {
            return future ? SatelliteBayStatus.Guard.UNSUPPORTED_ROOT : SatelliteBayStatus.Guard.QUARANTINED;
        }
        if (packageState == null || packageState.available()) {
            return SatelliteBayStatus.Guard.NONE;
        }
        return switch (packageState.availability()) {
            case UNSUPPORTED_DATA -> SatelliteBayStatus.Guard.UNSUPPORTED_DATA;
            case PACKAGE_UNAVAILABLE -> SatelliteBayStatus.Guard.PACKAGE_UNAVAILABLE;
            default -> SatelliteBayStatus.Guard.INVALID_PACKAGE;
        };
    }

    public boolean guarded() {
        return dataGuard() != SatelliteBayStatus.Guard.NONE;
    }

    /** Data guards first, then the transaction claim. */
    public SatelliteBayStatus.Guard guard() {
        SatelliteBayStatus.Guard data = dataGuard();
        return data != SatelliteBayStatus.Guard.NONE ? data
                : claimed() ? SatelliteBayStatus.Guard.CLAIMED : SatelliteBayStatus.Guard.NONE;
    }

    /**
     * Guarded roots and claimed positions refuse player break, leave explosions and drop nothing. The client holds no
     * root, so only the server decides; a client-side copy never blocks mining progress.
     */
    public boolean protectedFromRemoval() {
        return level != null && !level.isClientSide && guard() != SatelliteBayStatus.Guard.NONE;
    }

    /**
     * Whether an assembly or disassembly transaction claims this position. Fails closed off the server, and while
     * the transaction journal cannot be read.
     */
    public boolean claimed() {
        if (!(level instanceof ServerLevel server)) {
            return true;
        }
        return RocketTransactionSavedData.get(server.getServer()).claims(server.dimension().location(),
                new RocketPosition(worldPosition.getX(), worldPosition.getY(), worldPosition.getZ()));
    }

    /**
     * The phase and reason menu slots (W27d1): the package's receipt, else its runtime rejection (phase NONE), else NONE
     * with DEPLOYMENT_UNAVAILABLE. Server thread only; elsewhere the fixed pair.
     */
    public SatelliteBayStatus.Phase phase() {
        return status().map(status -> SatelliteBayStatus.Phase.fromName(status.phase())).orElse(SatelliteBayStatus.Phase.NONE);
    }

    public SatelliteBayStatus.Reason reason() {
        return status().map(status -> SatelliteBayStatus.Reason.fromName(status.reason()))
                .orElse(SatelliteBayStatus.Reason.DEPLOYMENT_UNAVAILABLE);
    }

    private Optional<io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.BayDeploymentSession.Status> status() {
        if (!(level instanceof ServerLevel server) || packageState == null) {
            return Optional.empty();
        }
        return packageState.identity().flatMap(identity -> BayReceipts.deploymentStatus(server.getServer(), identity.satelliteId()));
    }

    /** This exact instance is the loaded, server-thread block entity of a bay block. */
    private boolean live() {
        if (isRemoved() || !(level instanceof ServerLevel server) || !server.getServer().isSameThread()) {
            return false;
        }
        var chunk = server.getChunkSource().getChunkNow(worldPosition.getX() >> 4, worldPosition.getZ() >> 4);
        return chunk != null && chunk.getBlockEntities().get(worldPosition) == this
                && getBlockState().is(ModBlocks.ROCKET_SATELLITE_BAY.get());
    }

    /** The owner or an operator, never a spectator or a fake player, within 8 blocks of an unguarded bay. */
    public boolean admitted(Player player) {
        return live() && !guarded() && player instanceof ServerPlayer && !(player instanceof FakePlayer)
                && player.isAlive() && !player.isSpectator() && player.level() == level
                && player.distanceToSqr(worldPosition.getX() + 0.5D, worldPosition.getY() + 0.5D,
                        worldPosition.getZ() + 0.5D) <= MAX_MENU_DISTANCE * MAX_MENU_DISTANCE
                && level.mayInteract(player, worldPosition)
                && (contents != null && contents.owner().equals(player.getUUID()) || player.hasPermissions(2));
    }

    /** A new bay gets its placing player's UUID; an existing root (an operator's item NBT) is kept. */
    public void assignPlacer(Player player) {
        if (held == null && live()) {
            write(SatelliteBayRoot.Contents.empty(player.getUUID()));
        }
    }

    /**
     * The one-item package tag {@code offered} would be stored as, when it may enter this bay now: owned by the bay's
     * owner, unclaimed, and with no INTENT, REGISTERED or REPAIR receipt for its satellite (W27c1; fails closed while
     * the receipt index is not loaded). Every surface, simulated or actual, goes through this one check.
     */
    private Optional<CompoundTag> admissible(ItemStack offered) {
        if (contents == null || contents.pkg() != null || contents.consumedOp() != null || !live()) {
            return Optional.empty();
        }
        Optional<CompoundTag> pkg = SatelliteBayPackage.capture(offered);
        var identity = pkg.flatMap(captured -> SatelliteBayPackage.decode(captured).identity())
                .filter(candidate -> candidate.ownerId().equals(contents.owner()));
        if (identity.isEmpty() || claimed()
                || BayReceipts.blocksInsertion(((ServerLevel) level).getServer(), identity.get().satelliteId())) {
            return Optional.empty();
        }
        return pkg;
    }

    private ItemStack extract(boolean simulate) {
        if (contents == null || packageState == null || !packageState.available() || !live() || claimed()) {
            return ItemStack.EMPTY;
        }
        ItemStack result = packageState.liveCopy();
        if (!simulate) {
            write(SatelliteBayRoot.Contents.empty(contents.owner()));
        }
        return result;
    }

    private void write(SatelliteBayRoot.Contents next) {
        CompoundTag root = SatelliteBayRoot.encode(next);
        held = root;
        contents = next;
        packageState = next.pkg() == null ? null : SatelliteBayPackage.decode(next.pkg());
        future = false;
        unkeepable = false;
        retainedByReference = false;
        extracted = false;
        setChanged();
    }

    /**
     * The package as its ordinary item, once per removal: loot and the removal callback share {@link #extracted}.
     * A guarded, claimed, consumed or empty bay yields EMPTY.
     */
    ItemStack takePackageForRemoval() {
        if (extracted || contents == null || packageState == null || !packageState.available() || claimed()) {
            return ItemStack.EMPTY;
        }
        extracted = true;
        ItemStack result = packageState.liveCopy();
        UUID owner = contents.owner();
        held = SatelliteBayRoot.encode(SatelliteBayRoot.Contents.empty(owner));
        contents = SatelliteBayRoot.Contents.empty(owner);
        packageState = null;
        if (!isRemoved()) {
            setChanged();
        }
        return result;
    }

    /** Detection only: a guarded root removed by a raw replacement such as {@code /setblock}. */
    void noteRemoval() {
        SatelliteBayStatus.Guard guard = dataGuard();
        if (guard != SatelliteBayStatus.Guard.NONE) {
            LOGGER.warn("ARCE_SATELLITE_BAY_GUARDED_ROOT_REMOVED pos={} guard={}", worldPosition.toShortString(), guard);
        }
    }

    /** The rocket adapter's exact copy of the root; an empty compound for an empty unowned bay. */
    public CompoundTag captureRoot() {
        if (held == null) {
            return new CompoundTag();
        }
        if (contents == null) {
            throw new IllegalArgumentException("A quarantined satellite bay root cannot be captured");
        }
        return ((CompoundTag) held).copy();
    }

    /** Restores a captured root into a freshly placed bay; the adapter then recaptures and compares it. */
    public boolean restoreRoot(CompoundTag captured) {
        if (held != null) {
            return false;
        }
        if (captured.isEmpty()) {
            return true;
        }
        SatelliteBayRoot.Decoded decoded = SatelliteBayRoot.decode(captured);
        if (!decoded.valid()) {
            return false;
        }
        held = captured.copy();
        contents = decoded.contents().orElseThrow();
        packageState = contents.pkg() == null ? null : SatelliteBayPackage.decode(contents.pkg());
        future = false;
        unkeepable = false;
        retainedByReference = false;
        extracted = false;
        setChanged();
        return true;
    }

    @Override
    protected void saveAdditional(CompoundTag outer) {
        super.saveAdditional(outer);
        if (held != null) {
            // Unchecked roots stay verbatim too, but only an observed incompatibility denies the chunk save.
            outer.put(SatelliteBayRoot.KEY, retainedByReference ? held : held.copy());
        }
    }

    @Override
    public void load(CompoundTag outer) {
        super.load(outer);
        if (level != null && !isRemoved() && guarded()) {
            // A live /data or structure replacement keeps a held guarded root unchanged.
            LOGGER.warn("ARCE_SATELLITE_BAY_LIVE_REPLACEMENT_REFUSED pos={} guard={}",
                    worldPosition.toShortString(), dataGuard());
            return;
        }
        held = null;
        contents = null;
        packageState = null;
        future = false;
        unkeepable = false;
        retainedByReference = false;
        extracted = false;
        Tag raw = outer.get(SatelliteBayRoot.KEY);
        if (raw == null) {
            return;
        }
        SatelliteBayNativeCheck.Result nativeCheck = SatelliteBayNativeCheck.inspect(raw);
        unkeepable = nativeCheck == SatelliteBayNativeCheck.Result.INCOMPATIBLE;
        retainedByReference = nativeCheck != SatelliteBayNativeCheck.Result.LOSSLESS;
        held = retainedByReference ? raw : raw.copy();
        SatelliteBayRoot.Decoded decoded = SatelliteBayRoot.decode(held);
        if (decoded.valid()) {
            contents = decoded.contents().orElseThrow();
            packageState = contents.pkg() == null ? null : SatelliteBayPackage.decode(contents.pkg());
        } else {
            future = decoded.status() == SatelliteBayRoot.Status.FUTURE;
        }
        if (guarded()) {
            LOGGER.warn("ARCE_SATELLITE_BAY_GUARDED pos={} guard={} keepable={}",
                    worldPosition.toShortString(), dataGuard(), !unkeepable);
        }
        if (unkeepable && level instanceof ServerLevel server) {
            GuardedChunkSaves.recordObservedDenial(server, new ChunkPos(worldPosition), SAVE_REFUSAL);
        }
    }

    @Override
    public void onLoad() {
        super.onLoad();
        if (unkeepable && level instanceof ServerLevel server) {
            GuardedChunkSaves.recordObservedDenial(server, new ChunkPos(worldPosition), SAVE_REFUSAL);
        }
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("block.advancedrocketrycommunity.rocket_satellite_bay");
    }

    @Nullable
    @Override
    public AbstractContainerMenu createMenu(int id, Inventory playerInventory, Player player) {
        return admitted(player) ? new SatelliteBayMenu(id, playerInventory, this) : null;
    }

    @Nonnull
    @Override
    public <T> LazyOptional<T> getCapability(@Nonnull Capability<T> capability, @Nullable Direction side) {
        return capability == ForgeCapabilities.ITEM_HANDLER ? itemCapability.cast() : super.getCapability(capability, side);
    }

    @Override
    public void invalidateCaps() {
        super.invalidateCaps();
        inventory.active = false;
        itemCapability.invalidate();
    }

    @Override
    public void reviveCaps() {
        super.reviveCaps();
        createCapabilityView();
    }

    private void createCapabilityView() {
        if (inventory != null) {
            inventory.active = false;
        }
        inventory = new BayInventory();
        BayInventory view = inventory;
        itemCapability = LazyOptional.of(() -> view);
    }

    /** The single handler behind the capability and the menu slot. */
    public IItemHandler itemHandler() {
        return inventory;
    }

    private final class BayInventory implements IItemHandler {
        private boolean active = true;

        private void check(int slot) {
            if (slot != 0) {
                throw new IllegalArgumentException("Unknown satellite bay slot " + slot);
            }
        }

        @Override
        public int getSlots() {
            return 1;
        }

        @Override
        public int getSlotLimit(int slot) {
            check(slot);
            return 1;
        }

        @Nonnull
        @Override
        public ItemStack getStackInSlot(int slot) {
            check(slot);
            return active && packageState != null && live() ? packageState.liveCopy() : ItemStack.EMPTY;
        }

        @Override
        public boolean isItemValid(int slot, @Nonnull ItemStack stack) {
            check(slot);
            return active && admissible(stack).isPresent();
        }

        @Nonnull
        @Override
        public ItemStack insertItem(int slot, @Nonnull ItemStack stack, boolean simulate) {
            check(slot);
            Optional<CompoundTag> pkg = active ? admissible(stack) : Optional.empty();
            if (pkg.isEmpty()) {
                return stack;
            }
            if (!simulate) {
                write(new SatelliteBayRoot.Contents(contents.owner(), pkg.get(), null));
            }
            return stack.getCount() == 1 ? ItemStack.EMPTY : stack.copyWithCount(stack.getCount() - 1);
        }

        @Nonnull
        @Override
        public ItemStack extractItem(int slot, int amount, boolean simulate) {
            check(slot);
            return active && amount > 0 ? extract(simulate) : ItemStack.EMPTY;
        }
    }
}
