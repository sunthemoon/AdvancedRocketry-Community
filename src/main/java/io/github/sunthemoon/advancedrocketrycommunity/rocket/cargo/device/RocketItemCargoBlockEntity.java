package io.github.sunthemoon.advancedrocketrycommunity.rocket.cargo.device;

import com.mojang.logging.LogUtils;
import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import io.github.sunthemoon.advancedrocketrycommunity.persistence.GuardedChunkSaves;
import io.github.sunthemoon.advancedrocketrycommunity.registry.ModBlockEntities;
import io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.EscrowEndpoint;
import io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.EscrowItemBuffer;
import io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.RocketCargoDeviceHold;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.bay.SatelliteBayRoot;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.fuel.loader.LoaderBinding;
import java.util.ArrayList;
import java.util.List;
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

/** Native lifecycle and authority bridge. D-A never starts a cargo operation or creates an escrow ledger. */
public final class RocketItemCargoBlockEntity extends BlockEntity implements MenuProvider {
    public static final double MAX_ACTOR_DISTANCE = 8.0D;
    public static final String SAVE_REFUSAL =
            "Refusing chunk save with an item cargo root that native serialization cannot keep; back up and repair offline";
    private static final Logger LOGGER = LogUtils.getLogger();
    private final RocketCargoRole role;
    @Nullable private Tag held;
    @Nullable private RocketItemCargoRoot root;
    private RocketItemCargoRoot.Status status = RocketItemCargoRoot.Status.ABSENT;
    private boolean retainedByReference;
    private boolean unkeepable;
    private boolean operationLock;
    /** Set once the buffer has spilled on removal, so it can never spill or be used again. */
    private boolean spilled;
    private long capEpoch;
    private LazyOptional<IItemHandler> itemCap;

    public RocketItemCargoBlockEntity(BlockPos position, BlockState state) {
        super(((RocketItemCargoBlock) state.getBlock()).role() == RocketCargoRole.LOAD
                ? ModBlockEntities.ROCKET_ITEM_LOADER.get() : ModBlockEntities.ROCKET_ITEM_UNLOADER.get(), position, state);
        role = ((RocketItemCargoBlock) state.getBlock()).role();
        createCap();
    }

    @Override public boolean onlyOpCanSetNbt() { return true; }
    public RocketCargoRole role() { return role; }
    public Optional<RocketItemCargoRoot> root() { return Optional.ofNullable(root); }
    public RocketItemCargoRoot.Status dataStatus() { return status; }
    public Optional<Tag> heldRoot() { return held == null ? Optional.empty() : Optional.of(retainedByReference ? held : held.copy()); }
    public Optional<UUID> owner() { return root == null ? Optional.empty() : Optional.ofNullable(root.owner()); }
    public boolean claimed() { return owner().isPresent(); }
    public boolean unsupportedRoot() { return !status.supported(); }
    public boolean unkeepable() { return unkeepable; }
    public EscrowItemBuffer buffer() { return root == null || unsupportedRoot() || spilled ? RocketItemCargoRoot.EMPTY : root.buffer(); }
    /** A copy of one slot's stack, for display and the menu; never a live reference into the root. */
    public ItemStack stack(int slot) { return RocketItemCargoLocal.stack(buffer().slots().get(slot)); }

    public boolean duplicate() {
        return root != null && live() && !RocketFluidCargoIndex.claim((ServerLevel) level, root.device(), this);
    }

    public boolean escrowLocked() {
        if (root == null) { return false; }
        return root.marked() || !(level instanceof ServerLevel server) || !server.getServer().isSameThread()
                || RocketCargoDeviceHold.locked(server, root.device(), root.escrow().ledger(),
                        ModIdentity.id(RocketItemCargoBlock.id(role)).toString(), worldPosition, EscrowEndpoint.Unit.CONTAINER);
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
                && chunk.getBlockState(worldPosition).getBlock() instanceof RocketItemCargoBlock block && block.role() == role;
    }

    @Nullable public RocketCargoStatus deviceRefusal() {
        if (status == RocketItemCargoRoot.Status.INVALID) { return RocketCargoStatus.INVALID_DATA; }
        if (unsupportedRoot()) { return RocketCargoStatus.UNSUPPORTED_DATA; }
        if (!live()) { return RocketCargoStatus.WAITING_UNLOADED; }
        if (duplicate()) { return RocketCargoStatus.INVALID_DATA; }
        if (!claimed()) { return RocketCargoStatus.NOT_OWNER; }
        if (escrowLocked()) { return RocketCargoStatus.ESCROW_PENDING; }
        return root.binding() == null ? RocketCargoStatus.UNBOUND : null;
    }

    /** Admission is deliberately unwired. No snapshot preparation, queue, sequence or resource mutation occurs. */
    public RocketCargoStatus displayStatus() {
        RocketCargoStatus refusal = deviceRefusal();
        if (refusal != null) { return refusal; }
        return RocketCargoBindingChecks.check((ServerLevel) level, worldPosition, root.owner(), root.binding());
    }

    /** There is no prepared cargo operation in D-A; the display cursor is absent, not a client-selected target. */
    public Optional<BlockPos> cursor() { return Optional.empty(); }
    /** The container slot of the absent cursor; -1 while no operation is prepared. */
    public int cursorSlot() { return -1; }

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

    /** Shared non-reentrant lock for the port and the owner's menu. */
    boolean beginLocal() {
        if (operationLock || spilled || !live() || unsupportedRoot() || !claimed() || duplicate() || escrowLocked()) { return false; }
        operationLock = true;
        return true;
    }
    void endLocal() { operationLock = false; }
    boolean localInProgress() { return operationLock; }
    /** Whether the root with one slot replaced still fits its envelope; the lock must be held. */
    boolean slotFits(int slot, CompoundTag stack) {
        if (!operationLock) { throw new IllegalStateException("Local cargo storage requires its lock"); }
        try { return RocketItemCargoRoot.bounded(root.withSlot(slot, stack).encodeUnchecked()); }
        catch (IllegalArgumentException refused) { return false; }
    }
    void writeSlot(int slot, CompoundTag stack) {
        if (!operationLock) { throw new IllegalStateException("Local cargo storage requires its lock"); }
        write(root.withSlot(slot, stack));
    }

    void placed(@Nullable UUID owner) {
        if (!live() || protectedFromRemoval()) { return; }
        RocketFluidCargoIndex.release((ServerLevel) level, this);
        write(RocketItemCargoRoot.placed(owner, UUID.randomUUID(), role));
        claimDevice();
    }

    /** The buffer as ordinary item stacks, exactly once; nothing for a protected device or a second call. */
    List<ItemStack> spill() {
        if (spilled || protectedFromRemoval()) { return List.of(); }
        List<ItemStack> stacks = new ArrayList<>();
        for (int slot = 0; slot < EscrowItemBuffer.SLOTS; slot++) {
            ItemStack stack = stack(slot);
            if (!stack.isEmpty()) { stacks.add(stack); }
        }
        spilled = true;
        invalidatePort();
        return stacks;
    }
    boolean bind(LoaderBinding binding) {
        if (!beginLocal()) { return false; }
        try { write(root.withBinding(binding)); return true; }
        finally { endLocal(); }
    }
    boolean clearBinding() {
        return root != null && root.binding() != null && bind(null);
    }
    private void write(RocketItemCargoRoot next) {
        CompoundTag encoded = next.encode();
        held = encoded; root = next; status = RocketItemCargoRoot.Status.VALID;
        retainedByReference = false; unkeepable = false;
        setChanged();
    }

    @Override protected void saveAdditional(CompoundTag outer) {
        super.saveAdditional(outer);
        if (held != null) { outer.put(RocketItemCargoRoot.KEY, retainedByReference ? held : held.copy()); }
    }

    @Override public void load(CompoundTag outer) {
        // Refuse reentrant load without changing either native resources or superclass state.
        if (operationLock) { return; }
        if (level != null && !isRemoved() && protectedFromRemoval()) {
            LOGGER.warn("ARCE_ITEM_CARGO_LIVE_REPLACEMENT_REFUSED pos={}", worldPosition.toShortString());
            return;
        }
        super.load(outer);
        if (level instanceof ServerLevel server) { RocketFluidCargoIndex.release(server, this); }
        invalidatePort();
        held = null; root = null; retainedByReference = false; unkeepable = false;
        var read = RocketItemCargoRoot.read(outer, role);
        status = read.status();
        if (status != RocketItemCargoRoot.Status.ABSENT) {
            Tag raw = outer.get(RocketItemCargoRoot.KEY);
            // Same native depth/envelope position as the bay. WORK_LIMIT is keepable, never a save veto.
            unkeepable = !SatelliteBayRoot.nativelyKeepable(raw);
            retainedByReference = !RocketItemCargoRoot.bounded(raw);
            held = retainedByReference ? raw : raw.copy();
            root = read.root();
        }
        if (unsupportedRoot()) {
            LOGGER.warn("ARCE_ITEM_CARGO_UNSUPPORTED pos={} status={} keepable={}", worldPosition.toShortString(), status, !unkeepable);
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
    @Override public void onLoad() { super.onLoad(); recordDenial(); claimDevice(); }
    @Override public void setRemoved() {
        if (level instanceof ServerLevel server) { RocketFluidCargoIndex.release(server, this); }
        super.setRemoved();
    }
    @Override public void onChunkUnloaded() {
        if (level instanceof ServerLevel server) { RocketFluidCargoIndex.release(server, this); }
        invalidatePort();
        super.onChunkUnloaded();
    }
    @Override public void invalidateCaps() { super.invalidateCaps(); invalidatePort(); }
    @Override public void reviveCaps() { super.reviveCaps(); createCap(); }
    private void invalidatePort() { capEpoch++; if (itemCap != null) { itemCap.invalidate(); } }
    private void createCap() {
        long epoch = capEpoch;
        itemCap = LazyOptional.of(() -> new RocketItemCargoPort(this, epoch));
    }
    boolean currentPort(long epoch) { return epoch == capEpoch && !isRemoved() && !spilled; }

    @Nonnull @Override
    public <T> LazyOptional<T> getCapability(@Nonnull Capability<T> requested, @Nullable Direction side) {
        if (requested == ForgeCapabilities.ITEM_HANDLER) {
            return level == null || level.isClientSide || isRemoved() || spilled || unsupportedRoot() ? LazyOptional.empty() : itemCap.cast();
        }
        return super.getCapability(requested, side);
    }
    @Override public Component getDisplayName() { return Component.translatable("block.advancedrocketrycommunity." + RocketItemCargoBlock.id(role)); }
    @Nullable @Override public AbstractContainerMenu createMenu(int id, Inventory inventory, Player player) {
        return admitted(player) ? new RocketItemCargoMenu(id, inventory, this) : null;
    }
    void noteRemoval() {
        if (protectedFromRemoval()) {
            LOGGER.warn("ARCE_ITEM_CARGO_PROTECTED_ROOT_REMOVED pos={} unsupported={}", worldPosition.toShortString(), unsupportedRoot());
        }
    }
}
