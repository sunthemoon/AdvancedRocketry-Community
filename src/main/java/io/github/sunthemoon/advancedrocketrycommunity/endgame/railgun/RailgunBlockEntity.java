package io.github.sunthemoon.advancedrocketrycommunity.endgame.railgun;

import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.device.EndgameDeviceBlockEntity;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.device.EndgameDeviceTags;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.device.EndgameDevices;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.device.EndgameRedstoneMode;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.device.EndgameStructure;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.model.EndgameCode;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.model.EndgameNbt;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.model.EndgameSystem;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.service.EndgameRuntime;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.service.EndgameService;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.transit.TransitDestinationState;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.transit.TransitEndpoint;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.transit.TransitSourceState;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.transit.TransitTags;
import io.github.sunthemoon.advancedrocketrycommunity.registry.ModBlockEntities;
import io.github.sunthemoon.advancedrocketrycommunity.registry.ModBlocks;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.energy.IEnergyStorage;
import net.minecraftforge.items.IItemHandler;

/**
 * ADR-056 section 2: the railgun controller, a {@code railgun} endpoint of the ADR-054 section 11 transit ledger. Its
 * root holds the buffers ({@link RailgunStorage}), the ledger's source and destination state under {@code transit},
 * the settings (destination, {@code auto}, redstone mode, minimum stack size) and the freeze of a retired ID. It
 * registers once a chunk tag shows its ID persisted, is attached to the ledger while loaded, and launches through
 * {@link RailgunLauncher} within the per-railgun cadence and the server's per-tick cap. It never loads a chunk.
 */
public final class RailgunBlockEntity extends EndgameDeviceBlockEntity implements MenuProvider, TransitEndpoint {
    public static final ResourceLocation KIND = ModIdentity.id("railgun");
    public static final int ROOT_SCHEMA = 1;
    public static final String PATTERN_ID = "advancedrocketrycommunity:railgun";
    public static final int EVENT_LAUNCH = 1;
    public static final int EVENT_ARRIVAL = 2;
    public static final int MUZZLE_HEIGHT = 5;
    private static final int STATUS_CHECK_TICKS = 20;
    private static final int EFFECT_TICKS = 12;
    private static final Set<String> STATE_KEYS = Set.of(EndgameDeviceTags.FROZEN, "energy", "input", "receive",
            TransitTags.SECTION, "destination", "operator_target", "auto", "redstone", "min_stack");
    private static final Set<String> SECTION_KEYS = Set.of("next_seq", "outbox", "incoming", "receipts",
            "conflicts");

    private final RailgunStorage storage = new RailgunStorage(this::setChanged, () -> level);
    private final TransitSourceState source = new TransitSourceState();
    private final TransitDestinationState destination = new TransitDestinationState();
    private final EndgameStructure structure = new EndgameStructure(PATTERN_ID, ModBlocks.RAILGUN.get());
    private LazyOptional<IItemHandler> itemCapability = LazyOptional.empty();
    private LazyOptional<IEnergyStorage> energyCapability = LazyOptional.empty();
    private boolean frozen;
    @Nullable
    private UUID target;
    private boolean operatorTarget;
    private boolean auto;
    private EndgameRedstoneMode redstone = EndgameRedstoneMode.IGNORED;
    private int minStack = RailgunLaunch.MIN_STACK;

    private EndgameCode endpointStatus = EndgameCode.AWAITING_WORLD_SAVE;
    private EndgameCode lastCode = EndgameCode.OK;
    private long nextStatusCheck;
    private long lastLaunch = Long.MIN_VALUE / 2;
    @Nullable
    private UUID pendingActor;
    private boolean launchPending;
    private boolean attached;
    private int receiptsSeen;
    private int effect;
    private long effectUntil;

    public RailgunBlockEntity(BlockPos position, BlockState state) {
        super(ModBlockEntities.RAILGUN.get(), position, state);
    }

    @Override
    public EndgameSystem system() {
        return EndgameSystem.RAILGUN;
    }

    @Override
    protected int rootSchema() {
        return ROOT_SCHEMA;
    }

    @Override
    protected Set<String> stateKeys() {
        return STATE_KEYS;
    }

    @Override
    protected void resetState() {
        storage.reset();
        source.clear();
        destination.clear();
        frozen = false;
        target = null;
        operatorTarget = false;
        auto = false;
        redstone = EndgameRedstoneMode.IGNORED;
        minStack = RailgunLaunch.MIN_STACK;
    }

    @Override
    protected void readState(CompoundTag root) {
        frozen = EndgameNbt.requireBoolean(root, EndgameDeviceTags.FROZEN);
        storage.read(root);
        CompoundTag section = EndgameNbt.requireCompound(root, TransitTags.SECTION);
        EndgameNbt.requireKeys(section, SECTION_KEYS, "Railgun transit section");
        source.read(section);
        destination.read(section);
        target = root.contains("destination") ? EndgameNbt.requireUuid(root, "destination") : null;
        operatorTarget = EndgameNbt.requireBoolean(root, "operator_target");
        if (operatorTarget && target == null) {
            throw new IllegalArgumentException("An operator selection without a destination");
        }
        auto = EndgameNbt.requireBoolean(root, "auto");
        redstone = EndgameRedstoneMode.byName(EndgameNbt.requireString(root, "redstone", 16));
        minStack = EndgameNbt.requireInt(root, "min_stack");
        if (minStack < RailgunLaunch.MIN_STACK || minStack > RailgunLaunch.MAX_STACK) {
            throw new IllegalArgumentException("The minimum stack size is outside 1..64");
        }
        receiptsSeen = destination.receipts().size();
    }

    @Override
    protected void writeState(CompoundTag root) {
        root.putBoolean(EndgameDeviceTags.FROZEN, frozen);
        storage.write(root);
        CompoundTag section = new CompoundTag();
        source.write(section);
        destination.write(section);
        root.put(TransitTags.SECTION, section);
        if (target != null) {
            root.put("destination", NbtUtils.createUUID(target));
        }
        root.putBoolean("operator_target", operatorTarget);
        root.putBoolean("auto", auto);
        root.putString("redstone", redstone.name());
        root.putInt("min_stack", minStack);
    }

    public static void serverTick(Level level, BlockPos position, BlockState state, RailgunBlockEntity railgun) {
        if (level instanceof ServerLevel server) {
            railgun.tick(server, state);
        }
    }

    private void tick(ServerLevel level, BlockState state) {
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
        if (conflict) {
            return;
        }
        structure.tick(level, worldPosition, state.getValue(RailgunBlock.FACING), id, devices.get(), now);
        if (!structure.known() || frozen) {
            return;
        }
        // An automatic launch asks for a slot of the server's cap only with a payload and a destination.
        boolean wanted = launchPending || auto && redstone.satisfied(level.hasNeighborSignal(worldPosition))
                && endpointActive() && target != null
                && RailgunLaunch.payloadSlot(storage.inputStacks(), minStack) >= 0;
        if (!wanted || !RailgunLaunch.cadenceReady(lastLaunch, now)) {
            return;
        }
        if (!devices.get().railgunLaunches().take(id)) {
            devices.get().railgunLaunches().request(id);
            return;
        }
        EndgameCode code = RailgunLauncher.launch(this, level, service.get(), devices.get(), pendingActor, true);
        if (code != EndgameCode.OK && (launchPending || code != lastCode)) {
            RailgunLauncher.audit(this, service.get(), now, launchPending ? "launch" : "auto_launch", code,
                    pendingActor, "");
        }
        launchPending = false;
        pendingActor = null;
        lastCode = code;
        if (code != EndgameCode.OK) {
            lastLaunch = now; // A refused automatic launch tries again after the cadence.
        }
    }

    /** The endpoint status every 20 ticks, as the laser target does (ADR-054 section 9). */
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
            service.awaitRegistration(id, KIND, ownerId().get(), level.dimension().location(), worldPosition.asLong());
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

    /** Marks the chunk changed; a new receipt (a claim while loaded) shows the arrival flash. */
    @Override
    public void transitChanged() {
        setChanged();
        int receipts = destination.receipts().size();
        if (receipts > receiptsSeen && level != null && !level.isClientSide) {
            level.blockEvent(worldPosition, getBlockState().getBlock(), EVENT_ARRIVAL, 0);
        }
        receiptsSeen = receipts;
    }

    @Override
    public boolean returnToInput(List<ItemStack> stacks) {
        return storage.returnToInput(stacks);
    }

    // ---- Settings and launch state (RailgunLauncher, the menu and GameTests) ----------------------------------

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

    public Optional<UUID> target() {
        return Optional.ofNullable(target);
    }

    /** An operator's selection of another owner's railgun passes the route rule's owner check (section 3). */
    public boolean operatorTarget() {
        return operatorTarget;
    }

    void target(@Nullable UUID value, boolean operator) {
        target = value;
        operatorTarget = value != null && operator;
        setChanged();
    }

    public boolean auto() {
        return auto;
    }

    void auto(boolean value) {
        auto = value;
        setChanged();
    }

    public EndgameRedstoneMode redstoneMode() {
        return redstone;
    }

    void redstoneMode(EndgameRedstoneMode mode) {
        redstone = mode;
        setChanged();
    }

    public int minStack() {
        return minStack;
    }

    void minStack(int value) {
        minStack = value;
        setChanged();
    }

    /** A launch intent waits for the cadence and the server's per-tick cap; the next grant runs it. */
    void requestLaunch(UUID actor) {
        launchPending = true;
        pendingActor = actor;
    }

    /** GameTests: the menu's selection and launch intent, after the intent guard a player would pass. */
    public void selectForTest(@Nullable UUID destination, boolean operator) {
        target(destination, operator);
    }

    public void launchForTest(UUID actor) {
        requestLaunch(actor);
    }

    void launched(long now) {
        lastLaunch = now;
        if (level != null) {
            level.blockEvent(worldPosition, getBlockState().getBlock(), EVENT_LAUNCH, 0);
        }
    }

    public long lastLaunch() {
        return lastLaunch;
    }

    public EndgameCode lastCode() {
        return lastCode;
    }

    public EndgameCode structureCode() {
        return structure.code();
    }

    public RailgunStorage storage() {
        return storage;
    }

    /** An operator gave the railgun to another owner: the old owner's destination is no longer selected. */
    @Override
    protected void ownerChanged(UUID previous) {
        target = null;
        operatorTarget = false;
        auto = false;
        setChanged();
        if (level instanceof ServerLevel server && !frozen && deviceId().isPresent() && ownerId().isPresent()) {
            EndgameRuntime.operational().ifPresent(service -> service.awaitRegistration(deviceId().get(), KIND,
                    ownerId().get(), server.dimension().location(), worldPosition.asLong()));
        }
    }

    @Override
    protected String describeState() {
        return "endpoint=" + endpointStatus.name() + " frozen=" + frozen + " structure=" + structure.code().name()
                + " energy=" + storage.energy().energy() + " destination=" + (target == null ? "-" : target)
                + " auto=" + auto + " redstone=" + redstone.name() + " min_stack=" + minStack + " next_seq="
                + source.nextSeq() + " outbox=" + source.outbox().size() + " incoming="
                + destination.incoming().size() + " receipts=" + destination.receipts().size() + " last="
                + lastCode.name();
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
        EndgameRuntime.devices().ifPresent(structure::untrack);
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

    // ---- Visuals (ADR-056 section 6): block events, at most 8 concurrent effects per client --------------------

    @Override
    public boolean triggerEvent(int id, int parameter) {
        if (level != null && level.isClientSide && (id == EVENT_LAUNCH || id == EVENT_ARRIVAL)
                && RailgunEffects.start(level, level.getGameTime(), EFFECT_TICKS)) {
            effect = id;
            effectUntil = level.getGameTime() + EFFECT_TICKS;
            level.addParticle(ParticleTypes.FLASH, worldPosition.getX() + 0.5D, worldPosition.getY() + 1.5D,
                    worldPosition.getZ() + 0.5D, 0.0D, 0.0D, 0.0D);
            return true;
        }
        return super.triggerEvent(id, parameter);
    }

    public static void clientTick(Level level, BlockPos position, BlockState state, RailgunBlockEntity railgun) {
        if (railgun.effectUntil > level.getGameTime()) {
            RailgunEffects.tick(level, position, state, railgun.effect);
        }
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("block.advancedrocketrycommunity.railgun");
    }

    @Nullable
    @Override
    public AbstractContainerMenu createMenu(int id, Inventory inventory, Player player) {
        return new RailgunMenu(id, inventory, this);
    }
}
