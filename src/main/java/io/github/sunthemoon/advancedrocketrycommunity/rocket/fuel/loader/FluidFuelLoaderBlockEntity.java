package io.github.sunthemoon.advancedrocketrycommunity.rocket.fuel.loader;

import com.mojang.logging.LogUtils;
import io.github.sunthemoon.advancedrocketrycommunity.machine.tank.TankContainerExchange;
import io.github.sunthemoon.advancedrocketrycommunity.registry.ModBlockEntities;
import io.github.sunthemoon.advancedrocketrycommunity.registry.ModBlocks;
import io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.EscrowLoaderAdmission;
import io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.EscrowReconciliationAction;
import io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.EscrowReconciliationPruning;
import java.util.ArrayList;
import java.util.Objects;
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
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;
import org.slf4j.Logger;

/**
 * Design section 3: a thin bridge from the native lifecycle to one {@link LoaderRoot}. The held root is kept exactly as
 * loaded or last written, so an unsupported root saves unchanged. One all-side Fluid port, the owner's menu and local
 * container use are the only surfaces. Fuel reaches a rocket only through escrow: a paced attempt waits in the fair
 * queue, and only reconciliation's one-use permits debit the tank or prune its marker. Production admission stays
 * disabled; the escrow adapter's gated switch opens it for release tests.
 */
public final class FluidFuelLoaderBlockEntity extends BlockEntity implements MenuProvider {
    public static final double MAX_ACTOR_DISTANCE = 8.0D;
    private static final Logger LOGGER = LogUtils.getLogger();

    /** The root exactly as loaded or last written; null for a device that never had one. */
    @Nullable private Tag held;
    /** Present exactly when {@link #held} decodes as a schema-1 root. */
    @Nullable private LoaderRoot root;
    private LoaderRoot.Status status = LoaderRoot.Status.ABSENT;
    /** An over-bound root is kept by reference rather than copied. */
    private boolean retainedByReference;
    /** Transient: not saved, created when the device loads, so a load always waits one full interval. */
    @Nullable private RefillPacer pacer;
    @Nullable private FluidFuelLoaderStatus attempted;
    private boolean operationLock;
    private long progressCheckedAt = Long.MIN_VALUE;
    @Nullable private UUID progressCheckedFor;
    @Nullable private FluidFuelLoaderStatus progress;
    private long capEpoch;
    private LazyOptional<IFluidHandler> fluidCap;

    public FluidFuelLoaderBlockEntity(BlockPos position, BlockState state) {
        super(ModBlockEntities.FLUID_FUEL_LOADER.get(), position, state);
        createCap();
    }

    /** The owner, the device identity, the binding and the escrow section never load from a non-operator's item NBT. */
    @Override
    public boolean onlyOpCanSetNbt() {
        return true;
    }

    public Optional<LoaderRoot> root() {
        return Optional.ofNullable(root);
    }

    public LoaderRoot.Status dataStatus() {
        return status;
    }

    /** A detached copy of the held root when it is within its envelope, otherwise the retained reference. */
    public Optional<Tag> heldRoot() {
        return held == null ? Optional.empty() : Optional.of(retainedByReference ? held : held.copy());
    }

    public Optional<UUID> owner() {
        return root == null ? Optional.empty() : Optional.ofNullable(root.owner());
    }

    public boolean claimed() {
        return owner().isPresent();
    }

    /** An invalid, future or unregistered-Fluid root: kept verbatim, disabled and protected. */
    public boolean unsupportedRoot() {
        return !status.supported();
    }

    /** A second loaded loader with this device's UUID in the same Level; it stays disabled while the first is loaded. */
    public boolean duplicate() {
        return root != null && !isRemoved() && level instanceof ServerLevel server
                && !FluidFuelLoaderIndex.claim(server, root.device(), this);
    }

    /**
     * Escrow holds this device: its section has a marker, or a durable, queued, captured or UNKNOWN operation, a frozen
     * proof or a settlement receipt names it. Reading never creates a ledger.
     */
    public boolean escrowLocked() {
        if (root == null) {
            return false;
        }
        if (root.escrow().locked()) {
            return true;
        }
        if (!(level instanceof ServerLevel server) || !server.getServer().isSameThread()) {
            return true;
        }
        return escrowProgress() != null;
    }

    /**
     * The device's escrow progress (PREPARING, TRANSFERRING, ESCROW_PENDING or LEDGER_UNAVAILABLE), or null when escrow
     * names nothing for it. Ports polled every tick reuse one lookup per tick; an admission refreshes it at once.
     */
    @Nullable
    public FluidFuelLoaderStatus escrowProgress() {
        if (root == null || !(level instanceof ServerLevel server) || !server.getServer().isSameThread()) {
            return null;
        }
        long now = server.getGameTime();
        if (progressCheckedAt != now || !root.device().equals(progressCheckedFor)) {
            progress = EscrowLoaderAdmission.progress(this);
            progressCheckedAt = now;
            progressCheckedFor = root.device();
        }
        return progress;
    }

    /** The fair queue admitted this device: its port and binding lock in this very tick, before any capture repeats. */
    public void escrowAdmitted() {
        if (root != null && level instanceof ServerLevel server) {
            progress = FluidFuelLoaderStatus.PREPARING;
            progressCheckedAt = server.getGameTime();
            progressCheckedFor = root.device();
            attempted = FluidFuelLoaderStatus.PREPARING;
        }
    }

    /** R-038 for the loader: an escrow-locked device or an unsupported root refuses removal, explosions and drops. */
    public boolean protectedFromRemoval() {
        return level != null && !level.isClientSide && (unsupportedRoot() || escrowLocked());
    }

    /** This exact instance is the loaded, server-thread block entity of a loader block. */
    public boolean live() {
        if (isRemoved() || !(level instanceof ServerLevel server) || !server.getServer().isSameThread()) {
            return false;
        }
        var chunk = server.getChunkSource().getChunkNow(worldPosition.getX() >> 4, worldPosition.getZ() >> 4);
        return chunk != null && chunk.getBlockEntities().get(worldPosition) == this
                && getBlockState().is(ModBlocks.FLUID_FUEL_LOADER.get());
    }

    /**
     * The status the device's own state gives, before any rocket is looked up; null when there is none. While escrow
     * holds the device this is its progress, so the owner sees PREPARING or TRANSFERRING rather than a bare lock.
     */
    @Nullable
    public FluidFuelLoaderStatus deviceRefusal() {
        if (status == LoaderRoot.Status.INVALID) {
            return FluidFuelLoaderStatus.INVALID_DATA;
        }
        if (unsupportedRoot()) {
            return FluidFuelLoaderStatus.UNSUPPORTED_DATA;
        }
        if (duplicate()) {
            return FluidFuelLoaderStatus.INVALID_DATA;
        }
        if (!claimed()) {
            return FluidFuelLoaderStatus.NOT_OWNER;
        }
        if (escrowLocked()) {
            FluidFuelLoaderStatus held = escrowProgress();
            return held == null ? FluidFuelLoaderStatus.ESCROW_PENDING : held;
        }
        return root.binding() == null ? FluidFuelLoaderStatus.UNBOUND : null;
    }

    /**
     * What the menu shows: the device's own status or escrow progress at once, else the last paced attempt or fair-queue
     * visit. A progress member whose operation has since resolved no longer describes anything, so it reads as READY
     * until the next attempt refreshes it.
     */
    public FluidFuelLoaderStatus displayStatus() {
        FluidFuelLoaderStatus refusal = deviceRefusal();
        if (refusal != null) {
            return refusal;
        }
        if (attempted == FluidFuelLoaderStatus.PREPARING || attempted == FluidFuelLoaderStatus.TRANSFERRING) {
            return FluidFuelLoaderStatus.READY;
        }
        return attempted == null ? FluidFuelLoaderStatus.ADMISSION_DISABLED : attempted;
    }

    /** The last paced attempt's result, or null before the first one. */
    @Nullable
    public FluidFuelLoaderStatus lastAttempt() {
        return attempted;
    }

    public static void serverTick(ServerLevel level, BlockPos position, BlockState state, FluidFuelLoaderBlockEntity loader) {
        if (loader.pacer == null) {
            loader.pacer = RefillPacer.loadedAt(level.getGameTime());
        }
        if (loader.pacer.tryStart(level.getGameTime())) {
            if (loader.root != null && loader.root.escrow().locked() && loader.live()) {
                // Restart and next-observation cleanup of a retired marker; live records belong to reconciliation.
                EscrowReconciliationPruning.observe(loader);
            }
            loader.attempted = loader.attempt();
        }
    }

    /**
     * One attempt, which the ticker runs at most once per refill interval. It runs every eligibility check a fill would.
     * Where a fill would start it gives ADMISSION_DISABLED, unless the escrow adapter's gated switch is open: then the
     * device waits once in the fair queue (READY), and only that queue's visit captures or reserves anything.
     */
    public FluidFuelLoaderStatus attempt() {
        FluidFuelLoaderStatus refusal = deviceRefusal();
        if (refusal != null) {
            return refusal;
        }
        if (!live()) {
            return FluidFuelLoaderStatus.WAITING_UNLOADED;
        }
        ServerLevel server = (ServerLevel) level;
        FluidFuelLoaderStatus status = LoaderEligibility.evaluate(server, worldPosition, root).refusal();
        if (status != null) {
            return status;
        }
        if (!EscrowLoaderAdmission.enabled(server.getServer(), root.device())) {
            return FluidFuelLoaderStatus.ADMISSION_DISABLED;
        }
        EscrowLoaderAdmission.offer(this, this::admitFromQueue);
        return FluidFuelLoaderStatus.READY;
    }

    /**
     * One fair-queue visit (design section 2, "One attempt"): every device and eligibility check again, then at most one
     * capture and admission. Returns true when it ran a capture, whatever the outcome; the status is kept for the menu.
     */
    public boolean admitFromQueue() {
        FluidFuelLoaderStatus refusal = deviceRefusal();
        if (refusal != null || !live()) {
            attempted = refusal != null ? refusal : FluidFuelLoaderStatus.WAITING_UNLOADED;
            return false;
        }
        LoaderEligibility.Resolved eligible = LoaderEligibility.evaluate((ServerLevel) level, worldPosition, root);
        if (eligible.refusal() != null) {
            attempted = eligible.refusal();
            return false;
        }
        EscrowLoaderAdmission.Visit visit = EscrowLoaderAdmission.admit(this, eligible.rocket());
        attempted = visit.status();
        return visit.captured();
    }

    /** The ordinary reconciliation permit debits the tank and records its SOURCE marker as one write. */
    public void applyEscrowDebit(EscrowReconciliationAction.Permit permit) {
        LoaderRoot after = LoaderRoot.decode(Objects.requireNonNull(permit, "permit").consume(this));
        write(after);
    }

    /** C8 and O4: the one-use permit removes only retired markers; the tank and epoch stay. */
    public void pruneReconciledEscrow(EscrowReconciliationPruning.SourcePermit permit) {
        LoaderEscrowSection section = Objects.requireNonNull(permit, "permit").consume(this);
        write(new LoaderRoot(root.owner(), root.device(), root.binding(), root.tank(), section));
    }

    /** The owner, never a spectator, a fake player or another player (operators included), within 8 blocks. */
    public boolean admitted(Player player) {
        return actorNear(player) && supportedClaimed() && !duplicate() && root.owner().equals(player.getUUID());
    }

    /** A connected, living, non-fake player in this Level within 8 blocks who may interact here. */
    boolean actorNear(Player player) {
        return live() && player instanceof ServerPlayer server && !(player instanceof FakePlayer)
                && !server.hasDisconnected() && player.isAlive() && !player.isSpectator() && player.level() == level
                && player.distanceToSqr(worldPosition.getX() + 0.5D, worldPosition.getY() + 0.5D,
                        worldPosition.getZ() + 0.5D) <= MAX_ACTOR_DISTANCE * MAX_ACTOR_DISTANCE
                && level.mayInteract(player, worldPosition);
    }

    private boolean supportedClaimed() {
        return !unsupportedRoot() && root != null && root.owner() != null;
    }

    /** Fill, drain and local container use: a claimed, supported, unlocked, unique device, live, with no call in progress. */
    private boolean usable() {
        return !operationLock && live() && supportedClaimed() && !duplicate() && !escrowLocked();
    }

    // Placement, drops and binding ------------------------------------------------------------------------------

    /** Validated placement: a fresh device UUID, no binding, a pristine escrow section and the carried tank. */
    void placed(@Nullable UUID owner, LoaderTank tank) {
        if (!live()) {
            return;
        }
        if (level instanceof ServerLevel server) {
            FluidFuelLoaderIndex.release(server, this);
        }
        write(LoaderRoot.placed(owner, UUID.randomUUID(), tank));
        claimDevice();
        LOGGER.debug("Fluid fuel loader placed at {} claimed={}", worldPosition.toShortString(), owner != null);
    }

    /** The carrier this device drops: its owner and tank, or an empty unowned carrier for a device with no root. */
    LoaderCarrier carrier() {
        if (unsupportedRoot()) {
            throw new IllegalStateException("An unsupported loader root drops nothing");
        }
        return root == null ? new LoaderCarrier(null, LoaderTank.EMPTY) : LoaderCarrier.drop(root);
    }

    /** Writes a binding the linker has fully validated; the device must be idle and unchanged. */
    boolean bind(LoaderBinding binding) {
        Objects.requireNonNull(binding, "binding");
        if (operationLock || !live() || !supportedClaimed() || duplicate() || escrowLocked()) {
            return false;
        }
        write(new LoaderRoot(root.owner(), root.device(), binding, root.tank(), root.escrow()));
        attempted = null;
        return true;
    }

    /** Clears an idle binding; a protected or locked device refuses. */
    boolean clearBinding() {
        if (operationLock || !live() || !supportedClaimed() || duplicate() || escrowLocked() || root.binding() == null) {
            return false;
        }
        write(new LoaderRoot(root.owner(), root.device(), null, root.tank(), root.escrow()));
        attempted = null;
        return true;
    }

    private void write(LoaderRoot next) {
        CompoundTag encoded = next.encode();
        held = encoded;
        root = next;
        status = LoaderRoot.Status.VALID;
        retainedByReference = false;
        progressCheckedAt = Long.MIN_VALUE;
        setChanged();
    }

    // The Fluid port --------------------------------------------------------------------------------------------

    /** The tank's contents for display and the port; empty when the device is not usable. */
    public FluidStack fluid() {
        return root == null || unsupportedRoot() ? FluidStack.EMPTY : root.tank().fluidStack();
    }

    /** The tank, or empty for an unsupported device. */
    public LoaderTank tank() {
        return root == null || unsupportedRoot() ? LoaderTank.EMPTY : root.tank();
    }

    /**
     * One mB of the offered Fluid when it may enter {@code tank} at all, whatever the amount and the room: a registered
     * Fluid within the tank envelope, the same Fluid and native tag as any contents, and (Root's W82 decision) a
     * native envelope that an escrow operation could carry. The envelope's size does not depend on the amount, so an
     * offer larger than the tank is judged by its identity alone. Null when intake refuses it.
     */
    @Nullable
    private static LoaderTank intake(LoaderTank tank, FluidStack offered) {
        if (offered == null || offered.isEmpty()) {
            return null;
        }
        LoaderTank unit;
        try {
            FluidStack one = offered.copy();
            one.setAmount(1);
            unit = LoaderTank.of(one);
        } catch (IllegalArgumentException refused) {
            return null;
        }
        if (!unit.escrowCarriable()
                || !tank.isEmpty() && (!tank.fluid().equals(unit.fluid()) || !Objects.equals(tank.tag(), unit.tag()))) {
            return null;
        }
        return unit;
    }

    /** The tank after accepting as much of {@code offered} as fits, or null when nothing is accepted. */
    @Nullable
    private static LoaderTank accepting(LoaderTank tank, FluidStack offered) {
        LoaderTank unit = intake(tank, offered);
        int accepted = unit == null ? 0 : Math.min(offered.getAmount(), tank.room());
        return accepted <= 0 ? null : unit.withAmount(tank.amount() + accepted);
    }

    int fill(FluidStack offered, IFluidHandler.FluidAction action) {
        Objects.requireNonNull(action, "action");
        if (offered == null || !usable()) {
            return 0;
        }
        operationLock = true;
        try {
            LoaderTank before = root.tank();
            LoaderTank after = accepting(before, offered);
            if (after == null) {
                return 0;
            }
            if (action.execute()) {
                write(new LoaderRoot(root.owner(), root.device(), root.binding(), after, root.escrow()));
            }
            return after.amount() - before.amount();
        } finally {
            operationLock = false;
        }
    }

    FluidStack drain(int maximum, @Nullable FluidStack matching, IFluidHandler.FluidAction action) {
        Objects.requireNonNull(action, "action");
        if (!usable() || root.tank().isEmpty()) {
            return FluidStack.EMPTY;
        }
        operationLock = true;
        try {
            LoaderTank tank = root.tank();
            FluidStack contents = tank.fluidStack();
            if (matching != null) {
                if (matching.isEmpty() || !contents.isFluidEqual(matching)) {
                    return FluidStack.EMPTY;
                }
                maximum = matching.getAmount();
            }
            int amount = Math.min(maximum, tank.amount());
            if (amount <= 0) {
                return FluidStack.EMPTY;
            }
            FluidStack result = tank.withAmount(amount).fluidStack();
            if (action.execute()) {
                write(new LoaderRoot(root.owner(), root.device(), root.binding(), tank.withAmount(tank.amount() - amount),
                        root.escrow()));
            }
            return result;
        } finally {
            operationLock = false;
        }
    }

    boolean validFluid(FluidStack offered) {
        if (offered == null || !usable()) {
            return false;
        }
        operationLock = true;
        try {
            return intake(root.tank(), offered) != null;
        } finally {
            operationLock = false;
        }
    }

    /** Local container use by the owner: one whole bucket or canister, prepared on detached copies before any write. */
    public boolean exchange(Player player, InteractionHand hand) {
        if (!usable() || !admitted(player)) {
            return false;
        }
        operationLock = true;
        try {
            var inventory = player.getInventory();
            ItemStack heldStack = player.getItemInHand(hand);
            ItemStack heldBefore = heldStack.copy();
            var storage = new ArrayList<ItemStack>();
            var slots = new ArrayList<Integer>();
            for (int slot = 0; slot < inventory.items.size(); slot++) {
                if (hand != InteractionHand.MAIN_HAND || slot != inventory.selected) {
                    storage.add(inventory.getItem(slot));
                    slots.add(slot);
                }
            }
            LoaderTank before = root.tank();
            var plan = TankContainerExchange.plan(heldBefore, storage, before.fluidStack(), LoaderTank.CAPACITY);
            if (plan.isEmpty()) {
                return false;
            }
            LoaderTank after;
            try {
                after = LoaderTank.of(plan.get().fluid());
            } catch (IllegalArgumentException refused) {
                return false;
            }
            if (after.amount() > before.amount() && !after.escrowCarriable()) {
                return false;
            }
            var prepared = plan.get().slots();
            int destination = prepared.destinationSlot() < 0 ? -1 : slots.get(prepared.destinationSlot());
            if (!ItemStack.matches(player.getItemInHand(hand), heldBefore)) {
                return false;
            }
            write(new LoaderRoot(root.owner(), root.device(), root.binding(), after, root.escrow()));
            player.setItemInHand(hand, prepared.held());
            if (destination >= 0) {
                inventory.setItem(destination, prepared.destination());
            }
            inventory.setChanged();
            return true;
        } finally {
            operationLock = false;
        }
    }

    // Lifecycle -------------------------------------------------------------------------------------------------

    @Override
    protected void saveAdditional(CompoundTag outer) {
        super.saveAdditional(outer);
        if (held != null) {
            outer.put(LoaderRoot.KEY, retainedByReference ? held : held.copy());
        }
    }

    @Override
    public void load(CompoundTag outer) {
        if (operationLock) {
            throw new IllegalStateException("Reentrant fluid fuel loader load");
        }
        super.load(outer);
        if (level != null && !isRemoved() && protectedFromRemoval()) {
            // A live /data or structure replacement keeps a protected root unchanged.
            LOGGER.warn("ARCE_FLUID_FUEL_LOADER_LIVE_REPLACEMENT_REFUSED pos={} unsupported={}",
                    worldPosition.toShortString(), unsupportedRoot());
            return;
        }
        if (level instanceof ServerLevel server) {
            FluidFuelLoaderIndex.release(server, this);
        }
        invalidatePort();
        held = null;
        root = null;
        attempted = null;
        progressCheckedAt = Long.MIN_VALUE;
        retainedByReference = false;
        LoaderRoot.Read read = LoaderRoot.read(outer);
        status = read.status();
        if (status != LoaderRoot.Status.ABSENT) {
            Tag raw = outer.get(LoaderRoot.KEY);
            retainedByReference = !LoaderRoot.bounded(raw);
            held = retainedByReference ? raw : raw.copy();
            root = read.root();
        }
        if (unsupportedRoot()) {
            LOGGER.warn("ARCE_FLUID_FUEL_LOADER_UNSUPPORTED pos={} status={}", worldPosition.toShortString(), status);
        }
        createCap();
        claimDevice();
    }

    @Override
    public void onLoad() {
        super.onLoad();
        if (level instanceof ServerLevel server) {
            pacer = RefillPacer.loadedAt(server.getGameTime());
            attempted = null;
            claimDevice();
        }
    }

    /** The first loaded holder of a device UUID keeps it; the check in {@link #duplicate} also takes over a freed one. */
    private void claimDevice() {
        if (root != null && !isRemoved() && level instanceof ServerLevel server) {
            FluidFuelLoaderIndex.claim(server, root.device(), this);
        }
    }

    @Override
    public void setRemoved() {
        if (level instanceof ServerLevel server) {
            FluidFuelLoaderIndex.release(server, this);
        }
        super.setRemoved();
    }

    @Override
    public void onChunkUnloaded() {
        if (level instanceof ServerLevel server) {
            FluidFuelLoaderIndex.release(server, this);
        }
        super.onChunkUnloaded();
    }

    @Override
    public void invalidateCaps() {
        super.invalidateCaps();
        invalidatePort();
    }

    @Override
    public void reviveCaps() {
        super.reviveCaps();
        createCap();
    }

    private void invalidatePort() {
        capEpoch++;
        if (fluidCap != null) {
            fluidCap.invalidate();
        }
    }

    private void createCap() {
        long epoch = capEpoch;
        fluidCap = LazyOptional.of(() -> new LoaderFluidPort(this, epoch));
    }

    boolean currentPort(long epoch) {
        return epoch == capEpoch && !isRemoved();
    }

    /** One local port, the same for every side and for the null side; none on the client or for an unsupported root. */
    @Nonnull
    @Override
    public <T> LazyOptional<T> getCapability(@Nonnull Capability<T> requested, @Nullable Direction side) {
        if (requested == ForgeCapabilities.FLUID_HANDLER) {
            return level == null || level.isClientSide || isRemoved() || unsupportedRoot()
                    ? LazyOptional.empty() : fluidCap.cast();
        }
        return super.getCapability(requested, side);
    }

    // Menu ------------------------------------------------------------------------------------------------------

    @Override
    public Component getDisplayName() {
        return Component.translatable("block.advancedrocketrycommunity.fluid_fuel_loader");
    }

    @Nullable
    @Override
    public AbstractContainerMenu createMenu(int id, Inventory inventory, Player player) {
        return admitted(player) ? new FluidFuelLoaderMenu(id, inventory, this) : null;
    }

    /** Detection only: a protected root removed by a raw replacement such as {@code /setblock}. */
    void noteRemoval() {
        if (protectedFromRemoval()) {
            LOGGER.warn("ARCE_FLUID_FUEL_LOADER_PROTECTED_ROOT_REMOVED pos={} unsupported={}",
                    worldPosition.toShortString(), unsupportedRoot());
        }
    }
}
