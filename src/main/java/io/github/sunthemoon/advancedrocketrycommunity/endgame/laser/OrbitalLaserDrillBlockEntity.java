package io.github.sunthemoon.advancedrocketrycommunity.endgame.laser;

import io.github.sunthemoon.advancedrocketrycommunity.celestial.model.CelestialBodyDefinition;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.authority.EndgameAuthority;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.device.EndgameDeviceBlockEntity;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.device.EndgameDevices;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.device.EndgameRedstoneMode;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.device.EndgameStations;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.device.EndgameStructure;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.model.EndgameAction;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.model.EndgameCode;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.model.EndgameLimits;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.model.EndgameSystem;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.root.EndgameNbt;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.service.EndgameRuntime;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.service.EndgameService;
import io.github.sunthemoon.advancedrocketrycommunity.registry.ModBlockEntities;
import io.github.sunthemoon.advancedrocketrycommunity.registry.ModBlocks;
import java.security.SecureRandom;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.energy.IEnergyStorage;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * ADR-055 sections 1 to 3 and 6: the orbital laser drill controller. Everything the drill pays for or produces in
 * logical mode lives here ({@link LaserDrillStorage}, settings, seed and operation index), so one chunk save covers
 * payment and output. A logical operation takes the cost, advances the index and adds the whole drawn stack in one
 * tick; a full output pauses with {@code OUTPUT_FULL} and the same draw is retried. In physical mode the controller
 * keeps its side of the link ({@link LaserLink}) and {@link LaserPhysicalDrill} digs one layer per operation at the
 * linked laser target. Button effects are in {@link LaserDrillIntents}.
 */
public final class OrbitalLaserDrillBlockEntity extends EndgameDeviceBlockEntity implements MenuProvider {
    public static final int ROOT_SCHEMA = 1;
    public static final String PATTERN_ID = "advancedrocketrycommunity:orbital_laser_drill";
    private static final int STATUS_RECHECK_TICKS = 20;
    private static final SecureRandom SEEDS = new SecureRandom();
    private static final Set<String> STATE_KEYS = Set.of("running", "redstone", "mode", "seed", "op_index", "energy",
            "lens", "output", "link");

    private final LaserDrillStorage storage = new LaserDrillStorage(this::setChanged, this::getLevel);
    private LazyOptional<IItemHandler> itemCapability = LazyOptional.empty();
    private LazyOptional<IEnergyStorage> energyCapability = LazyOptional.empty();
    private boolean running;
    private EndgameRedstoneMode redstone = EndgameRedstoneMode.IGNORED;
    private LaserDrillMode mode = LaserDrillMode.LOGICAL;
    private long seed;
    private long index;
    @Nullable
    private LaserLink link;

    private final EndgameStructure structure = new EndgameStructure(PATTERN_ID, ModBlocks.ORBITAL_LASER_DRILL.get());
    private EndgameCode status = EndgameCode.STOPPED;
    private EndgameCode lastStop = EndgameCode.OK;
    private boolean admitted;
    private long nextAdmission;
    private long nextEvaluation;
    private long summaryStart = Long.MIN_VALUE;
    private long summaryOperations;
    private long summaryItems;
    private String summaryTable = "-";
    private Optional<String> lastBody = Optional.empty();
    private Optional<String> lastTable = Optional.empty();
    private boolean renderActive;
    private boolean linkSettled;
    private boolean linkLost;
    @Nullable
    private Pending pending;

    /** A physical start waiting for the same player's confirmation (runtime only). */
    record Pending(UUID actor, long tick) {
    }

    /** The station case and the live orbit body at the controller. */
    private record StationBody(EndgameCode station, Optional<CelestialBodyDefinition> body) {
    }

    public OrbitalLaserDrillBlockEntity(BlockPos position, BlockState state) {
        super(ModBlockEntities.ORBITAL_LASER_DRILL.get(), position, state);
    }

    @Override
    public EndgameSystem system() {
        return EndgameSystem.LASER_DRILL;
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
        running = false;
        redstone = EndgameRedstoneMode.IGNORED;
        mode = LaserDrillMode.LOGICAL;
        seed = 0L;
        index = 0L;
        link = null;
        linkSettled = false;
        linkLost = false;
        pending = null;
    }

    /** The seed comes from the server's {@code SecureRandom}, never from a client. */
    @Override
    protected void onNewIdentity() {
        seed = SEEDS.nextLong();
    }

    @Override
    protected void readState(CompoundTag root) {
        running = EndgameNbt.requireBoolean(root, "running");
        redstone = EndgameRedstoneMode.byName(EndgameNbt.requireString(root, "redstone", 16));
        mode = LaserDrillMode.byName(EndgameNbt.requireString(root, "mode", 16));
        seed = EndgameNbt.requireLong(root, "seed");
        index = EndgameNbt.requireLong(root, "op_index");
        if (index < 0) {
            throw new IllegalArgumentException("The operation index is negative");
        }
        storage.read(root);
        link = root.contains("link") ? LaserLink.read(EndgameNbt.requireCompound(root, "link")) : null;
    }

    @Override
    protected void writeState(CompoundTag root) {
        root.putBoolean("running", running);
        root.putString("redstone", redstone.name());
        root.putString("mode", mode.name());
        root.putLong("seed", seed);
        root.putLong("op_index", index);
        storage.write(root);
        if (link != null) {
            root.put("link", link.write());
        }
    }

    public static void serverTick(Level level, BlockPos position, BlockState state, OrbitalLaserDrillBlockEntity drill) {
        if (level instanceof ServerLevel server) {
            drill.tick(server, state);
        }
    }

    private void tick(ServerLevel level, BlockState state) {
        if (quarantined()) {
            return;
        }
        Optional<EndgameDevices> devices = EndgameRuntime.devices();
        Optional<EndgameService> service = EndgameRuntime.operational();
        if (devices.isEmpty() || service.isEmpty() || deviceId().isEmpty()) {
            setStatus(level, null, EndgameCode.ROOT_UNAVAILABLE);
            return;
        }
        long now = level.getGameTime();
        UUID id = deviceId().get();
        structure.tick(level, worldPosition, state.getValue(OrbitalLaserDrillBlock.FACING), id, devices.get(), now);
        LaserDrillSettings settings = devices.get().laserSettings();
        if (running && !admitted && ownerId().isPresent() && now >= nextAdmission) {
            // A running drill that loads asks again; a refused one retries every 200 ticks (ADR-054 section 7).
            admitted = devices.get().active().admit(EndgameSystem.LASER_DRILL, id, ownerId().get(),
                    settings.activePerOwner(), settings.activeGlobal());
            if (!admitted) {
                nextAdmission = now + EndgameLimits.STRUCTURE_REVALIDATION_TICKS;
            }
        }
        summarizeIfDue(service.get(), now);
        if (!structure.known() || now < nextEvaluation) {
            return;
        }
        if (mode == LaserDrillMode.PHYSICAL) {
            physicalTick(level, devices.get(), service.get(), settings, now);
            return;
        }
        LaserDrillOperation.Decision decision = decide(level, devices.get(), settings);
        setStatus(level, service.get(), decision.code());
        if (!decision.operates()) {
            nextEvaluation = now + STATUS_RECHECK_TICKS;
            return;
        }
        if (!devices.get().laserOperations().take(id)) {
            // Over the per-tick cap the operation waits, served in ID order (ADR-054 section 7).
            devices.get().laserOperations().request(id);
            nextEvaluation = now + 1;
            return;
        }
        LaserDrillTable table = decision.table().orElseThrow();
        LaserDrillTable.Entry entry = decision.stack().orElseThrow();
        storage.spend(settings.costFe());
        index++;
        storage.insert(stack(entry));
        summaryOperations++;
        summaryItems += entry.count();
        summaryTable = table.id() + "@" + table.version();
        setChanged();
        nextEvaluation = now + settings.operationIntervalTicks();
    }

    /**
     * One physical evaluation: a contact settles a debt even while the drill is stopped or the system is disabled;
     * layers run only while breaking is allowed (ADR-054 section 1 settlement).
     */
    private void physicalTick(ServerLevel level, EndgameDevices devices, EndgameService service,
                              LaserDrillSettings settings, long now) {
        if (pending != null && now - pending.tick() > LaserDrillIntents.CONFIRM_WINDOW_TICKS) {
            pending = null;
        }
        StationBody stationBody = stationAndBody(level, devices);
        EndgameCode common = LaserDrillOperation.common(inputs(level, devices, settings, stationBody));
        boolean breaking = devices.settings().enabled(EndgameSystem.LASER_DRILL)
                && devices.settings().laserPhysicalMining();
        if (link == null || ownerId().isEmpty()) {
            setStatus(level, service, pending != null ? EndgameCode.CONFIRM_REQUIRED : common != EndgameCode.OK ? common
                    : !breaking ? EndgameCode.PHYSICAL_DISABLED : EndgameCode.NO_TARGET);
            nextEvaluation = now + STATUS_RECHECK_TICKS;
            return;
        }
        long paidBefore = link.opsPaid();
        int energyBefore = storage.energy();
        LaserPhysicalDrill.Outcome outcome = LaserPhysicalDrill.evaluate(new LaserPhysicalDrill.Context(level, devices,
                service, deviceId().orElseThrow(), ownerId().get(), settings, common, breaking, stationBody.body(), now),
                link, storage);
        linkSettled = outcome.settled();
        linkLost = outcome.lost();
        if (link.opsPaid() != paidBefore || storage.energy() != energyBefore) {
            setChanged();
        }
        setStatus(level, service, pending != null && !running ? EndgameCode.CONFIRM_REQUIRED : outcome.code());
        nextEvaluation = outcome.waitingForBudget() ? now + 1 : outcome.code() == EndgameCode.OK
                ? now + settings.operationIntervalTicks() : now + STATUS_RECHECK_TICKS;
    }

    /** The orbit body is the station's live orbit body, re-read for every operation (ADR-055 section 1). */
    private StationBody stationAndBody(ServerLevel level, EndgameDevices devices) {
        EndgameStations.At at = EndgameStations.at(level, worldPosition);
        if (at.station().isEmpty() || ownerId().isEmpty() || !EndgameAuthority.decide(new EndgameAuthority.Request(
                ownerId().get(), false, ownerId(), at.context(), EndgameAction.OPERATE, false)).allowed()) {
            lastBody = Optional.empty();
            return new StationBody(EndgameCode.STATION_UNAVAILABLE, Optional.empty());
        }
        Optional<CelestialBodyDefinition> body = devices.celestial()
                .flatMap(catalog -> catalog.get(at.station().get().orbitBody()));
        lastBody = body.map(definition -> definition.id().toString());
        return new StationBody(EndgameCode.OK, body);
    }

    private LaserDrillOperation.Inputs inputs(ServerLevel level, EndgameDevices devices, LaserDrillSettings settings,
                                              StationBody stationBody) {
        return new LaserDrillOperation.Inputs(ownerId().isPresent(),
                devices.settings().enabled(EndgameSystem.LASER_DRILL), stationBody.station(), structure.code(),
                storage.lensPresent(), running, redstone.satisfied(level.hasNeighborSignal(worldPosition)), admitted,
                stationBody.body(), devices.laserTables(), storage.energy(), settings.costFe(), seed, index);
    }

    private LaserDrillOperation.Decision decide(ServerLevel level, EndgameDevices devices, LaserDrillSettings settings) {
        LaserDrillOperation.Decision decision = LaserDrillOperation.decide(inputs(level, devices, settings,
                stationAndBody(level, devices)), entry -> storage.fits(stack(entry)));
        lastTable = decision.table().map(table -> table.id() + "@" + table.version());
        return decision;
    }

    private static ItemStack stack(LaserDrillTable.Entry entry) {
        Item item = ForgeRegistries.ITEMS.getValue(entry.item());
        return item == null ? ItemStack.EMPTY : new ItemStack(item, entry.count());
    }

    /** Every status code change is audited (ADR-055 section 6). */
    private void setStatus(ServerLevel level, @Nullable EndgameService service, EndgameCode code) {
        if (code == status) {
            return;
        }
        EndgameCode previous = status;
        status = code;
        updateRenderState(level);
        if (code != EndgameCode.OK) {
            lastStop = code;
        }
        if (service != null) {
            service.audit().line(level.getGameTime(), system().id(), "status", code.name(), deviceId().orElse(null),
                    ownerId().orElse(null), null, "previous=" + previous.name());
        }
    }

    /** One logical summary per drill every 1,200 ticks: operations, items and the table version. */
    private void summarizeIfDue(EndgameService service, long now) {
        if (summaryStart == Long.MIN_VALUE) {
            summaryStart = now;
            return;
        }
        if (now - summaryStart < EndgameLimits.AUDIT_SUMMARY_INTERVAL_TICKS) {
            return;
        }
        if (summaryOperations > 0) {
            service.audit().line(now, system().id(), "logical_summary", "OK", deviceId().orElse(null),
                    ownerId().orElse(null), null, "operations=" + summaryOperations + " items=" + summaryItems
                            + " table=" + summaryTable + " algorithm=" + LaserDraw.ALGORITHM);
        }
        summaryStart = now;
        summaryOperations = 0;
        summaryItems = 0;
    }

    // ---- State the button effects change (LaserDrillIntents) --------------------------------------------------

    void running(boolean next, EndgameDevices devices) {
        running = next;
        if (next) {
            admitted = true;
        } else {
            release(devices);
        }
        nextEvaluation = 0;
        setChanged();
        if (level instanceof ServerLevel server) {
            updateRenderState(server);
        }
    }

    void redstoneMode(EndgameRedstoneMode next) {
        redstone = next;
        nextEvaluation = 0;
        setChanged();
    }

    void mode(LaserDrillMode next) {
        mode = next;
        pending = null;
        lastTable = Optional.empty();
        nextEvaluation = 0;
        setChanged();
    }

    /** A new link has had no contact, so it holds no debt or credit yet. */
    void link(@Nullable LaserLink next) {
        link = next;
        linkSettled = true;
        linkLost = false;
        nextEvaluation = 0;
        setChanged();
    }

    Optional<LaserLink> link() {
        return Optional.ofNullable(link);
    }

    boolean linkSettled() {
        return linkSettled;
    }

    boolean linkLost() {
        return linkLost;
    }

    Optional<Pending> pending() {
        return Optional.ofNullable(pending);
    }

    void pending(@Nullable Pending next) {
        pending = next;
        nextEvaluation = 0;
    }

    void audit(String action, EndgameCode code, UUID actor, String fields) {
        if (level instanceof ServerLevel server) {
            EndgameRuntime.operational().ifPresent(service -> service.audit().line(server.getGameTime(), system().id(),
                    action, code.name(), deviceId().orElse(null), ownerId().orElse(null), actor, fields));
        }
    }

    private void release(EndgameDevices devices) {
        admitted = false;
        deviceId().ifPresent(id -> devices.active().release(EndgameSystem.LASER_DRILL, id));
    }

    /** Unloaded or removed devices are not active and leave the structure index (ADR-054 section 7). */
    private void unloadRuntime() {
        EndgameRuntime.devices().ifPresent(devices -> {
            release(devices);
            structure.untrack(devices);
        });
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
    protected String describeState() {
        return "status=" + status.name() + " structure=" + structure.code().name() + " running=" + running
                + " admitted=" + admitted + " redstone=" + redstone.name() + " mode=" + mode.name() + " op_index="
                + index + " energy=" + storage.energy() + " lens=" + storage.lensPresent() + " link="
                + (link == null ? "-" : link.marker() + " ops_paid=" + link.opsPaid() + " settled=" + linkSettled
                + " lost=" + linkLost) + " pending=" + (pending != null);
    }

    public EndgameCode status() {
        return status;
    }

    /** The most recent code that kept the drill from operating. */
    public EndgameCode lastStop() {
        return lastStop;
    }

    public EndgameCode structureCode() {
        return structure.code();
    }

    public boolean running() {
        return running;
    }

    public boolean admitted() {
        return admitted;
    }

    public EndgameRedstoneMode redstoneMode() {
        return redstone;
    }

    public LaserDrillMode mode() {
        return mode;
    }

    public long operationIndex() {
        return index;
    }

    public long seed() {
        return seed;
    }

    /** The linked marker and the controller's {@code ops_paid}, for views and tests. */
    public Optional<UUID> linkedMarker() {
        return link().map(LaserLink::marker);
    }

    public long opsPaid() {
        return link == null ? 0L : link.opsPaid();
    }

    public boolean confirmationPendingFor(UUID player) {
        return pending != null && pending.actor().equals(player);
    }

    /** The orbit body and table of the last evaluation, for the owner's view only. */
    public Optional<String> lastBody() {
        return lastBody;
    }

    public Optional<String> lastTable() {
        return lastTable;
    }

    public LaserDrillStorage storage() {
        return storage;
    }

    /** Ticks until the next evaluation, for the menu's progress value (0..1,200). */
    public int cooldown(long now) {
        return (int) Math.max(0, Math.min(LaserDrillSettings.MAX_INTERVAL_TICKS, nextEvaluation - now));
    }

    /** The emitter glows while the drill runs without a stop code (ADR-055 section 5). */
    private void updateRenderState(ServerLevel level) {
        boolean next = running && status == EndgameCode.OK;
        if (next != renderActive) {
            renderActive = next;
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    @Override
    public CompoundTag getUpdateTag() {
        CompoundTag tag = new CompoundTag();
        tag.putBoolean("active", renderActive);
        return tag;
    }

    @Override
    public void handleUpdateTag(CompoundTag tag) {
        renderActive = tag.getBoolean("active");
    }

    @Nullable
    @Override
    public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    public boolean activeForRender() {
        return renderActive;
    }

    @Override
    public AABB getRenderBoundingBox() {
        return new AABB(worldPosition).expandTowards(0.0D, -(2.0D + LaserBeam.EMITTER_LENGTH), 0.0D);
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("block.advancedrocketrycommunity.orbital_laser_drill");
    }

    @Nullable
    @Override
    public AbstractContainerMenu createMenu(int id, Inventory inventory, Player player) {
        return new OrbitalLaserDrillMenu(id, inventory, this);
    }

    /** A quarantined device is inert: it exposes no capability. */
    @Override
    public <T> LazyOptional<T> getCapability(@Nonnull Capability<T> capability, @Nullable Direction side) {
        if (!quarantined()) {
            if (capability == ForgeCapabilities.ITEM_HANDLER) {
                return itemCapability.cast();
            }
            if (capability == ForgeCapabilities.ENERGY) {
                return energyCapability.cast();
            }
        }
        return super.getCapability(capability, side);
    }

    @Override
    public void onLoad() {
        super.onLoad();
        itemCapability = LazyOptional.of(storage::automation);
        energyCapability = LazyOptional.of(storage::energyInput);
    }

    @Override
    public void invalidateCaps() {
        super.invalidateCaps();
        itemCapability.invalidate();
        energyCapability.invalidate();
    }
}
