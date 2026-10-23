package io.github.sunthemoon.advancedrocketrycommunity.resourceescrow;

import io.github.sunthemoon.advancedrocketrycommunity.AdvancedRocketryCommunity;
import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.CelestialIds;
import io.github.sunthemoon.advancedrocketrycommunity.registry.ModEntities;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.cargo.device.*;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.entity.RocketEntity;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.fuel.loader.FluidFuelLoaderBlockEntity;
import java.nio.file.Files;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.*;
import net.minecraftforge.eventbus.api.*;
import net.minecraftforge.fml.common.Mod;

/** Test-artifact-only composition of the existing reference load and real production cargo/fuel queues. */
@Mod.EventBusSubscriber(modid = ModIdentity.MOD_ID)
public final class CargoNativeBenchmark {
    private static final String PROPERTY = "advancedrocketrycommunity.cargoBenchmark";
    private final MinecraftServer server;
    private final List<Device> devices = new ArrayList<>();
    private final List<FluidFuelLoaderBlockEntity> fuel = new ArrayList<>();
    private final List<Refusal> refusals = new ArrayList<>();
    private final List<Map<String, Object>> ticks = new ArrayList<>();
    private final Map<String, Object> report = new LinkedHashMap<>();
    private CargoNativePackets packets;
    private boolean placed, started, finished;
    private long startNanos;
    private int startTick;
    private int reconciliations, commits;
    private long publicationStart;
    private final Map<UUID, String> previousHashes = new HashMap<>();
    private final List<Map<String, Object>> actions = new ArrayList<>();
    private final List<Map<String, Object>> trackingLosses = new ArrayList<>();
    private static final class Refusal {
        final RocketEntity rocket;
        final RocketItemCargoBlockEntity device;
        final net.minecraft.nbt.CompoundTag beforeRocket, beforeDevice;
        final EscrowOperation.Kind kind;
        final int start;
        final Map<String, Object> row = new LinkedHashMap<>();
        boolean complete;
        Refusal(RocketEntity rocket, RocketItemCargoBlockEntity device, boolean load, int start) {
            this.rocket = rocket; this.device = device; this.start = start;
            kind = load ? EscrowOperation.Kind.ITEM_CARGO_LOAD : EscrowOperation.Kind.ITEM_CARGO_UNLOAD;
            beforeRocket = EscrowNativeEnvelope.rocket(rocket); beforeDevice = device.saveWithFullMetadata();
            row.put("kind", kind.name()); row.put("tag_byte_keys", 355); row.put("start_tick", start);
        }
    }
    private static final class Device {
        final RocketEntity rocket;
        final EscrowCargoProbeDevice device;
        final boolean item, load, large;
        final String before, after;
        final Map<String, Object> row = new LinkedHashMap<>();
        long requested;
        int admitted = -1, published = -1, retired = -1;
        Device(RocketEntity rocket, EscrowCargoProbeDevice device, boolean item, boolean load, boolean large, String after) {
            this.rocket = rocket; this.device = device; this.item = item; this.load = load; this.large = large;
            before = rocket.snapshot().orElseThrow().contentHash(); this.after = after;
            row.put("device", (item ? "item_" : "fluid_") + (load ? "loader" : "unloader")); row.put("large", large);
            row.put("physical", rocket.getUUID()); row.put("before_hash", before); row.put("after_hash", after);
            row.put("blocks", rocket.snapshot().orElseThrow().blocks().size());
            row.put("payloads", rocket.snapshot().orElseThrow().blocks().stream().filter(b -> b.blockEntityPayload().isPresent()).count());
            row.put("aggregate_bytes", CargoNativeFixtures.aggregate(rocket.snapshot().orElseThrow()));
            row.put("snapshot_bytes", CargoNativeFixtures.encoded(rocket.snapshot().orElseThrow()));
        }
        EscrowOperation.Kind kind() { return item ? (load ? EscrowOperation.Kind.ITEM_CARGO_LOAD : EscrowOperation.Kind.ITEM_CARGO_UNLOAD)
                : (load ? EscrowOperation.Kind.FLUID_CARGO_LOAD : EscrowOperation.Kind.FLUID_CARGO_UNLOAD); }
        RocketCargoStatus attempt() {
            return item ? ((RocketItemCargoBlockEntity) rocket.level().getBlockEntity(position())).attempt() : device.fluidEntity().attempt();
        }
        BlockPos position() { return (BlockPos) row.get("position"); }
    }
    @SubscribeEvent(priority = EventPriority.LOWEST) public static void starting(ServerStartingEvent event) {
        if (!Boolean.getBoolean(PROPERTY) || !"start".equals(System.getProperty("advancedrocketrycommunity.escrowScenario"))) { return; }
        EscrowTestEndpoints.requireEnabled(event.getServer());
        MinecraftForge.EVENT_BUS.register(new CargoNativeBenchmark(event.getServer()));
    }
    private CargoNativeBenchmark(MinecraftServer server) {
        this.server = server; report.put("schema_version", 1); report.put("status", "IN_PROGRESS");
        report.put("source_commit", System.getProperty("advancedrocketrycommunity.benchmarkCommit"));
        report.put("heap_max_bytes", Runtime.getRuntime().maxMemory());
        report.put("jvm_flags", java.lang.management.ManagementFactory.getRuntimeMXBean().getInputArguments());
        report.put("collectors", java.lang.management.ManagementFactory.getGarbageCollectorMXBeans().stream().map(b -> b.getName()).toList());
        var moon = Objects.requireNonNull(server.getLevel(CelestialIds.MOON_LEVEL));
        for (int i = 0; i < 12; i++) {
            var chunk = new ChunkPos(at(i)); moon.getChunkSource().addRegionTicket(TicketType.START, chunk, 2, net.minecraft.util.Unit.INSTANCE);
            moon.getChunk(chunk.x, chunk.z); // Fixed test setup only; all later endpoint access is loaded-only.
        }
    }
    // Keep every origin within the stationary Moon player's two-chunk view distance. Snapshot size is unchanged;
    // these are stationary, explicitly bound entities, so overlapping visual extents do not select a different device.
    private static BlockPos at(int i) { return new BlockPos(248 + (i % 4) * 8, 100, 144 + (i / 4) * 8); }

    @SubscribeEvent(priority = EventPriority.HIGHEST) public void start(TickEvent.ServerTickEvent event) {
        if (event.getServer() != server || event.phase != TickEvent.Phase.START || !started || finished) { return; }
        startNanos = System.nanoTime();
        reconciliations = 0; commits = 0;
    }
    @SubscribeEvent(priority = EventPriority.LOW) public void tick(TickEvent.ServerTickEvent event) {
        if (event.getServer() != server || event.phase != TickEvent.Phase.END || finished) { return; }
        try { work(); }
        catch (RuntimeException failure) {
            report.put("error", failure.toString());
            report.put("error_stack", Arrays.stream(failure.getStackTrace()).limit(30).map(Object::toString).toList());
            finish("STOP_MEASUREMENT_ERROR");
        }
    }
    @SubscribeEvent public void untracking(net.minecraftforge.event.entity.player.PlayerEvent.StopTracking event) {
        if (!started || finished || event.getEntity().getServer() != server) { return; }
        if (devices.stream().anyMatch(d -> d.rocket == event.getTarget())) {
            trackingLosses.add(Map.of("tick", server.getTickCount(), "player", event.getEntity().getUUID(), "rocket", event.getTarget().getUUID()));
        }
    }
    private void work() {
        var runtime = EscrowRuntime.of(server);
        var reference = (EscrowFlightBenchmarkProbe) field(runtime, "flightBenchmark");
        // The reference probe creates its first ledger after Runtime's scheduler lookup on that tick.
        // Attach only after the next ordinary Runtime tick has created the production scheduler.
        if (reference == null || !(boolean) field(reference, "initialized") || runtime.reconciliationScheduler() == null) { return; }
        if (!placed) { place(); placed = true; }
        observeRefusals();
        if (!(boolean) field(reference, "measuring")) { return; }
        if (!started) {
            packets.check();
            for (var d : devices) { EscrowTags.require(packets.complete(d.rocket.getUUID(), d.before), "Initial tracked visual missing: " + d.row); }
            started = true; startTick = server.getTickCount();
            for (var d : devices) {
                d.requested = System.nanoTime(); EscrowCargoSwitches.forTests(server, d.kind(), d.device.id(), true);
                d.row.put("requested_tick", startTick); d.row.put("attempt", d.attempt().name());
            }
            for (var loader : fuel) {
                runtime.loaderAdmissionForTests(server, FluidFuelLoaderEscrowFixtures.device(loader), true);
                loader.attempt();
            }
            report.put("mixed_queue_start", runtime.fills(server).size()); return;
        }
        packets.check();
        var data = ResourceEscrowSavedData.existing(server); int tick = server.getTickCount();
        for (var d : devices) {
            if (d.admitted < 0 && data.ledger().records().stream().anyMatch(op -> op.device().identity().equals(d.device.id()))) {
                d.admitted = tick; d.row.put("admitted_tick", tick);
                EscrowCargoSwitches.forTests(server, d.kind(), d.device.id(), false);
            }
            if (d.published < 0 && d.rocket.snapshot().orElseThrow().contentHash().equals(d.after)) {
                d.published = tick; d.row.put("published_tick", tick);
            }
            if (d.retired < 0 && d.published >= 0 && d.rocket.escrowSection().markers().isEmpty()
                    && d.device.section().markers().isEmpty() && d.device.section().epoch() == 1
                    && data.ledger().records().stream().noneMatch(op -> op.device().identity().equals(d.device.id()))) {
                d.retired = tick; d.row.put("retired_tick", tick); d.row.put("cycle_ticks", tick - startTick);
                d.row.put("cycle_ns", System.nanoTime() - d.requested);
                EscrowTags.require(d.device.amount() == (d.item ? (d.load ? 6 : 64) : (d.load ? 4000 : 1000)), "Device resource conservation failed");
                d.row.put("resources_verified", true);
            }
        }
        for (var loader : fuel) {
            if (data.ledger().records().stream().anyMatch(op -> op.device().identity().equals(FluidFuelLoaderEscrowFixtures.device(loader)))) {
                runtime.loaderAdmissionForTests(server, FluidFuelLoaderEscrowFixtures.device(loader), false);
            }
        }
        var budget = runtime.cargoPreparations().budget();
        var used = ((Number) field(budget, "tick")).longValue() == tick;
        long bytes = used ? budget.chargedBytes() : 0; int visits = used ? budget.chargedVisits() : 0;
        int reconstructions = used ? budget.reconstructions() : 0;
        EscrowTags.require(bytes <= 16L * CargoNativeFixtures.MIB && visits <= 16384 && reconstructions <= 1, "Native shared cargo budget exceeded");
        EscrowTags.require(ticks.size() < 4000, "Native cargo observation deadline exceeded");
        var barrierBudget = (EscrowBarrierBudget) field(runtime.barrier(), "budget");
        EscrowTags.require(commits <= 64 && reconciliations <= 8 && barrierBudget.bytes() <= 16L * CargoNativeFixtures.MIB,
                "Native commits/reconciliations/detached capture budget exceeded");
        ticks.add(Map.of("tick", tick, "wall_ns", startNanos == 0 ? 0 : System.nanoTime() - startNanos,
                "charged_bytes", bytes, "visits", visits, "reconstructions", reconstructions,
                "records", data.ledger().records().size(), "queue", runtime.fills(server).size(),
                "commits", commits, "reconciliations", reconciliations, "detached_capture_bytes", barrierBudget.bytes()));
        if ((boolean) field(reference, "finishing") || (boolean) field(reference, "done")) {
            var gaps = new ArrayList<String>();
            if (!trackingLosses.isEmpty()) { gaps.add("cargo tracking continuity lost; no continuous-player republication claim"); }
            for (var d : devices) {
                boolean visual = packets.complete(d.rocket.getUUID(), d.after); d.row.put("updated_visual", visual);
                if (d.retired < 0) { gaps.add(d.row.get("device") + ": cycle incomplete, large=" + d.large); }
                if (!visual) { gaps.add(d.row.get("device") + ": no updated-hash visual on continuously observed transport, large=" + d.large); }
            }
            report.put("measurement_failures", gaps);
            if (refusals.size() != 2 || refusals.stream().anyMatch(r -> !r.complete)) { gaps.add("native item NBT refusals incomplete"); }
            finish(gaps.isEmpty() ? "NATIVE_MEASURED" : "STOP_MISSING_MEASUREMENT");
        }
    }
    private void place() {
        packets = new CargoNativePackets(server);
        var runtime = EscrowRuntime.of(server);
        runtime.terminalCutForTests(server, this::checkpoint);
        var scheduler = runtime.reconciliationScheduler();
        var driver = (EscrowReconciliationScheduler.Driver) field(scheduler, "driver");
        // Transparent diagnostic wrapper around the unchanged production dispatch, not a replacement executor.
        set(scheduler, "driver", (EscrowReconciliationScheduler.Driver) (op, records, completion) -> {
            reconciliations++; long before = System.nanoTime();
            driver.attempt(op, records, completion);
            if (started && op.kind().cargo()) {
                EscrowTags.require(actions.size() < 32768, "Native action observation bound exceeded");
                actions.add(Map.of("tick", server.getTickCount(), "operation", op.id(), "kind", op.kind().name(),
                        "phase", "reconciliation_main", "wall_ns", System.nanoTime() - before));
            }
        });
        var moon = Objects.requireNonNull(server.getLevel(CelestialIds.MOON_LEVEL));
        for (int i = 0; i < 8; i++) {
            boolean item = i % 4 >= 2, load = i % 2 == 0, large = i < 4; var position = at(i);
            EscrowTags.require(moon.isPositionEntityTicking(position), "Cargo fixture chunk not ticking");
            var base = CargoNativeFixtures.base(moon, position.offset(4, 0, 0), item, load);
            var snapshot = large ? CargoNativeFixtures.largest(base, item, load) : base;
            var rocket = Objects.requireNonNull(ModEntities.ROCKET.get().create(moon)); rocket.initialize(snapshot, UUID.randomUUID(), UUID.randomUUID());
            EscrowTags.require(moon.addFreshEntity(rocket), "Cargo native publication failed");
            var device = EscrowCargoProbeDevice.create(moon, position, rocket, load, item);
            var d = new Device(rocket, device, item, load, large, CargoNativeFixtures.after(snapshot, item, load).contentHash());
            d.row.put("position", position); d.row.put("adjacent_codec_refused", !large || !CargoNativeFixtures.fits(base,
                    CargoNativeFixtures.aggregate(snapshot) + 1, item, load)); devices.add(d);
        }
        for (int i = 8; i < 10; i++) {
            var position = at(i); var rocket = FluidFuelLoaderEscrowFixtures.rocket(moon, position.offset(4, 0, 0), UUID.randomUUID(),
                    FluidFuelLoaderEscrowFixtures.mono(2000));
            EscrowTags.require(moon.addFreshEntity(rocket), "Fuel benchmark rocket failed");
            fuel.add(FluidFuelLoaderEscrowFixtures.loader(moon, position, rocket.ownerId().orElseThrow(),
                    FluidFuelLoaderEscrowFixtures.tank(FluidFuelLoaderEscrowFixtures.ROCKET_FUEL, 5000), FluidFuelLoaderEscrowFixtures.binding(rocket)));
        }
        for (int i = 10; i < 12; i++) {
            boolean load = i == 10; var position = at(i);
            var stack = new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.STONE, 64).save(new net.minecraft.nbt.CompoundTag());
            var tag = new net.minecraft.nbt.CompoundTag(); for (int key = 0; key < 355; key++) { tag.putByte("n" + key, (byte) 1); }
            stack.put("tag", tag); EscrowItemStackCodec.validateNative(stack);
            var items = new net.minecraft.nbt.ListTag();
            if (!load) { var entry = stack.copy(); entry.putByte("Slot", (byte) 0); items.add(entry); }
            var snapshot = ItemCargoReconciliationFixtures.snapshot(moon, position.offset(4, 0, 0), items);
            var framed = new EscrowItemCargoPayload(new EscrowCargoPayload.Cursor(snapshot.snapshotId(), 0, 0, 0,
                    snapshot.blocks().get(0).blockEntityPayload().orElseThrow().adapterId().toString()), stack, 0, 0).encode();
            var rocket = Objects.requireNonNull(ModEntities.ROCKET.get().create(moon)); rocket.initialize(snapshot, UUID.randomUUID(), UUID.randomUUID());
            EscrowTags.require(moon.addFreshEntity(rocket), "Refusal fixture publication failed");
            EscrowCargoProbeDevice.create(moon, position, rocket, load, true);
            var device = (RocketItemCargoBlockEntity) moon.getBlockEntity(position);
            var buffer = load ? new EscrowItemBuffer(List.of(stack, new net.minecraft.nbt.CompoundTag(), new net.minecraft.nbt.CompoundTag(), new net.minecraft.nbt.CompoundTag()))
                    : RocketItemCargoRoot.EMPTY;
            var outer = new net.minecraft.nbt.CompoundTag(); outer.put(RocketItemCargoRoot.KEY, device.root().orElseThrow().withBuffer(buffer).encode()); device.load(outer);
            var refusal = new Refusal(rocket, device, load, server.getTickCount()); refusals.add(refusal);
            refusal.row.put("inner_and_frame_valid", true);
            refusal.row.put("framed_bytes", io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketNbtSize.uncompressedBytes(framed));
            EscrowCargoSwitches.forTests(server, refusal.kind, device.root().orElseThrow().device(), true); device.attempt();
        }
    }
    private void observeRefusals() {
        for (var refusal : refusals) {
            if (refusal.complete) { continue; }
            var status = refusal.device.displayStatus();
            if (status != RocketCargoStatus.CARGO_TOO_LARGE) {
                EscrowTags.require(server.getTickCount() - refusal.start < 100, "Native refusal did not finish within 100 ticks: " + status); continue;
            }
            EscrowTags.require(refusal.beforeRocket.equals(EscrowNativeEnvelope.rocket(refusal.rocket))
                    && refusal.beforeDevice.equals(refusal.device.saveWithFullMetadata())
                    && ResourceEscrowSavedData.existing(server).ledger().records().stream().noneMatch(op -> op.device().identity()
                        .equals(refusal.device.root().orElseThrow().device())), "Refused native item NBT mutated resources/markers or admitted a record");
            refusal.complete = true; refusal.row.put("status", status.name()); refusal.row.put("unmutated", true);
            refusal.row.put("elapsed_ticks", server.getTickCount() - refusal.start);
            EscrowCargoSwitches.forTests(server, refusal.kind, refusal.device.root().orElseThrow().device(), false);
        }
    }
    private void finish(String status) {
        finished = true; report.put("status", status); write();
        AdvancedRocketryCommunity.LOGGER.info("ARCE_CARGO_BENCH_DONE status={}", status);
    }
    private void write() {
        // Observe only after collection stops, so diagnostics do not alter measured tick work.
        var data = ResourceEscrowSavedData.existing(server);
        if (data != null) {
            report.put("final_ledger_snbt", EscrowCodec.encode(data.ledger()).toString());
            report.put("final_writer_idle", data.bind(server).writerIdle());
            report.put("final_ledger_operational", data.bind(server).operational());
        }
        for (var d : devices) {
            var state = new LinkedHashMap<String, Object>();
            state.put("device_id", d.device.id());
            state.put("device_epoch", d.device.section().epoch());
            state.put("device_section_snbt", d.device.section().encode().toString());
            state.put("device_amount", d.device.amount());
            state.put("rocket_section_snbt", d.rocket.escrowSection().encode().toString());
            state.put("rocket_hash", d.rocket.snapshot().orElseThrow().contentHash());
            state.put("rocket_removed", d.rocket.isRemoved());
            var level = (net.minecraft.server.level.ServerLevel) d.rocket.level();
            state.put("device_entity_ticking", level.isPositionEntityTicking(d.position()));
            state.put("rocket_entity_ticking", level.isPositionEntityTicking(d.rocket.blockPosition()));
            state.put("device_status", d.item
                    ? ((RocketItemCargoBlockEntity) level.getBlockEntity(d.position())).displayStatus().name()
                    : d.device.fluidEntity().displayStatus().name());
            d.row.put("final_state", state);
        }
        report.put("devices", devices.stream().map(d -> d.row).toList()); report.put("ticks", ticks);
        report.put("actions", actions);
        report.put("tracking_losses", trackingLosses);
        report.put("item_nbt_refusals", refusals.stream().map(r -> r.row).toList());
        if (packets != null) { report.put("visual_packets", new ArrayList<>(packets.rows)); }
        report.put("fuel_device_epochs", fuel.stream().map(l -> FluidFuelLoaderEscrowFixtures.section(l).epoch()).toList());
        try { Files.writeString(server.getWorldPath(LevelResource.ROOT).resolve("cargo-native.json"),
                new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(report)); }
        catch (java.io.IOException failure) { throw new IllegalStateException("Cargo evidence export failed", failure); }
    }
    @SubscribeEvent public void stopped(ServerStoppedEvent event) {
        if (event.getServer() != server) { return; }
        if (!finished) { report.put("error", "Server stopped before benchmark completion"); report.put("status", "STOP_MEASUREMENT_ERROR"); write(); }
        if (packets != null) { packets.close(); } MinecraftForge.EVENT_BUS.unregister(this);
    }
    static Object field(Object object, String name) { return field(object.getClass(), object, name); }
    static Object field(Class<?> type, Object object, String name) {
        try { var field = type.getDeclaredField(name); field.setAccessible(true); return field.get(object); }
        catch (ReflectiveOperationException failure) { throw new IllegalStateException("Benchmark observation unavailable: " + name, failure); }
    }
    private void checkpoint(String label) {
        if (!server.isSameThread() || !started || finished || !label.startsWith("C2_")) { return; }
        if (label.endsWith("BEFORE")) {
            previousHashes.clear(); devices.forEach(d -> previousHashes.put(d.rocket.getUUID(), d.rocket.snapshot().orElseThrow().contentHash()));
            publicationStart = System.nanoTime();
        } else if (label.endsWith("AFTER")) {
            long elapsed = System.nanoTime() - publicationStart; commits++;
            for (var d : devices) {
                if (!d.rocket.snapshot().orElseThrow().contentHash().equals(previousHashes.get(d.rocket.getUUID()))) {
                    actions.add(Map.of("tick", server.getTickCount(), "physical", d.rocket.getUUID(), "kind", d.kind().name(),
                            "phase", "publication_main", "wall_ns", elapsed));
                }
            }
        }
    }
    private static void set(Object object, String name, Object value) {
        try { var field = object.getClass().getDeclaredField(name); field.setAccessible(true); field.set(object, value); }
        catch (ReflectiveOperationException failure) { throw new IllegalStateException("Benchmark observer attachment unavailable", failure); }
    }
}
