package io.github.sunthemoon.advancedrocketrycommunity.endgame.cargo;

import io.github.sunthemoon.advancedrocketrycommunity.endgame.device.EndgameDeviceBlockEntity;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.device.EndgameDeviceTags;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.device.EndgameDevices;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.device.EndgameRedstoneMode;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.device.RoundRobinBudget;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.model.EndgameCode;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.model.EndgameNbt;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.service.EndgameRuntime;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.service.EndgameService;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.transit.TransitDestinationState;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.transit.TransitEndpoint;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.transit.TransitSourceState;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.transit.TransitTags;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.energy.IEnergyStorage;
import net.minecraftforge.items.IItemHandler;

/**
 * A cargo endpoint of the ADR-054 section 11 transit ledger (the railgun, the elevator anchor and terminal). Its root
 * holds the buffers ({@link CargoStorage}), the ledger's source and destination state under {@code transit}, {@code
 * auto}, the redstone mode, the freeze of a retired ID and the device's own settings. Every 20 ticks it checks its
 * registration (section 9); while loaded it is attached to the ledger, unless it is a copy whose registered ID stands
 * at another position. A launch intent, or {@code auto} with the redstone mode satisfied, launches within the
 * device's 20-tick cadence and a grant of its system's per-tick cap; refused automatic attempts wait one cadence. It
 * never loads a chunk.
 */
public abstract class CargoEndpointBlockEntity extends EndgameDeviceBlockEntity implements TransitEndpoint {
    public static final int CADENCE_TICKS = 20;
    private static final int STATUS_CHECK_TICKS = 20;
    private static final Set<String> SHARED_KEYS = Set.of(EndgameDeviceTags.FROZEN, "energy", "input", "receive",
            TransitTags.SECTION, "auto", "redstone");
    private static final Set<String> SECTION_KEYS = Set.of("next_seq", "outbox", "incoming", "receipts",
            "conflicts");

    private final CargoStorage storage;
    private final TransitSourceState source = new TransitSourceState();
    private final TransitDestinationState destination = new TransitDestinationState();
    private final Set<String> stateKeys;
    private LazyOptional<IItemHandler> itemCapability = LazyOptional.empty();
    private LazyOptional<IEnergyStorage> energyCapability = LazyOptional.empty();
    private boolean frozen;
    private boolean auto;
    private EndgameRedstoneMode redstone = EndgameRedstoneMode.IGNORED;

    private EndgameCode endpointStatus = EndgameCode.AWAITING_WORLD_SAVE;
    private EndgameCode lastCode = EndgameCode.OK;
    private long nextStatusCheck;
    private long lastLaunch = Long.MIN_VALUE / 2;
    @Nullable
    private UUID pendingActor;
    private boolean launchPending;
    private boolean attached;
    private int receiptsSeen;

    protected CargoEndpointBlockEntity(BlockEntityType<?> type, BlockPos position, BlockState state,
                                       int energyCapacity, int maxInputPerTick, Set<String> settingKeys) {
        super(type, position, state);
        storage = new CargoStorage(this::setChanged, () -> level, energyCapacity, maxInputPerTick);
        Set<String> keys = new HashSet<>(SHARED_KEYS);
        keys.addAll(settingKeys);
        stateKeys = Set.copyOf(keys);
    }

    /** The endpoint kind in the index (ADR-054 section 9). */
    public abstract ResourceLocation kind();

    protected abstract void resetSettings();

    protected abstract void readSettings(CompoundTag root);

    protected abstract void writeSettings(CompoundTag root);

    /** Runs each tick after the endpoint checks (a structure, a region); false while the device cannot launch. */
    protected abstract boolean ready(ServerLevel level, BlockState state, UUID id, EndgameDevices devices, long now);

    /** Whether an automatic launch has a payload and a destination, so it may ask for a slot of the cap. */
    protected abstract boolean autoReady();

    /** The system's per-tick launch cap. */
    protected abstract RoundRobinBudget launchBudget(EndgameDevices devices);

    /** A launch: the first refusal, or {@code OK} after the escrow. A dry check ({@code escrow} false) changes nothing. */
    public abstract EndgameCode launch(ServerLevel level, EndgameService service, EndgameDevices devices,
                                       @Nullable UUID actor, boolean escrow);

    /** One audit line under the device's system. */
    public void audit(EndgameService service, long now, String action, EndgameCode code, @Nullable UUID actor,
                      String fields) {
        service.audit().line(now, system().id(), action, code.name(), deviceId().orElse(null),
                ownerId().orElse(null), actor, fields);
    }

    /** A claim added a receipt while loaded (an arrival). */
    protected void claimed() {
    }

    /** An escrow happened in this tick. */
    protected void launchedEffect() {
    }

    /** The device's own runtime to release when it unloads or is removed. */
    protected void unloadDevice() {
    }

    @Override
    protected final Set<String> stateKeys() {
        return stateKeys;
    }

    @Override
    protected final void resetState() {
        storage.reset();
        source.clear();
        destination.clear();
        frozen = false;
        auto = false;
        redstone = EndgameRedstoneMode.IGNORED;
        resetSettings();
    }

    @Override
    protected final void readState(CompoundTag root) {
        frozen = EndgameNbt.requireBoolean(root, EndgameDeviceTags.FROZEN);
        storage.read(root);
        CompoundTag section = EndgameNbt.requireCompound(root, TransitTags.SECTION);
        EndgameNbt.requireKeys(section, SECTION_KEYS, "Transit section");
        source.read(section);
        destination.read(section);
        auto = EndgameNbt.requireBoolean(root, "auto");
        redstone = EndgameRedstoneMode.byName(EndgameNbt.requireString(root, "redstone", 16));
        readSettings(root);
        receiptsSeen = destination.receipts().size();
    }

    @Override
    protected final void writeState(CompoundTag root) {
        root.putBoolean(EndgameDeviceTags.FROZEN, frozen);
        storage.write(root);
        CompoundTag section = new CompoundTag();
        source.write(section);
        destination.write(section);
        root.put(TransitTags.SECTION, section);
        root.putBoolean("auto", auto);
        root.putString("redstone", redstone.name());
        writeSettings(root);
    }

    protected final void tickServer(ServerLevel level, BlockState state) {
        if (quarantined()) {
            return;
        }
        Optional<EndgameService> service = EndgameRuntime.operational();
        Optional<EndgameDevices> devices = EndgameRuntime.devices();
        if (service.isEmpty() || devices.isEmpty() || deviceId().isEmpty()) {
            endpointStatus = EndgameCode.ROOT_UNAVAILABLE;
            attached = false;
            return;
        }
        long now = level.getGameTime();
        UUID id = deviceId().get();
        if (now >= nextStatusCheck) {
            nextStatusCheck = now + STATUS_CHECK_TICKS;
            checkEndpoint(level, service.get(), id);
        }
        // A copy carrying a registered ID at another position (operator copy tools) is inert: it never takes the
        // ledger's place of the registered endpoint (ADR-054 section 9).
        boolean conflict = endpointStatus == EndgameCode.ENDPOINT_POSITION_CONFLICT;
        if (!attached && !conflict) {
            service.get().transits().attach(this);
            attached = true;
        } else if (attached && conflict) {
            service.get().transits().detach(id, this);
            attached = false;
        }
        if (conflict || !ready(level, state, id, devices.get(), now) || frozen) {
            return;
        }
        boolean wanted = launchPending || auto && redstone.satisfied(level.hasNeighborSignal(worldPosition))
                && endpointActive() && autoReady();
        if (!wanted || now - lastLaunch < CADENCE_TICKS) {
            return;
        }
        RoundRobinBudget budget = launchBudget(devices.get());
        if (!budget.take(id)) {
            budget.request(id);
            return;
        }
        EndgameCode code = launch(level, service.get(), devices.get(), pendingActor, true);
        if (code != EndgameCode.OK && (launchPending || code != lastCode)) {
            audit(service.get(), now, launchPending ? "launch" : "auto_launch", code, pendingActor, "");
        }
        launchPending = false;
        pendingActor = null;
        lastCode = code;
        if (code != EndgameCode.OK) {
            lastLaunch = now; // A refused automatic launch tries again after the cadence.
        }
    }

    /** The endpoint status every 20 ticks (ADR-054 section 9). */
    private void checkEndpoint(ServerLevel level, EndgameService service, UUID id) {
        if (ownerId().isEmpty()) {
            endpointStatus = EndgameCode.UNOWNED;
            return;
        }
        if (frozen) {
            endpointStatus = EndgameCode.ENDPOINT_RETIRED;
            return;
        }
        endpointStatus = service.endpointStatus(id, level.dimension().location(), worldPosition.asLong());
        if (endpointStatus == EndgameCode.ENDPOINT_RETIRED) {
            // A retired ID is frozen for good: its transit contents wait for a resolve or a break (review R4-L3).
            frozen = true;
            setChanged();
            service.forgetCandidate(id);
        } else if (endpointStatus != EndgameCode.OK && endpointStatus != EndgameCode.ENDPOINT_POSITION_CONFLICT) {
            service.awaitRegistration(id, kind(), ownerId().get(), level.dimension().location(),
                    worldPosition.asLong());
            setChanged();
        }
    }

    // ---- TransitEndpoint ------------------------------------------------------------------------------------

    @Override
    public UUID endpointId() {
        return deviceId().orElseThrow();
    }

    @Override
    public Optional<UUID> endpointOwner() {
        return ownerId();
    }

    @Override
    public boolean transitFrozen() {
        return frozen || quarantined();
    }

    @Override
    public TransitSourceState source() {
        return source;
    }

    @Override
    public TransitDestinationState destination() {
        return destination;
    }

    @Override
    public TransitDestinationState.ReceiveBuffer receiveBuffer() {
        return storage.receiveBuffer();
    }

    /** Marks the chunk changed; a new receipt (a claim while loaded) is an arrival. */
    @Override
    public void transitChanged() {
        setChanged();
        int receipts = destination.receipts().size();
        if (receipts > receiptsSeen && level != null && !level.isClientSide) {
            claimed();
        }
        receiptsSeen = receipts;
    }

    @Override
    public boolean returnToInput(List<ItemStack> stacks) {
        return storage.returnToInput(stacks);
    }

    // ---- State for launches, menus and GameTests --------------------------------------------------------------

    /** Registered here, owned, not frozen or quarantined: it can launch and be selected. */
    public boolean endpointActive() {
        return endpointStatus == EndgameCode.OK && usable() && !frozen;
    }

    public EndgameCode endpointStatus() {
        return endpointStatus;
    }

    /** Outbox entries, incoming payloads, receipts or frozen conflicts: a non-operator break is refused. */
    public boolean busy() {
        return !frozen && (source.holdsContents() || destination.holdsContents());
    }

    public boolean frozen() {
        return frozen;
    }

    public boolean auto() {
        return auto;
    }

    /** After the intent guard (ADR-054 section 4). */
    public void auto(boolean value) {
        auto = value;
        setChanged();
    }

    public EndgameRedstoneMode redstoneMode() {
        return redstone;
    }

    /** After the intent guard (ADR-054 section 4). */
    public void redstoneMode(EndgameRedstoneMode mode) {
        redstone = mode;
        setChanged();
    }

    /** A launch intent waits for the cadence and the system's per-tick cap; the next grant runs it. */
    public void requestLaunch(UUID actor) {
        launchPending = true;
        pendingActor = actor;
    }

    /** GameTests: the launch intent, after the intent guard a player would pass. */
    public void launchForTest(UUID actor) {
        requestLaunch(actor);
    }

    /** An escrow left this endpoint now: the cadence starts. */
    protected void launched(long now) {
        lastLaunch = now;
        launchedEffect();
    }

    public long lastLaunch() {
        return lastLaunch;
    }

    public EndgameCode lastCode() {
        return lastCode;
    }

    public CargoStorage storage() {
        return storage;
    }

    /** Settings that belong to the old owner are dropped and the endpoint waits again under its new owner. */
    @Override
    protected void ownerChanged(UUID previous) {
        auto = false;
        setChanged();
        if (level instanceof ServerLevel server && !frozen && deviceId().isPresent() && ownerId().isPresent()) {
            EndgameRuntime.operational().ifPresent(service -> service.awaitRegistration(deviceId().get(), kind(),
                    ownerId().get(), server.dimension().location(), worldPosition.asLong()));
        }
    }

    /** The shared part of {@code device inspect}. */
    protected String describeCargo() {
        return "endpoint=" + endpointStatus.name() + " frozen=" + frozen + " energy=" + storage.energy().energy()
                + " auto=" + auto + " redstone=" + redstone.name() + " next_seq=" + source.nextSeq() + " outbox="
                + source.outbox().size() + " incoming=" + destination.incoming().size() + " receipts="
                + destination.receipts().size() + " last=" + lastCode.name();
    }

    // ---- Lifecycle ------------------------------------------------------------------------------------------

    private void unloadRuntime() {
        if (attached) {
            attached = false;
            deviceId().ifPresent(id -> EndgameRuntime.service().ifPresent(service -> {
                service.transits().detach(id, this);
                service.forgetCandidate(id);
            }));
        }
        unloadDevice();
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
        if (level != null && !level.isClientSide) {
            unloadRuntime();
        }
    }

    @Override
    public void onChunkUnloaded() {
        super.onChunkUnloaded();
        if (level != null && !level.isClientSide) {
            unloadRuntime();
        }
    }

    @Override
    public void onLoad() {
        super.onLoad();
        itemCapability = LazyOptional.of(storage::automation);
        energyCapability = LazyOptional.of(storage::energy);
        nextStatusCheck = 0;
    }

    @Override
    public void invalidateCaps() {
        super.invalidateCaps();
        itemCapability.invalidate();
        energyCapability.invalidate();
    }

    @Override
    public <T> LazyOptional<T> getCapability(@Nonnull Capability<T> capability, @Nullable Direction side) {
        if (!quarantined() && !frozen) {
            if (capability == ForgeCapabilities.ITEM_HANDLER) {
                return itemCapability.cast();
            }
            if (capability == ForgeCapabilities.ENERGY) {
                return energyCapability.cast();
            }
        }
        return super.getCapability(capability, side);
    }
}
