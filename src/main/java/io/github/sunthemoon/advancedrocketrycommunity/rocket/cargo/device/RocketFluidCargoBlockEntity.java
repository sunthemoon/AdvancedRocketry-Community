package io.github.sunthemoon.advancedrocketrycommunity.rocket.cargo.device;

import com.mojang.logging.LogUtils;
import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import io.github.sunthemoon.advancedrocketrycommunity.persistence.GuardedChunkSaves;
import io.github.sunthemoon.advancedrocketrycommunity.registry.ModBlockEntities;
import io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.RocketCargoDeviceHold;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.bay.SatelliteBayRoot;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.fuel.loader.LoaderBinding;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.fuel.loader.LoaderCarrier;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.fuel.loader.LoaderTank;
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
import net.minecraft.world.InteractionHand;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.fluids.capability.IFluidHandler;
import org.slf4j.Logger;

/** Native lifecycle and authority bridge. Cargo admission is gated and stops at acknowledged PREPARED. */
public final class RocketFluidCargoBlockEntity extends BlockEntity implements MenuProvider {
    public static final double MAX_ACTOR_DISTANCE = 8.0D;
    public static final String SAVE_REFUSAL =
            "Refusing chunk save with a fluid cargo root that native serialization cannot keep; back up and repair offline";
    private static final Logger LOGGER = LogUtils.getLogger();
    private final RocketCargoRole role;
    @Nullable private Tag held;
    @Nullable private RocketFluidCargoRoot root;
    private RocketFluidCargoRoot.Status status = RocketFluidCargoRoot.Status.ABSENT;
    private boolean retainedByReference;
    private boolean unkeepable;
    private boolean operationLock;
    private long capEpoch;
    private LazyOptional<IFluidHandler> fluidCap;
    private final io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.EscrowFluidCargoAdmission admission =
            new io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.EscrowFluidCargoAdmission(this);
    private io.github.sunthemoon.advancedrocketrycommunity.rocket.fuel.loader.RefillPacer pacer;
    private final io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.EscrowDeviceTurns turns =
            new io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.EscrowDeviceTurns();
    public boolean refreshTurns() {
        if (!live() || root == null || root.binding() == null) { turns.close(); return false; }
        return turns.refresh(((ServerLevel) level).getServer(), root.device(), root.binding().logicalRocket());
    }
    public void transferBlocked() { if (refreshTurns()) { turns.blocked(); } }
    public boolean transferTurn() { return refreshTurns() && turns.allows(); }
    public boolean claimTransferTurn() { return refreshTurns() && turns.claim(); }
    public void releaseTransferTurn() { turns.release(); }

    public RocketFluidCargoBlockEntity(BlockPos position, BlockState state) {
        super(((RocketFluidCargoBlock) state.getBlock()).role() == RocketCargoRole.LOAD
                ? ModBlockEntities.ROCKET_FLUID_LOADER.get() : ModBlockEntities.ROCKET_FLUID_UNLOADER.get(), position, state);
        role = ((RocketFluidCargoBlock) state.getBlock()).role();
        createCap();
    }

    @Override public boolean onlyOpCanSetNbt() { return true; }
    public RocketCargoRole role() { return role; }
    public Optional<RocketFluidCargoRoot> root() { return Optional.ofNullable(root); }
    public RocketFluidCargoRoot.Status dataStatus() { return status; }
    public Optional<Tag> heldRoot() { return held == null ? Optional.empty() : Optional.of(retainedByReference ? held : held.copy()); }
    public Optional<UUID> owner() { return root == null ? Optional.empty() : Optional.ofNullable(root.owner()); }
    public boolean claimed() { return owner().isPresent(); }
    public boolean unsupportedRoot() { return !status.supported(); }
    public boolean unkeepable() { return unkeepable; }
    public LoaderTank tank() { return root == null || unsupportedRoot() ? LoaderTank.EMPTY : root.tank(); }

    public boolean duplicate() {
        return root != null && live() && !RocketFluidCargoIndex.claim((ServerLevel) level, root.device(), this);
    }

    public boolean escrowLocked() {
        if (root == null) { return false; }
        return root.marked() || !(level instanceof ServerLevel server) || !server.getServer().isSameThread()
                || RocketCargoDeviceHold.locked(server, root.device(), root.escrow().ledger(),
                        ModIdentity.id(role.id()).toString(), worldPosition);
    }

    public boolean protectedFromRemoval() {
        return level != null && !level.isClientSide && (unsupportedRoot() || escrowLocked());
    }

    /** Exact loaded instance, block role, world bounds and server thread; never loads a chunk. */
    public boolean live() {
        if (isRemoved() || !(level instanceof ServerLevel server) || !server.getServer().isSameThread()
                || !server.isInWorldBounds(worldPosition) || !server.getWorldBorder().isWithinBounds(worldPosition)) { return false; }
        var chunk = server.getChunkSource().getChunkNow(worldPosition.getX() >> 4, worldPosition.getZ() >> 4);
        return chunk != null && chunk.getBlockEntities().get(worldPosition) == this
                && chunk.getBlockState(worldPosition).getBlock() instanceof RocketFluidCargoBlock block && block.role() == role;
    }

    @Nullable public RocketCargoStatus deviceRefusal() {
        if (status == RocketFluidCargoRoot.Status.INVALID) { return RocketCargoStatus.INVALID_DATA; }
        if (unsupportedRoot()) { return RocketCargoStatus.UNSUPPORTED_DATA; }
        if (!live()) { return RocketCargoStatus.WAITING_UNLOADED; }
        if (duplicate()) { return RocketCargoStatus.INVALID_DATA; }
        if (!claimed()) { return RocketCargoStatus.NOT_OWNER; }
        if (escrowLocked()) { return RocketCargoStatus.ESCROW_PENDING; }
        return root.binding() == null ? RocketCargoStatus.UNBOUND : null;
    }

    /** Scalar display only; admission and resource authority remain on the server. */
    public RocketCargoStatus displayStatus() {
        RocketCargoStatus refusal = deviceRefusal();
        if (refusal != null) { return refusal; }
        return admission.status() == RocketCargoStatus.ADMISSION_DISABLED
                ? RocketCargoBindingChecks.check((ServerLevel) level, worldPosition, root) : admission.status();
    }
    public RocketCargoStatus attempt() { return admission.attempt(); }
    public static void serverTick(ServerLevel level, BlockPos pos, BlockState state, RocketFluidCargoBlockEntity device) {
        if (device.pacer == null) { device.pacer = io.github.sunthemoon.advancedrocketrycommunity.rocket.fuel.loader.RefillPacer.loadedAt(level.getGameTime()); }
        if (device.pacer.tryStart(level.getGameTime())) { device.attempt(); }
    }

    /** The server-selected cursor exists only while a transient preparation is active. */
    public Optional<BlockPos> cursor() { return admission.cursor(); }

    public boolean admitted(Player player) {
        return actorNear(player) && !unsupportedRoot() && claimed() && !duplicate() && root.owner().equals(player.getUUID());
    }

    boolean actorNear(Player player) {
        return live() && player instanceof ServerPlayer server && !(player instanceof FakePlayer)
                && !server.hasDisconnected() && player.isAlive() && !player.isSpectator() && player.level() == level
                && player.distanceToSqr(worldPosition.getX() + 0.5D, worldPosition.getY() + 0.5D,
                        worldPosition.getZ() + 0.5D) <= MAX_ACTOR_DISTANCE * MAX_ACTOR_DISTANCE
                && level.mayInteract(player, worldPosition);
    }

    /** Shared non-reentrant lock for the port and held-container exchange. */
    boolean beginLocal() {
        if (operationLock || !live() || unsupportedRoot() || !claimed() || duplicate() || escrowLocked()) { return false; }
        operationLock = true;
        return true;
    }
    void endLocal() { operationLock = false; }
    boolean localInProgress() { return operationLock; }
    void writeTank(LoaderTank next) {
        if (!operationLock) { throw new IllegalStateException("Local cargo mutation requires its lock"); }
        write(root.withTank(next));
    }
    public boolean exchange(Player player, InteractionHand hand) { return RocketCargoLocalTank.exchange(this, player, hand); }

    void placed(@Nullable UUID owner, LoaderTank tank) {
        if (!live() || protectedFromRemoval()) { return; }
        RocketFluidCargoIndex.release((ServerLevel) level, this);
        write(RocketFluidCargoRoot.placed(owner, UUID.randomUUID(), role, tank));
        claimDevice();
    }
    LoaderCarrier carrier() {
        if (protectedFromRemoval()) { throw new IllegalStateException("Protected cargo devices drop nothing"); }
        return new LoaderCarrier(owner().orElse(null), tank());
    }
    boolean bind(LoaderBinding binding) {
        if (!beginLocal()) { return false; }
        try { admission.close(); turns.close(); write(root.withBinding(binding)); refreshTurns(); return true; }
        finally { endLocal(); }
    }
    boolean clearBinding() {
        return root != null && root.binding() != null && bind(null);
    }
    private void write(RocketFluidCargoRoot next) {
        CompoundTag encoded = next.encode();
        held = encoded; root = next; status = RocketFluidCargoRoot.Status.VALID;
        retainedByReference = false; unkeepable = false;
        setChanged();
    }

    @Override protected void saveAdditional(CompoundTag outer) {
        super.saveAdditional(outer);
        if (held != null) { outer.put(RocketFluidCargoRoot.KEY, retainedByReference ? held : held.copy()); }
    }

    @Override public void load(CompoundTag outer) {
        // Refuse reentrant load without changing either native resources or superclass state.
        if (operationLock) { return; }
        if (level != null && !isRemoved() && protectedFromRemoval()) {
            LOGGER.warn("ARCE_FLUID_CARGO_LIVE_REPLACEMENT_REFUSED pos={}", worldPosition.toShortString());
            return;
        }
        super.load(outer);
        admission.close(); turns.close(); pacer = null;
        if (level instanceof ServerLevel server) { RocketFluidCargoIndex.release(server, this); }
        invalidatePort();
        held = null; root = null; retainedByReference = false; unkeepable = false;
        var read = RocketFluidCargoRoot.read(outer, role);
        status = read.status();
        if (status != RocketFluidCargoRoot.Status.ABSENT) {
            Tag raw = outer.get(RocketFluidCargoRoot.KEY);
            // Same native depth/envelope position as the bay. WORK_LIMIT is keepable, never a save veto.
            unkeepable = !SatelliteBayRoot.nativelyKeepable(raw);
            retainedByReference = !RocketFluidCargoRoot.bounded(raw);
            held = retainedByReference ? raw : raw.copy();
            root = read.root();
        }
        if (unsupportedRoot()) {
            LOGGER.warn("ARCE_FLUID_CARGO_UNSUPPORTED pos={} status={} keepable={}", worldPosition.toShortString(), status, !unkeepable);
        }
        recordDenial();
        createCap();
        claimDevice();
    }

    private void recordDenial() {
        if (unkeepable && level instanceof ServerLevel server) {
            GuardedChunkSaves.recordObservedDenial(server, new ChunkPos(worldPosition), SAVE_REFUSAL);
        }
    }
    private void claimDevice() {
        if (root != null && live()) { RocketFluidCargoIndex.claim((ServerLevel) level, root.device(), this); }
    }
    @Override public void onLoad() { super.onLoad(); recordDenial(); claimDevice(); refreshTurns(); }
    @Override public void setRemoved() {
        admission.close(); turns.close(); pacer = null;
        if (level instanceof ServerLevel server) { RocketFluidCargoIndex.release(server, this); }
        super.setRemoved();
    }
    @Override public void onChunkUnloaded() {
        admission.close(); turns.close(); pacer = null;
        if (level instanceof ServerLevel server) { RocketFluidCargoIndex.release(server, this); }
        invalidatePort();
        super.onChunkUnloaded();
    }
    @Override public void invalidateCaps() { super.invalidateCaps(); invalidatePort(); }
    @Override public void reviveCaps() { super.reviveCaps(); createCap(); }
    private void invalidatePort() { capEpoch++; if (fluidCap != null) { fluidCap.invalidate(); } }
    private void createCap() {
        long epoch = capEpoch;
        fluidCap = LazyOptional.of(() -> new RocketCargoFluidPort(this, epoch));
    }
    boolean currentPort(long epoch) { return epoch == capEpoch && !isRemoved(); }

    @Nonnull @Override
    public <T> LazyOptional<T> getCapability(@Nonnull Capability<T> requested, @Nullable Direction side) {
        if (requested == ForgeCapabilities.FLUID_HANDLER) {
            return level == null || level.isClientSide || isRemoved() || unsupportedRoot() ? LazyOptional.empty() : fluidCap.cast();
        }
        return super.getCapability(requested, side);
    }
    @Override public Component getDisplayName() { return Component.translatable("block.advancedrocketrycommunity." + role.id()); }
    @Nullable @Override public AbstractContainerMenu createMenu(int id, Inventory inventory, Player player) {
        return admitted(player) ? new RocketFluidCargoMenu(id, inventory, this) : null;
    }
    void noteRemoval() {
        if (protectedFromRemoval()) {
            LOGGER.warn("ARCE_FLUID_CARGO_PROTECTED_ROOT_REMOVED pos={} unsupported={}", worldPosition.toShortString(), unsupportedRoot());
        }
    }
}
