package io.github.sunthemoon.advancedrocketrycommunity.resourceescrow;

import com.mojang.logging.LogUtils;
import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import io.github.sunthemoon.advancedrocketrycommunity.registry.ModEntities;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.cargo.device.RocketCargoRole;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.cargo.device.RocketCargoStatus;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.cargo.device.RocketFluidCargoBlockEntity;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.cargo.device.RocketFluidCargoRoot;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.cargo.device.RocketItemCargoBlockEntity;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.entity.RocketEntity;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.forge.PressurizedTankRocketAdapter;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.fuel.loader.FluidFuelLoaderBlockEntity;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.fuel.loader.LoaderTank;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketBlock;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketBlockState;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketPosition;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketPropulsion;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketStructureSnapshot;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.stats.RocketStats;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.fluids.FluidStack;
import org.slf4j.Logger;

/**
 * W332's gated R-042 admission probe (W332-DESIGN section 2, item 2), reached only through the bay release-test commands
 * and the complete {@link EscrowTestEndpoints} gate. A cell holds five ordinary stationary rockets of one owner, each
 * bound to one real device with an operator-loaded root: a fluid fuel loader and the four cargo devices, in W95's and
 * W330's fixture shapes. An attempt opens one device's gated switch, makes the device's own real attempt and reports
 * the status and whether the rocket and device envelopes, the ledger and the shared authority's counts changed. Nothing
 * here changes an admission decision, a cap, a budget or a deadline.
 */
public final class R042AdmissionProbe {
    /** The five kinds, in cell row order; FUEL is the fluid fuel loader. */
    public enum Kind {
        FUEL(null), FLUID_LOAD(EscrowOperation.Kind.FLUID_CARGO_LOAD), FLUID_UNLOAD(EscrowOperation.Kind.FLUID_CARGO_UNLOAD),
        ITEM_LOAD(EscrowOperation.Kind.ITEM_CARGO_LOAD), ITEM_UNLOAD(EscrowOperation.Kind.ITEM_CARGO_UNLOAD);
        @Nullable final EscrowOperation.Kind cargo;
        Kind(@Nullable EscrowOperation.Kind cargo) { this.cargo = cargo; }
        boolean load() { return this == FLUID_LOAD || this == ITEM_LOAD; }
    }
    static final List<String> CELLS = List.of("A", "B", "S");
    static final int Y = 2;
    private static final int[] ROWS = {2, 6, 10, 14, 18};
    /** Ticks an admitted cargo attempt's switch may stay open while its operation is captured (W330 closed it there). */
    static final int CAPTURE_TICKS = 1_200;
    private static final Logger LOGGER = LogUtils.getLogger();
    /** Open switches of admitted cargo attempts, per server; forgotten when the server stops (as W254's drives). */
    private static final Map<MinecraftServer, List<Watch>> WATCHES = new IdentityHashMap<>();
    private static boolean listening;

    private record Watch(String cell, Kind kind, UUID device, BlockPos at, UUID rocket, long epoch, long deadline) { }

    private R042AdmissionProbe() { }

    static int cellIndex(String name) {
        int index = CELLS.indexOf(name);
        EscrowTags.require(index >= 0, "Unknown R-042 cell " + name);
        return index;
    }
    static BlockPos device(int cell, Kind kind) { return new BlockPos(64 + 16 * cell + 2, Y, ROWS[kind.ordinal()]); }
    static BlockPos rocket(int cell, Kind kind) { return new BlockPos(64 + 16 * cell + 6, Y, ROWS[kind.ordinal()]); }
    static List<Kind> kinds(String value) {
        if ("ALL".equals(value)) { return List.of(Kind.values()); }
        var kinds = Arrays.stream(value.split(",")).map(Kind::valueOf).distinct().toList();
        EscrowTags.require(!kinds.isEmpty(), "No R-042 kind selected");
        return kinds;
    }

    /** Builds one cell of {@code owner}; refused while any of its positions holds a rocket or a block entity. */
    public static int cell(MinecraftServer server, String name, UUID owner) {
        EscrowTestEndpoints.requireEnabled(server);
        int cell = cellIndex(name); ServerLevel level = server.overworld();
        if (!ticking(level, cell)) { LOGGER.info("ARCE_RELEASE_R042_WAIT cell={}", name); return 0; }
        for (Kind kind : Kind.values()) {
            EscrowTags.require(rockets(level, cell, kind).isEmpty() && level.getBlockEntity(device(cell, kind)) == null,
                    "The R-042 cell is occupied");
        }
        for (Kind kind : Kind.values()) {
            RocketEntity rocket = createRocket(level, rocket(cell, kind), owner, kind);
            UUID device = createDevice(level, device(cell, kind), rocket, owner, kind);
            LOGGER.info("ARCE_RELEASE_R042_CELL cell={} kind={} owner={} logical={} rocket={} device={}", name, kind, owner,
                    rocket.assemblyTransactionId().orElseThrow(), rocket.getUUID(), device);
        }
        return 1;
    }

    /** One real attempt of each selected kind's device, with its switch open only for that attempt or its capture. */
    public static int attempt(MinecraftServer server, String name, String selected) {
        EscrowTestEndpoints.requireEnabled(server);
        int cell = cellIndex(name); ServerLevel level = server.overworld();
        if (!ticking(level, cell)) { LOGGER.info("ARCE_RELEASE_R042_WAIT cell={}", name); return 0; }
        // As W330's cap probe: attempt only once the transfer journal's checked write covers the cell's rockets, so a
        // refusal is the admission's own answer and not the journal's TRANSFER_JOURNAL_UNPROVED (W332 smoke 1).
        if (!io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.persistence.RocketTransferSavedData.get(server).retryChecked(server)) {
            LOGGER.info("ARCE_RELEASE_R042_WAIT cell={} reason=TRANSFER_JOURNAL", name); return 0;
        }
        var runtime = EscrowRuntime.of(server); var authority = runtime.propulsion(server).authority();
        for (Kind kind : kinds(selected)) {
            View view = view(level, cell, kind);
            if (view == null) { LOGGER.info("ARCE_RELEASE_R042_ATTEMPT cell={} kind={} found=false", name, kind); continue; }
            int pending = authority.pendingCount(), retained = authority.retainedCount();
            CompoundTag rocketBefore = EscrowNativeEnvelope.rocket(view.rocket()), deviceBefore = view.root();
            CompoundTag ledgerBefore = ledger(server);
            String status; boolean admitted;
            if (kind == Kind.FUEL) {
                var loader = (FluidFuelLoaderBlockEntity) view.device();
                runtime.loaderAdmissionForTests(server, view.deviceId(), true);
                try { admitted = loader.admitFromQueue(); } finally { runtime.loaderAdmissionForTests(server, view.deviceId(), false); }
                status = String.valueOf(loader.lastAttempt());
            } else {
                EscrowCargoSwitches.forTests(server, kind.cargo, view.deviceId(), true);
                RocketCargoStatus result = view.device() instanceof RocketFluidCargoBlockEntity fluid ? fluid.attempt()
                        : ((RocketItemCargoBlockEntity) view.device()).attempt();
                admitted = result == RocketCargoStatus.READY || result == RocketCargoStatus.PREPARING || result == RocketCargoStatus.TRANSFERRING;
                if (admitted) {
                    watch(server, new Watch(name, kind, view.deviceId(), view.device().getBlockPos(), view.rocket().getUUID(),
                            view.deviceEpoch(), server.getTickCount() + CAPTURE_TICKS));
                } else { EscrowCargoSwitches.forTests(server, kind.cargo, view.deviceId(), false); }
                status = result.name();
            }
            CompoundTag ledgerAfter = ledger(server);
            boolean unchanged = rocketBefore.equals(EscrowNativeEnvelope.rocket(view.rocket())) && deviceBefore.equals(view.root())
                    && Objects.equals(ledgerBefore, ledgerAfter) && pending == authority.pendingCount() && retained == authority.retainedCount();
            LOGGER.info("ARCE_RELEASE_R042_ATTEMPT cell={} kind={} found=true status={} admitted={} unchanged={} pending={}>{} "
                    + "retained={}>{} ledger={}>{} logical={}", name, kind, status, admitted, unchanged, pending,
                    authority.pendingCount(), retained, authority.retainedCount(), ledgerBefore == null ? "absent" : "present",
                    ledgerAfter == null ? "absent" : "present", view.logical());
        }
        return 1;
    }

    /** Each kind's amounts, epochs, markers and device hold, read on the server thread. */
    public static int report(MinecraftServer server, String name) {
        EscrowTestEndpoints.requireEnabled(server);
        int cell = cellIndex(name); ServerLevel level = server.overworld();
        if (!ticking(level, cell)) { LOGGER.info("ARCE_RELEASE_R042_WAIT cell={}", name); return 0; }
        for (Kind kind : Kind.values()) {
            View view = view(level, cell, kind);
            if (view == null) { LOGGER.info("ARCE_RELEASE_R042_REPORT cell={} kind={} found=false", name, kind); continue; }
            var section = view.rocket().escrowSection();
            LOGGER.info("ARCE_RELEASE_R042_REPORT cell={} kind={} found=true rocket={} device={} rocketEpoch={} deviceEpoch={} "
                    + "rocketMarkers={} deviceMarkers={} locked={} protected={} refusal={} watching={} logical={}", name, kind,
                    view.rocketAmount(), view.deviceAmount(), section.epoch(), view.deviceEpoch(), section.markers().size(),
                    view.deviceMarkers(), view.locked(), view.protectedFromRemoval(), view.refusal(), watching(server, view.deviceId()),
                    view.logical());
        }
        return 1;
    }

    @Nullable private static CompoundTag ledger(MinecraftServer server) {
        var data = ResourceEscrowSavedData.existing(server);
        return data == null ? null : EscrowCodec.encode(data.ledger());
    }

    private static boolean ticking(ServerLevel level, int cell) {
        return Arrays.stream(Kind.values()).allMatch(kind -> level.isPositionEntityTicking(device(cell, kind))
                && level.isPositionEntityTicking(rocket(cell, kind)));
    }

    private static List<RocketEntity> rockets(ServerLevel level, int cell, Kind kind) {
        return level.getEntitiesOfClass(RocketEntity.class, new AABB(rocket(cell, kind)).inflate(1.0D, 2.0D, 1.0D));
    }

    @Nullable private static View view(ServerLevel level, int cell, Kind kind) {
        var rockets = rockets(level, cell, kind);
        BlockEntity device = level.getBlockEntity(device(cell, kind));
        boolean matches = switch (kind) {
            case FUEL -> device instanceof FluidFuelLoaderBlockEntity;
            case FLUID_LOAD, FLUID_UNLOAD -> device instanceof RocketFluidCargoBlockEntity;
            case ITEM_LOAD, ITEM_UNLOAD -> device instanceof RocketItemCargoBlockEntity;
        };
        return rockets.size() == 1 && matches ? new View(kind, rockets.get(0), device) : null;
    }

    private static RocketEntity createRocket(ServerLevel level, BlockPos at, UUID owner, Kind kind) {
        RocketEntity rocket = switch (kind) {
            case FUEL -> FluidFuelLoaderEscrowFixtures.rocket(level, at, owner, FluidFuelLoaderEscrowFixtures.mono(2_000));
            case FLUID_LOAD, FLUID_UNLOAD -> fluidRocket(level, at, owner, kind.load() ? 0 : 5_000);
            case ITEM_LOAD, ITEM_UNLOAD -> itemRocket(level, at, owner, kind.load() ? 60 : 10);
        };
        EscrowTags.require(level.addFreshEntity(rocket), "The R-042 cell rocket was not added");
        return rocket;
    }

    private static UUID createDevice(ServerLevel level, BlockPos at, RocketEntity rocket, UUID owner, Kind kind) {
        return switch (kind) {
            case FUEL -> FluidFuelLoaderEscrowFixtures.device(FluidFuelLoaderEscrowFixtures.loader(level, at, owner,
                    FluidFuelLoaderEscrowFixtures.tank(FluidFuelLoaderEscrowFixtures.ROCKET_FUEL, 1_000),
                    FluidFuelLoaderEscrowFixtures.binding(rocket)));
            case FLUID_LOAD, FLUID_UNLOAD -> {
                level.setBlockAndUpdate(at, (kind.load() ? io.github.sunthemoon.advancedrocketrycommunity.registry.ModBlocks.ROCKET_FLUID_LOADER
                        : io.github.sunthemoon.advancedrocketrycommunity.registry.ModBlocks.ROCKET_FLUID_UNLOADER).get().defaultBlockState());
                var device = (RocketFluidCargoBlockEntity) Objects.requireNonNull(level.getBlockEntity(at));
                var root = RocketFluidCargoRoot.placed(owner, UUID.randomUUID(), kind.load() ? RocketCargoRole.LOAD : RocketCargoRole.UNLOAD,
                        kind.load() ? LoaderTank.of(new FluidStack(Fluids.WATER, 5_000)) : LoaderTank.EMPTY)
                        .withBinding(FluidFuelLoaderEscrowFixtures.binding(rocket));
                var tag = new CompoundTag(); tag.put(RocketFluidCargoRoot.KEY, root.encode()); device.load(tag);
                yield EscrowCargoCutProbe.id(device);
            }
            case ITEM_LOAD, ITEM_UNLOAD -> EscrowItemCutProbe.id(EscrowItemCutProbe.device(level, at, rocket,
                    kind.load() ? RocketCargoRole.LOAD : RocketCargoRole.UNLOAD, kind.load() ? 10 : 60));
        };
    }

    /** W142's pressurized-tank cargo rocket with {@code cargo} mB of water and 1,000 mB of rocket fuel, owned by {@code owner}. */
    private static RocketEntity fluidRocket(ServerLevel level, BlockPos at, UUID owner, int cargo) {
        var snapshot = RocketStructureSnapshot.create(UUID.randomUUID(), level.dimension().location(),
                new RocketPosition(at.getX(), at.getY(), at.getZ()),
                List.of(new RocketBlock(new RocketPosition(0, 0, 0), new RocketBlockState(PressurizedTankRocketAdapter.TYPE, Map.of()),
                                PressurizedTankRocketAdapter.payload(cargo == 0 ? FluidStack.EMPTY : new FluidStack(Fluids.WATER, cargo))),
                        new RocketBlock(new RocketPosition(0, 1, 0), new RocketBlockState(ResourceLocation.tryParse("minecraft:iron_block"), Map.of()))),
                List.of(new RocketPosition(0, 1, 0)), new RocketStats(2, 100, 1_000, 2_000, 1, 1, 1, 1), level.getGameTime(),
                new RocketPropulsion(RocketPropulsion.Mode.MONO, 2_000, 0, 1, 1, RocketPropulsion.Settings.DEFAULTS));
        var rocket = Objects.requireNonNull(ModEntities.ROCKET.get().create(level));
        rocket.initialize(snapshot, UUID.randomUUID(), owner);
        var flight = rocket.flightData().orElseThrow();
        rocket.updateFlightData(flight.withFuel(flight.fuel().withFluidBank(false, ModIdentity.id("rocket_fuel"), null, 1_000), level.getGameTime()));
        return rocket;
    }

    /** W143's item cargo rocket with {@code count} items, legal native propulsion and 1,000 mB of rocket fuel. */
    private static RocketEntity itemRocket(ServerLevel level, BlockPos origin, UUID owner, int count) {
        var entries = new ListTag(); var stack = ItemCargoReconciliationFixtures.stack(count); stack.putByte("Slot", (byte) 0); entries.add(stack);
        var snapshot = ItemCargoReconciliationFixtures.snapshot(level, origin, entries);
        snapshot = RocketStructureSnapshot.create(snapshot.snapshotId(), snapshot.sourceDimension(), snapshot.sourceOrigin(), snapshot.blocks(),
                snapshot.passengerAnchors(), snapshot.stats(), snapshot.createdAtGameTime(), EscrowItemCutProbe.PROPULSION);
        var rocket = Objects.requireNonNull(ModEntities.ROCKET.get().create(level));
        rocket.initialize(snapshot, UUID.randomUUID(), owner);
        var flight = rocket.flightData().orElseThrow();
        rocket.updateFlightData(flight.withFuel(EscrowItemCutProbe.cargoFuel(flight.fuel()), level.getGameTime()));
        return rocket;
    }

    private static synchronized void watch(MinecraftServer server, Watch watch) {
        if (!listening) {
            MinecraftForge.EVENT_BUS.addListener(EventPriority.LOWEST, false, TickEvent.ServerTickEvent.class, R042AdmissionProbe::tick);
            MinecraftForge.EVENT_BUS.addListener((ServerStoppedEvent stopped) -> forget(stopped.getServer()));
            listening = true;
        }
        WATCHES.computeIfAbsent(server, ignored -> new ArrayList<>()).add(watch);
    }
    private static synchronized void forget(MinecraftServer server) { WATCHES.remove(server); }
    private static synchronized boolean watching(MinecraftServer server, UUID device) {
        return WATCHES.getOrDefault(server, List.of()).stream().anyMatch(watch -> watch.device().equals(device));
    }
    private static synchronized List<Watch> watches(MinecraftServer server) { return List.copyOf(WATCHES.getOrDefault(server, List.of())); }
    private static synchronized void done(MinecraftServer server, Watch watch) {
        var list = WATCHES.get(server); if (list != null) { list.remove(watch); }
    }

    /** At each tick's END: close an admitted cargo switch once its operation is captured, or at its deadline. */
    private static void tick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) { return; }
        var server = event.getServer(); var pending = watches(server);
        if (pending.isEmpty() || !EscrowTestEndpoints.enabled(server)) { return; }
        for (Watch watch : pending) {
            var data = ResourceEscrowSavedData.existing(server);
            boolean recorded = data != null && data.ledger().records().stream().anyMatch(op -> op.rocket().identity().equals(watch.rocket()));
            BlockEntity device = server.overworld().getBlockEntity(watch.at());
            RocketEscrowSection section = device instanceof RocketFluidCargoBlockEntity fluid ? EscrowCargoCutProbe.section(fluid)
                    : device instanceof RocketItemCargoBlockEntity item ? EscrowItemCutProbe.section(item) : null;
            boolean moved = section != null && (section.epoch() != watch.epoch() || !section.markers().isEmpty());
            if (recorded || moved || server.getTickCount() >= watch.deadline()) {
                EscrowCargoSwitches.forTests(server, watch.kind().cargo, watch.device(), false);
                done(server, watch);
                LOGGER.info("ARCE_RELEASE_R042_GATE_CLOSED cell={} kind={} reason={} tick={}", watch.cell(), watch.kind(),
                        recorded || moved ? "CAPTURED" : "DEADLINE", server.getTickCount());
            }
        }
    }

    /** One kind's rocket and device, found by their fixed positions. */
    private record View(Kind kind, RocketEntity rocket, BlockEntity device) {
        UUID logical() { return rocket.assemblyTransactionId().orElseThrow(); }
        UUID deviceId() {
            return switch (kind) {
                case FUEL -> FluidFuelLoaderEscrowFixtures.device((FluidFuelLoaderBlockEntity) device);
                case FLUID_LOAD, FLUID_UNLOAD -> EscrowCargoCutProbe.id((RocketFluidCargoBlockEntity) device);
                case ITEM_LOAD, ITEM_UNLOAD -> EscrowItemCutProbe.id((RocketItemCargoBlockEntity) device);
            };
        }
        CompoundTag root() {
            return switch (kind) {
                case FUEL -> ((FluidFuelLoaderBlockEntity) device).root().orElseThrow().encode();
                case FLUID_LOAD, FLUID_UNLOAD -> ((RocketFluidCargoBlockEntity) device).root().orElseThrow().encode();
                case ITEM_LOAD, ITEM_UNLOAD -> ((RocketItemCargoBlockEntity) device).root().orElseThrow().encode();
            };
        }
        long rocketAmount() {
            return switch (kind) {
                case FUEL -> FluidFuelLoaderEscrowFixtures.bank(rocket, false);
                case FLUID_LOAD, FLUID_UNLOAD -> EscrowCargoCutProbe.cargo(rocket);
                case ITEM_LOAD, ITEM_UNLOAD -> EscrowItemCutProbe.cargo(rocket);
            };
        }
        long deviceAmount() {
            return switch (kind) {
                case FUEL -> FluidFuelLoaderEscrowFixtures.amount((FluidFuelLoaderBlockEntity) device);
                case FLUID_LOAD, FLUID_UNLOAD -> EscrowCargoCutProbe.tank((RocketFluidCargoBlockEntity) device);
                case ITEM_LOAD, ITEM_UNLOAD -> EscrowItemCutProbe.tank((RocketItemCargoBlockEntity) device);
            };
        }
        long deviceEpoch() {
            return switch (kind) {
                case FUEL -> FluidFuelLoaderEscrowFixtures.section((FluidFuelLoaderBlockEntity) device).epoch();
                case FLUID_LOAD, FLUID_UNLOAD -> EscrowCargoCutProbe.section((RocketFluidCargoBlockEntity) device).epoch();
                case ITEM_LOAD, ITEM_UNLOAD -> EscrowItemCutProbe.section((RocketItemCargoBlockEntity) device).epoch();
            };
        }
        int deviceMarkers() {
            return switch (kind) {
                case FUEL -> FluidFuelLoaderEscrowFixtures.section((FluidFuelLoaderBlockEntity) device).markers().size();
                case FLUID_LOAD, FLUID_UNLOAD -> EscrowCargoCutProbe.section((RocketFluidCargoBlockEntity) device).markers().size();
                case ITEM_LOAD, ITEM_UNLOAD -> EscrowItemCutProbe.section((RocketItemCargoBlockEntity) device).markers().size();
            };
        }
        boolean locked() {
            return device instanceof FluidFuelLoaderBlockEntity loader ? loader.escrowLocked()
                    : device instanceof RocketFluidCargoBlockEntity fluid ? fluid.escrowLocked() : ((RocketItemCargoBlockEntity) device).escrowLocked();
        }
        boolean protectedFromRemoval() {
            return device instanceof FluidFuelLoaderBlockEntity loader ? loader.protectedFromRemoval()
                    : device instanceof RocketFluidCargoBlockEntity fluid ? fluid.protectedFromRemoval()
                    : ((RocketItemCargoBlockEntity) device).protectedFromRemoval();
        }
        String refusal() {
            Object refusal = device instanceof FluidFuelLoaderBlockEntity loader ? loader.deviceRefusal()
                    : device instanceof RocketFluidCargoBlockEntity fluid ? fluid.deviceRefusal() : ((RocketItemCargoBlockEntity) device).deviceRefusal();
            return refusal == null ? "NONE" : refusal.toString();
        }
    }
}
