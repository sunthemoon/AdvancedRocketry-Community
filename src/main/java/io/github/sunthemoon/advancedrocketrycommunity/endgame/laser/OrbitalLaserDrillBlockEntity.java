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
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.energy.IEnergyStorage;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * ADR-055 sections 1, 2 and 6: the orbital laser drill controller. Everything the drill pays for or produces lives
 * here ({@link LaserDrillStorage}, settings, seed and operation index), so one chunk save covers payment and output.
 * A logical operation takes the cost, advances the index and adds the whole drawn stack in one tick at the
 * controller; a full output pauses with {@code OUTPUT_FULL} and the same draw is retried.
 */
public final class OrbitalLaserDrillBlockEntity extends EndgameDeviceBlockEntity implements MenuProvider {
    public static final int ROOT_SCHEMA = 1;
    public static final String PATTERN_ID = "advancedrocketrycommunity:orbital_laser_drill";
    private static final int STATUS_RECHECK_TICKS = 20;
    private static final SecureRandom SEEDS = new SecureRandom();
    private static final Set<String> STATE_KEYS = Set.of("running", "redstone", "mode", "seed", "op_index", "energy",
            "lens", "output");

    private final LaserDrillStorage storage = new LaserDrillStorage(this::setChanged, this::getLevel);
    private LazyOptional<IItemHandler> itemCapability = LazyOptional.empty();
    private LazyOptional<IEnergyStorage> energyCapability = LazyOptional.empty();
    private boolean running;
    private EndgameRedstoneMode redstone = EndgameRedstoneMode.IGNORED;
    private LaserDrillMode mode = LaserDrillMode.LOGICAL;
    private long seed;
    private long index;

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
        if (mode != LaserDrillMode.LOGICAL) {
            throw new IllegalArgumentException("This build has only the logical mode");
        }
        seed = EndgameNbt.requireLong(root, "seed");
        index = EndgameNbt.requireLong(root, "op_index");
        if (index < 0) {
            throw new IllegalArgumentException("The operation index is negative");
        }
        storage.read(root);
    }

    @Override
    protected void writeState(CompoundTag root) {
        root.putBoolean("running", running);
        root.putString("redstone", redstone.name());
        root.putString("mode", mode.name());
        root.putLong("seed", seed);
        root.putLong("op_index", index);
        storage.write(root);
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

    /** The orbit body is the station's live orbit body, re-read for every operation (ADR-055 section 1). */
    private LaserDrillOperation.Decision decide(ServerLevel level, EndgameDevices devices, LaserDrillSettings settings) {
        EndgameStations.At at = EndgameStations.at(level, worldPosition);
        EndgameCode station = EndgameCode.OK;
        Optional<CelestialBodyDefinition> body = Optional.empty();
        if (at.station().isEmpty() || ownerId().isEmpty() || !EndgameAuthority.decide(new EndgameAuthority.Request(
                ownerId().get(), false, ownerId(), at.context(), EndgameAction.OPERATE, false)).allowed()) {
            station = EndgameCode.STATION_UNAVAILABLE;
        } else {
            body = devices.celestial().flatMap(catalog -> catalog.get(at.station().get().orbitBody()));
        }
        lastBody = body.map(definition -> definition.id().toString());
        LaserDrillOperation.Decision decision = LaserDrillOperation.decide(new LaserDrillOperation.Inputs(ownerId().isPresent(),
                devices.settings().enabled(EndgameSystem.LASER_DRILL), station, structure.code(),
                storage.lensPresent(), running, redstone.satisfied(level.hasNeighborSignal(worldPosition)), admitted,
                body, devices.laserTables(), storage.energy(), settings.costFe(), seed, index),
                entry -> storage.fits(stack(entry)));
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

    /** Start: admitted first come first served, refused beyond a limit with {@code ACTIVE_LIMIT}. */
    EndgameCode start(EndgameDevices devices, UUID actor) {
        if (running) {
            return EndgameCode.OK;
        }
        if (ownerId().isEmpty()) {
            return EndgameCode.UNOWNED;
        }
        LaserDrillSettings settings = devices.laserSettings();
        if (!devices.active().admit(EndgameSystem.LASER_DRILL, deviceId().orElseThrow(), ownerId().get(),
                settings.activePerOwner(), settings.activeGlobal())) {
            audit("start", EndgameCode.ACTIVE_LIMIT, actor, "");
            return EndgameCode.ACTIVE_LIMIT;
        }
        running = true;
        admitted = true;
        nextEvaluation = 0;
        setChanged();
        audit("start", EndgameCode.OK, actor, "");
        return EndgameCode.OK;
    }

    EndgameCode stop(EndgameDevices devices, UUID actor) {
        if (!running) {
            return EndgameCode.OK;
        }
        running = false;
        release(devices);
        nextEvaluation = 0;
        setChanged();
        audit("stop", EndgameCode.OK, actor, "");
        return EndgameCode.OK;
    }

    EndgameCode cycleRedstone(UUID actor) {
        redstone = redstone.next();
        nextEvaluation = 0;
        setChanged();
        audit("redstone", EndgameCode.OK, actor, "mode=" + redstone.name());
        return EndgameCode.OK;
    }

    private void audit(String action, EndgameCode code, UUID actor, String fields) {
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
                + index + " energy=" + storage.energy() + " lens=" + storage.lensPresent();
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
