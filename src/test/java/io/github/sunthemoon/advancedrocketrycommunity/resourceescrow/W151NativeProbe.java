package io.github.sunthemoon.advancedrocketrycommunity.resourceescrow;

import io.github.sunthemoon.advancedrocketrycommunity.AdvancedRocketryCommunity;
import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.entity.RocketEntity;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFlightState;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFlightAction;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.persistence.RocketTransferSavedData;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.server.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.eventbus.api.*;
import net.minecraftforge.fml.common.Mod;

/** W151 packaged-server fixture. Only the explicitly built diagnostic JAR includes this class. */
@Mod.EventBusSubscriber(modid = ModIdentity.MOD_ID)
public final class W151NativeProbe {
    private final MinecraftServer server;
    private final EscrowFlightBenchmarkLoad load;
    private final String kind = System.getProperty("advancedrocketrycommunity.w151Case", "load");
    private final boolean recovery = !"start".equals(System.getProperty("advancedrocketrycommunity.w151Stage", "start"));
    private final ConcurrentLinkedQueue<Map<String, Object>> events = new ConcurrentLinkedQueue<>();
    private final AtomicInteger count = new AtomicInteger();
    private final List<Long> ticks = new ArrayList<>();
    private final List<Map<String, Object>> cycles = new ArrayList<>();
    private final List<RocketEntity> governed = new ArrayList<>();
    private final Map<String, Object> report = new LinkedHashMap<>();
    private final IdentityHashMap<Object, Integer> watches = new IdentityHashMap<>();
    private final BlockPos shared = new BlockPos(72, 100, 72), other = new BlockPos(104, 124, 72);
    private W151NativeReturns.Producer ordinary, control;
    private ResourceEscrowSavedData data;
    private EscrowTestEndpoints.Fixture backlog;
    private volatile Map<String, Object> cycle;
    private volatile String activeRequest = "none";
    private volatile int observedTick;
    private long tickStart, measuredAt = -1, completedAt = -1;
    private int age, nextGoverned, supersedingStart, ordinaryStores, controlStores, controlDuring;
    private boolean initialized, measured, done, cut, awaitingCapture, backlogQueued, backlogAck, saved, injected, retried;

    @SubscribeEvent public static void started(ServerStartedEvent event) {
        if (!Boolean.getBoolean("advancedrocketrycommunity.w151Native")) { return; }
        EscrowTestEndpoints.requireEnabled(event.getServer());
        MinecraftForge.EVENT_BUS.register(new W151NativeProbe(event.getServer()));
    }
    private W151NativeProbe(MinecraftServer server) {
        this.server = server; load = new EscrowFlightBenchmarkLoad(server);
        report.put("source", System.getProperty("advancedrocketrycommunity.w151Commit"));
        report.put("case", kind); report.put("stage", System.getProperty("advancedrocketrycommunity.w151Stage"));
        report.put("deadline_ticks", 4000); report.put("heap_max", Runtime.getRuntime().maxMemory());
        var runtime = EscrowRuntime.of(server);
        EscrowTags.require(runtime.monitor().ordinaryAvailable(load.earth.dimension().location().toString(), new ChunkPos(shared)),
                "A scheduling pin survived the process");
        report.put("restart_initial_slot_unpinned", true);
        runtime.monitor().observerForTests(server, this::capture);
        runtime.terminalCutForTests(server, this::checkpoint);
    }
    static UUID id(String label) { return UUID.nameUUIDFromBytes(("w151:" + label).getBytes(StandardCharsets.UTF_8)); }
    private void event(Map<String, Object> row) {
        if (count.incrementAndGet() > 24000) { throw new IllegalStateException("Native event bound exceeded"); }
        events.add(row);
    }
    private void checkpoint(String label) {
        if (done) { return; }
        // Writer-thread events contain only immutable labels and the published tick, not live world reads.
        event(Map.of("event", "checkpoint", "label", label, "tick", observedTick,
                "thread", Thread.currentThread().getName(), "cycle", activeRequest));
        if (!server.isSameThread() || cycle == null) { return; }
        if (label.equals("F1_STORE_BEFORE")) { awaitingCapture = true; }
        if (label.equals("F1_READBACK_AFTER")) {
            cycle.put("callback", observedTick);
            if (!backlogQueued) {
                var op = backlog.operation(data.bind(server).nextSequence());
                var queue = EscrowRuntime.of(server).fills(server);
                data.bind(server).prepareTest(op, ok -> {
                    EscrowTags.require(ok, "Native ordinary ledger backlog failed");
                    backlog.apply(op); backlogAck = true; queue.resolved(op.id());
                    event(Map.of("event", "ledger_backlog_ack", "tick", server.getTickCount(), "operation", op.id().toString()));
                });
                queue.preparing(op.id()); backlogQueued = true;
                cycle.put("backlog", op.id().toString());
            }
        }
        if (label.equals("F1_ARM_CAPTURE")) { cycle.put("writer_capture", observedTick); }
        if (label.equals("F1_ARM_ACK")) {
            cycle.put("ack", observedTick); cycle.put("release", observedTick);
            var arm = data.ledger().armedFlights().stream().filter(a -> a.logical().equals(governed.get(nextGoverned).assemblyTransactionId().orElseThrow())).findFirst().orElseThrow();
            cycle.put("transfer", arm.transfer().toString()); cycle.put("serial", arm.serial());
            cycle.put("writer_generation", data.ledger().writerGeneration());
            if (kind.equals("ack")) { pause("ack"); }
        }
    }
    private void capture(EscrowSaveMonitor.Observation observation) {
        boolean ours = observation.slot().entities() && observation.slot().level().equals(load.earth.dimension().location().toString())
                && (observation.slot().chunk() == new ChunkPos(shared).toLong() || observation.slot().chunk() == new ChunkPos(other).toLong());
        if (!ours || done) { return; }
        int identity = watches.computeIfAbsent(observation.counter(), ignored -> watches.size() + 1);
        var row = new LinkedHashMap<String, Object>();
        row.put("event", "generation"); row.put("slot", observation.slot().chunk()); row.put("watch", identity);
        row.put("generation", observation.generation()); row.put("cause", observation.cause()); row.put("tick", server.getTickCount());
        boolean pinned = !EscrowRuntime.of(server).monitor().ordinaryAvailable(observation.slot().level(), new ChunkPos(observation.slot().chunk()));
        row.put("protected_capture", pinned); event(row);
        if (awaitingCapture && observation.cause().equals("CAPTURE") && observation.slot().chunk() == new ChunkPos(shared).toLong()) {
            cycle.put("capture", server.getTickCount()); cycle.put("watch", identity); cycle.put("generation", observation.generation());
            cycle.put("slot", observation.slot().chunk()); cycle.put("admitted_attempts", (int) cycle.getOrDefault("admitted_attempts", 0) + 1);
            awaitingCapture = false;
        } else if (observation.cause().equals("CAPTURE") && !pinned) {
            if (observation.slot().chunk() == new ChunkPos(shared).toLong()) { ordinaryStores++; }
            else { controlStores++; if (cycle != null && !cycle.containsKey("ack")) { controlDuring++; } }
        }
    }
    @SubscribeEvent(priority = EventPriority.HIGHEST) public void start(TickEvent.ServerTickEvent event) {
        if (event.getServer() != server || event.phase != TickEvent.Phase.START || done) { return; }
        observedTick = server.getTickCount(); tickStart = System.nanoTime();
    }
    @SubscribeEvent(priority = EventPriority.LOWEST) public void end(TickEvent.ServerTickEvent event) {
        if (event.getServer() != server || event.phase != TickEvent.Phase.END || done) { return; }
        try {
            if (++age > 4000) { finish("BOUND_MISSED", "Fixed 4000-tick failure ceiling reached"); return; }
            work();
            if (measured && !done) { ticks.add(System.nanoTime() - tickStart); }
        } catch (RuntimeException failure) {
            report.put("stack", Arrays.stream(failure.getStackTrace()).limit(25).map(Object::toString).toList());
            finish("OBSERVATION_FAILED", failure.toString());
        }
    }
    private void work() {
        if (!load.setup(recovery)) { return; }
        if (data == null) { data = recovery ? ResourceEscrowSavedData.existing(server) : ResourceEscrowSavedData.forTests(server); }
        EscrowTags.require(data != null, "Restart lost the escrow root"); data.bind(server);
        var journal = RocketTransferSavedData.get(server);
        if (!journal.retryChecked(server) || !data.bind(server).operational()) { return; }
        if (!initialized) { initialize(); }
        long now = server.getTickCount();
        if (cycle != null && !cycle.containsKey("ack") && now - ((Number) cycle.get("requested")).longValue() > 1860) {
            finish("BOUND_MISSED", "Conditional test service ceiling 20+200+200+200+40+1000+200 exceeded"); return;
        }
        if (!measured) {
            if (!recovery) { load.assemble(); }
            measured = true; measuredAt = now; supersedingStart = EscrowRuntime.of(server).barrier().supersedingSaves();
        }
        if (age % 200 == 0) { load.refill(); }
        if (recovery) { recover(); return; }
        var player = load.players.get(0);
        boolean producing = completedAt < 0 || now - completedAt < 200;
        if (nextGoverned < 3 && cycle == null && ordinary.completed > 0 && ordinary.countdown()
                && EscrowRuntime.of(server).barrier().available(load.earth.dimension().location().toString(), true, now)) {
            if (!saved) {
                EscrowTags.require(server.saveEverything(false, true, true), "Baseline save refused");
                saved = true; return;
            }
            var rocket = governed.get(nextGoverned); UUID request = UUID.randomUUID();
            cycle = new LinkedHashMap<>(); cycle.put("request", request.toString()); cycle.put("requested", now);
            activeRequest = request.toString();
            cycle.put("logical", rocket.assemblyTransactionId().orElseThrow().toString()); cycle.put("ordinary_before", ordinaryStores);
            cycles.add(cycle);
            W151NativeReturns.request(player, rocket, RocketFlightAction.LAUNCH, request);
            EscrowTags.require(RocketEscrowGate.flightFrozen(rocket), "Governed request did not admit F1");
            if (kind.equals("save") && !injected) {
                EscrowTags.require(load.earth.entityManager.storeChunkSections(new ChunkPos(shared).toLong(), entity -> { }), "Actual save refused");
                cycle.put("external_save", now); injected = true;
            }
        }
        ordinary.tick(player, producing); control.tick(player, producing);
        if (cycle != null && !cycle.containsKey("ack")) {
            var pending = W151NativeReturns.observation(ordinary.rocket);
            if (Boolean.TRUE.equals(pending.get("pending")) && pending.get("attempts").equals(0)) {
                cycle.putIfAbsent("ordinary_deferred", now);
                if (kind.equals("deferred")) { pause("deferred"); }
            }
            var writes = W151NativeReturnsAccess.field(data.bind(server), "flightWrites");
            Map<?, ?> queue = W151NativeReturnsAccess.field(writes, "queued");
            if (!queue.isEmpty()) {
                cycle.putIfAbsent("queued", now);
                if (kind.equals("queued")) { pause("queued"); }
            }
            if (kind.equals("save") && injected && !retried && !RocketEscrowGate.flightFrozen(governed.get(nextGoverned))
                    && now - ((Number) cycle.get("requested")).longValue() >= 120) {
                EscrowTags.require(!cycle.containsKey("ack") && journal.findByLogicalRocket(governed.get(nextGoverned).assemblyTransactionId().orElseThrow()).isEmpty(),
                        "External save retained stale ARM/T authority");
                cycle.put("expected_refusal", true); cycle = null; activeRequest = "none"; retried = true;
            }
        }
        if (cycle != null && cycle.containsKey("ack")) {
            var rocket = governed.get(nextGoverned);
            if (rocket.flightData().orElseThrow().state() == RocketFlightState.COUNTDOWN && !cycle.containsKey("cancel")) {
                W151NativeReturns.request(player, rocket, RocketFlightAction.CANCEL, UUID.randomUUID()); cycle.put("cancel", now);
            }
            UUID logical = rocket.assemblyTransactionId().orElseThrow();
            if (cycle.containsKey("cancel") && journal.findByLogicalRocket(logical).isEmpty() && RocketEscrowGate.idle(rocket)
                    && journal.actionAuthorityAvailable(logical)) {
                EscrowTags.require(rocket.flightData().orElseThrow().fuel().totalAmount() == 1000, "Governed return changed fuel");
                cycle.put("settled", now); nextGoverned++; cycle = null; activeRequest = "none";
                if (nextGoverned == 3) { completedAt = now; report.put("ordinary_at_governed_completion", ordinaryStores); }
            }
        }
        if (!producing && journal.entries().isEmpty() && RocketEscrowGate.idle(ordinary.rocket) && RocketEscrowGate.idle(control.rocket)) {
            validate(); finish("COMPLETE", "");
        }
    }
    private void initialize() {
        var player = load.players.get(0);
        if (recovery) {
            ordinary = new W151NativeReturns.Producer(find(id("ordinary")), this::event);
            control = new W151NativeReturns.Producer(find(id("control")), this::event);
            for (int i = 0; i < 3; i++) { governed.add(find(id("governed-" + i))); }
        } else {
            ordinary = new W151NativeReturns.Producer(W151NativeReturns.create(load.earth, shared.above(24), id("ordinary"), player.getUUID()), this::event);
            control = new W151NativeReturns.Producer(W151NativeReturns.create(load.earth, other, id("control"), player.getUUID()), this::event);
            for (int i = 0; i < 3; i++) {
                var rocket = EscrowFlightBenchmarkLoad.createFlightRocket(load.earth, id("governed-" + i), player.getUUID(), shared.above(i * 8));
                var initial = rocket.flightData().orElseThrow();
                rocket.updateFlightData(initial.withFuel(initial.fuel().withFluidBank(false, ModIdentity.id("rocket_fuel"), null, 1000), load.earth.getGameTime()));
                var tag = rocket.saveWithoutId(new CompoundTag());
                tag.getCompound("RocketEntityData").put("resource_escrow", new RocketEscrowSection(data.ledger().id(), 0, List.of()).encode());
                rocket.load(tag); EscrowTags.require(load.earth.addFreshEntity(rocket), "Governed publication refused"); governed.add(rocket);
            }
            backlog = EscrowTestEndpoints.create(load.earth, new BlockPos(136, 100, 72), data.ledger().id());
        }
        initialized = true;
    }
    private RocketEntity find(UUID logical) {
        var matches = load.earth.getEntitiesOfClass(RocketEntity.class, new AABB(64, 0, 64, 144, 256, 80)).stream()
                .filter(r -> r.assemblyTransactionId().filter(logical::equals).isPresent()).toList();
        EscrowTags.require(matches.size() == 1, "Missing/duplicate native logical rocket " + logical); return matches.get(0);
    }
    private void recover() {
        var journal = RocketTransferSavedData.get(server); long now = server.getTickCount();
        ordinary.tick(load.players.get(0), false); control.tick(load.players.get(0), false);
        for (var rocket : governed) {
            if (rocket.flightData().orElseThrow().state() == RocketFlightState.COUNTDOWN && now % 20 == 0) {
                W151NativeReturns.request(load.players.get(0), rocket, RocketFlightAction.CANCEL, UUID.randomUUID());
            }
        }
        if (now - measuredAt < 200 || !journal.entries().isEmpty() || !journal.acknowledgedCurrent()) { return; }
        for (var rocket : all()) {
            EscrowTags.require(find(rocket.assemblyTransactionId().orElseThrow()) == rocket
                    && rocket.flightData().orElseThrow().fuel().totalAmount() == 1000 && RocketEscrowGate.idle(rocket),
                    "Restart lost conservation or retained authority");
        }
        finish("RECOVERED", "");
    }
    private List<RocketEntity> all() { var result = new ArrayList<>(governed); result.add(ordinary.rocket); result.add(control.rocket); return result; }
    private void validate() {
        EscrowTags.require(nextGoverned == 3 && ordinary.completed >= 2 && control.completed >= 2 && ordinary.polls > 0
                && ordinaryStores > (int) report.get("ordinary_at_governed_completion") && controlDuring > 0 && backlogAck,
                "Missing continuous demand, before/after capture, independent progress or backlog acknowledgement");
        for (var row : cycles) {
            if (Boolean.TRUE.equals(row.get("expected_refusal"))) { continue; }
            EscrowTags.require(row.get("admitted_attempts").equals(1) && row.containsKey("ordinary_deferred")
                    && row.containsKey("callback") && row.containsKey("writer_capture") && row.containsKey("ack") && row.containsKey("release"),
                    "Missing one-attempt consumer observations: " + row);
        }
        EscrowTags.require(cycles.get(retried ? 1 : 0).containsKey("queued"), "No native W145 queue residence observed");
        int superseding = EscrowRuntime.of(server).barrier().supersedingSaves() - supersedingStart;
        EscrowTags.require(superseding == (kind.equals("save") ? 1 : 0), "Unexpected governed invalidations: " + superseding);
        for (var rocket : all()) { EscrowTags.require(find(rocket.assemblyTransactionId().orElseThrow()) == rocket, "Duplicate survivor"); }
        EscrowTags.require(load.assembled(), "Reference 2048-block assembly missing");
    }
    private void pause(String boundary) {
        if (cut || done) { return; } cut = true; report.put("cut", boundary);
        export("CUT", ""); AdvancedRocketryCommunity.LOGGER.info("W151_NATIVE_CUT boundary={}", boundary);
        // Test-only cut observation window; the external driver terminates only this owned JVM.
        try { Thread.sleep(20000); } catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); }
        finish("OBSERVATION_FAILED", "Cut was not stopped within its observation window");
    }
    private void finish(String status, String error) {
        if (done) { return; } done = true; export(status, error);
        AdvancedRocketryCommunity.LOGGER.info("W151_NATIVE_DONE status={}", status);
    }
    private void export(String status, String error) {
        report.put("status", status); report.put("error", error); report.put("age", age); report.put("cycles", cycles);
        report.put("events", List.copyOf(events)); report.put("tick_ns", ticks); report.put("load", load.diagnostics());
        report.put("ordinary_stores", ordinaryStores); report.put("control_stores", controlStores); report.put("control_during", controlDuring);
        if (ordinary != null) { report.put("ordinary_completed", ordinary.completed); report.put("ordinary_polls", ordinary.polls); report.put("control_completed", control.completed); }
        try { Files.writeString(server.getWorldPath(LevelResource.ROOT).resolve("w151-native.json"),
                new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(report)); }
        catch (java.io.IOException failure) { throw new java.io.UncheckedIOException(failure); }
    }
}
