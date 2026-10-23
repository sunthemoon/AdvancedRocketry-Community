package io.github.sunthemoon.advancedrocketrycommunity.resourceescrow;

import io.github.sunthemoon.advancedrocketrycommunity.AdvancedRocketryCommunity;
import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketNbtSize;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStartingEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import java.lang.management.ManagementFactory;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.TicketType;
import net.minecraft.util.Unit;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.storage.IOWorker;
import net.minecraft.world.level.chunk.storage.RegionFileStorage;
import net.minecraft.world.level.storage.LevelResource;

/** Test-artifact-only R82 pressure, derived from the unchanged historical native BENCH pattern. */
@Mod.EventBusSubscriber(modid = ModIdentity.MOD_ID)
public final class CargoNativePressureProbe implements EscrowTimingSink {
    private record Sample(String phase, long nanos) { }
    private final MinecraftServer server;
    private final ServerLevel level;
    private final EscrowRuntime runtime;
    private final ConcurrentLinkedQueue<Sample> samples = new ConcurrentLinkedQueue<>();
    private final AtomicInteger sampleCount = new AtomicInteger();
    private final List<EscrowTestEndpoints.Fixture> fixtures = new ArrayList<>();
    private final List<EscrowOperation> operations = new ArrayList<>();
    private final List<Long> admittedAt = new ArrayList<>();
    private final java.util.Set<java.util.UUID> aged = new java.util.HashSet<>();
    private final List<CompletableFuture<Void>> seeded = new ArrayList<>();
    private final com.sun.management.ThreadMXBean threadMemory = (com.sun.management.ThreadMXBean) ManagementFactory.getThreadMXBean();
    private ResourceEscrowSavedData data;
    private EscrowCoordinator coordinator;
    private IOWorker terrain, entities;
    private CompletableFuture<Void> initialForce;
    private CompletableFuture<?> terrainLoad, entityLoad;
    private int seed, warmup, elapsed, prepared, acknowledged;
    private long tickStart, allocationStart;
    private long savedTick = -1, gcStartCount, gcStartMillis;
    private volatile boolean measuring;
    private boolean pressure, done;

    private final java.util.Map<String, Object> report = new java.util.LinkedHashMap<>();
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void starting(ServerStartingEvent event) {
        if (!Boolean.getBoolean("advancedrocketrycommunity.cargoPressureBenchmark")) { return; }
        var server = event.getServer(); EscrowTestEndpoints.requireEnabled(server);
        EscrowTags.require(System.getProperty("advancedrocketrycommunity.escrowScenario") == null,
                "Cargo pressure must not route a historical release probe");
        var runtime = EscrowRuntime.of(server);
        EscrowTags.require(CargoNativeBenchmark.field(runtime, "barrier") == null
                && !(boolean) CargoNativeBenchmark.field(runtime, "consumerTimingSelected"),
                "Pressure timings must attach before service creation");
        var probe = new CargoNativePressureProbe(server, runtime);
        try {
            var sink = EscrowRuntime.class.getDeclaredField("consumerTimings"); sink.setAccessible(true); sink.set(runtime, probe);
            var selected = EscrowRuntime.class.getDeclaredField("consumerTimingSelected");
            selected.setAccessible(true); selected.setBoolean(runtime, true);
        } catch (ReflectiveOperationException failure) { throw new IllegalStateException("Pressure timing attachment failed", failure); }
        MinecraftForge.EVENT_BUS.register(probe);
    }
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void start(TickEvent.ServerTickEvent event) {
        if (event.getServer() == server && event.phase == TickEvent.Phase.START) { startTick(); }
    }
    @SubscribeEvent(priority = EventPriority.LOW)
    public void advance(TickEvent.ServerTickEvent event) {
        if (event.getServer() != server || event.phase != TickEvent.Phase.END) { return; }
        try { tick(); }
        catch (RuntimeException failure) {
            report.put("error", failure.getClass().getName() + ": " + failure.getMessage());
            try { if (coordinator != null && operations.size() > prepared) {
                var proposed = new EscrowLedger(data.ledger().id(), operations.get(operations.size() - 1).sequence(),
                        data.ledger().writerGeneration(), List.copyOf(operations), List.of(), List.of());
                report.put("attempted_records", operations.size());
                report.put("candidate_reserved_bytes", EscrowSettlementCapacity.reservedBytes(proposed));
                report.put("floor_retirement_headroom_bytes", EscrowSettlementCapacity.FLOOR_RETIREMENT_BYTES);
                report.put("root_limit_bytes", EscrowLimits.ROOT_BYTES);
                report.put("pending_at_failure", coordinator.admissionAuthority().pendingCount());
                report.put("retained_at_failure", data.ledger().records().size());
                report.put("next_sequence_at_failure", coordinator.nextSequence());
            } } catch (RuntimeException diagnostic) { report.put("diagnostic_error", diagnostic.toString()); }
            report.put("status", "STOP_MEASUREMENT_ERROR"); writeReport(); throw failure;
        }
    }
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void end(TickEvent.ServerTickEvent event) {
        if (event.getServer() == server && event.phase == TickEvent.Phase.END) { endTick(); }
    }
    @SubscribeEvent
    public void stopped(ServerStoppedEvent event) {
        if (event.getServer() != server) { return; }
        if (!done && !report.containsKey("error")) {
            report.put("error", "Server stopped before pressure measurement completed");
            report.put("status", "STOP_MEASUREMENT_ERROR");
        }
        writeReport(); MinecraftForge.EVENT_BUS.unregister(this);
    }
    private CargoNativePressureProbe(MinecraftServer server, EscrowRuntime runtime) {
        EscrowTestEndpoints.requireEnabled(server); this.server = server; this.runtime = runtime; level = server.overworld();
        EscrowTags.require(threadMemory.isThreadAllocatedMemorySupported() && threadMemory.isThreadAllocatedMemoryEnabled(),
                "Thread allocation measurement unavailable");
        report.put("schema_version", 1); report.put("status", "IN_PROGRESS");
        report.put("source_commit", System.getProperty("advancedrocketrycommunity.benchmarkCommit"));
        report.put("component_sizes", java.util.stream.IntStream.range(0, 58).map(i -> i < 2 ? 4 : 1).boxed().toList());
        report.put("requested_records", 64); report.put("payload_millibuckets", 64000);
        report.put("historical_probe_unchanged", true);
        for (int i = 0; i < 16; i++) {
            var chunk = new ChunkPos(4 + i, 4); level.getChunkSource().addRegionTicket(TicketType.START, chunk, 2, Unit.INSTANCE);
            level.getChunk(chunk.x, chunk.z);
        }
        for (int i = 0; i < 3; i++) {
            var chunk = new ChunkPos(32 + i, 4); level.getChunkSource().addRegionTicket(TicketType.START, chunk, 2, Unit.INSTANCE);
            level.getChunk(chunk.x, chunk.z);
        }
    }
    @Override public void sample(String phase, long nanos) {
        if (!measuring) { return; }
        if (sampleCount.incrementAndGet() > 131_072) { throw new IllegalStateException("Escrow benchmark sample limit exceeded"); }
        samples.add(new Sample(phase, nanos));
    }
    void startTick() {
        if (!measuring || done) { return; }
        tickStart = System.nanoTime(); allocationStart = allocated();
    }
    void endTick() {
        if (!measuring || done || tickStart == 0) { return; }
        sample("tick_main", System.nanoTime() - tickStart); sample("allocation_bytes", allocated() - allocationStart);
        if (savedTick != server.getTickCount()) { sample("tick_main_nonsave", System.nanoTime() - tickStart); }
        if (data.ledger().records().stream().anyMatch(op -> operations.stream().limit(8).anyMatch(chain -> chain.id().equals(op.id()))
                || operations.stream().skip(11).anyMatch(healthy -> healthy.id().equals(op.id())))) {
            sample("tick_main_active", System.nanoTime() - tickStart);
        }
    }
    private long allocated() { return threadMemory.getThreadAllocatedBytes(Thread.currentThread().getId()); }
    void tick() {
        if (done) { return; }
        if (data == null) {
            for (int i = 0; i < 16; i++) { if (!level.areEntitiesLoaded(new ChunkPos(4 + i, 4).toLong())) { return; } }
            for (int i = 0; i < 3; i++) { if (!level.areEntitiesLoaded(new ChunkPos(32 + i, 4).toLong())) { return; } }
            data = ResourceEscrowSavedData.forTests(server); coordinator = data.bind(server);
            for (int i = 0; i < 64; i++) {
                // Independent blocked slots must not veto a healthy endpoint's joint slot proof.
                var pos = i >= 8 && i <= 10 ? new BlockPos((32 + i - 8) * 16, 100, 64)
                        : new BlockPos(64 + (i / 4) * 16, 100, 64 + (i % 4) * 2);
                int component = CargoNativePressureWorkload.component(i);
                fixtures.add(component != i ? EscrowTestEndpoints.chainSource(fixtures.get(component), pos)
                        : EscrowTestEndpoints.create(level, pos, data.ledger().id()));
            }
            for (int first : new int[] {0, 4}) {
                fixtures.get(first).bank().getPersistentData().getCompound("arce_escrow_fixture").putInt("capacity", 4000);
            }
            var sizes = new ArrayList<Integer>(); var historicalSizes = new ArrayList<Integer>();
            for (int i = 0; i < 64; i++) {
                var base = fixtures.get(i).operation(i + 1);
                sizes.add(RocketNbtSize.uncompressedBytes(EscrowCodec.operation(CargoNativePressureWorkload.operation(base, i))));
                // UUIDs, coordinates and epochs have fixed encoded widths; only topology changes.
                historicalSizes.add(RocketNbtSize.uncompressedBytes(EscrowCodec.operation(
                        i < 8 ? CargoNativePressureWorkload.chained(base, i) : base)));
            }
            EscrowTags.require(sizes.equals(historicalSizes), "Pressure record byte load changed");
            report.put("operation_bytes", sizes); report.put("historical_operation_bytes", historicalSizes);
            writeReport();
            server.saveEverything(false, true, true); data.setDirty(); coordinator.autosave();
            terrain = level.getChunkSource().chunkMap.worker; entities = runtime.store(level).entityWorkerForTests();
        }
        if (seed < 256) {
            for (int i = 0; i < 8 && seed < 256; i++, seed++) {
                var pos = new ChunkPos((8 + seed) * 32, 256);
                var chunk = NbtUtils.addCurrentDataVersion(new CompoundTag()); chunk.putInt("xPos", pos.x); chunk.putInt("zPos", pos.z);
                chunk.putString("Status", "minecraft:empty"); chunk.put("sections", new ListTag()); chunk.put("block_entities", new ListTag());
                var entity = NbtUtils.addCurrentDataVersion(new CompoundTag()); entity.putIntArray("Position", new int[] {pos.x, pos.z}); entity.put("Entities", new ListTag());
                seeded.add(terrain.store(pos, chunk)); seeded.add(entities.store(pos, entity));
            }
            return;
        }
        if (initialForce == null) {
            initialForce = CompletableFuture.allOf(seeded.toArray(CompletableFuture[]::new))
                    .thenCompose(ignored -> CompletableFuture.allOf(terrain.synchronize(true), entities.synchronize(true))); return;
        }
        if (!initialForce.isDone() || !pressure && !coordinator.writerIdle()) { return; }
        if (initialForce.isCompletedExceptionally()) { throw new IllegalStateException("Benchmark cache seeding/force failed"); }
        if (warmup++ < 100) { return; }
        if (!pressure) {
            EscrowTags.require(cacheSize(terrain) == 256 && cacheSize(entities) == 256, "Both original region caches must be saturated");
            AdvancedRocketryCommunity.LOGGER.info("ARCE_ESCROW_BENCH_CACHES terrain=256 entities=256 sync={}", server.forceSynchronousWrites());
            gcStartCount = gcCount(); gcStartMillis = gcMillis(); pressure = true; measuring = true;
        }
        elapsed++;
        if (prepared < 64) {
            var fixture = fixtures.get(prepared); var op = fixture.operation(coordinator.nextSequence());
            op = CargoNativePressureWorkload.operation(op, prepared);
            operations.add(op); admittedAt.add(System.nanoTime());
            fixture.prepare(coordinator, op, result -> { EscrowTags.require(result, "Benchmark L1 failed"); acknowledged++; }); prepared++;
        } else if (acknowledged < 64) { coordinator.commitPreparations(); }
        else if (acknowledged == 64) {
            fixtures.get(8).source().getPersistentData().getCompound("arce_escrow_fixture").putInt("amount", 4999); fixtures.get(8).source().setChanged();
            var adapter = runtime.reconcileTests(coordinator, fixtures, endpoint -> endpoint.key().equals(operations.get(9).source().key()));
            adapter.unregister(operations.get(10).source().key()); acknowledged++;
        }
        if (elapsed % 200 == 0) {
            savedTick = server.getTickCount();
            long start = System.nanoTime(); server.saveEverything(false, true, true); sample("autosave_main", System.nanoTime() - start);
        }
        loadProbe(false); loadProbe(true);
        if (acknowledged > 64) {
            for (int i = 0; i < operations.size(); i++) {
                var id = operations.get(i).id();
                if (!aged.contains(id) && data.ledger().records().stream().noneMatch(op -> op.id().equals(id))) {
                    aged.add(id); sample("record_queue_age", System.nanoTime() - admittedAt.get(i));
                }
            }
        }
        if (elapsed % 20 == 0) {
            // Opening one extra region each time forces genuine LRU eviction during competing proof work.
            var pos = new ChunkPos((8 + elapsed / 20 % 256) * 32, 256);
            terrain.loadAsync(pos); entities.loadAsync(pos);
        }
        if (elapsed >= 1400) { finish(); }
    }
    private void loadProbe(boolean entity) {
        var old = entity ? entityLoad : terrainLoad; if (old != null && !old.isDone()) { return; }
        IOWorker worker = entity ? entities : terrain; long start = System.nanoTime();
        var pos = new ChunkPos((8 + elapsed % 256) * 32, 256);
        var next = worker.loadAsync(pos).whenComplete((root, failure) -> {
            if (failure != null) { throw new IllegalStateException("Benchmark vanilla load failed", failure); }
            sample(entity ? "entity_load_queue_io" : "terrain_load_queue_io", System.nanoTime() - start);
        });
        if (entity) { entityLoad = next; } else { terrainLoad = next; }
    }
    private static int cacheSize(IOWorker worker) {
        // Read-only test diagnostics by exact field types; no writer/decorator replacement or fault seam.
        try {
            var field = java.util.Arrays.stream(IOWorker.class.getDeclaredFields()).filter(f -> f.getType() == RegionFileStorage.class).findFirst().orElseThrow();
            field.setAccessible(true); Object storage = field.get(worker);
            var cache = java.util.Arrays.stream(RegionFileStorage.class.getDeclaredFields())
                    .filter(f -> f.getType() == it.unimi.dsi.fastutil.longs.Long2ObjectLinkedOpenHashMap.class).findFirst().orElseThrow();
            cache.setAccessible(true); return ((java.util.Map<?, ?>) cache.get(storage)).size();
        } catch (ReflectiveOperationException failure) { throw new IllegalStateException(failure); }
    }
    private void finish() {
        EscrowTags.require(data.ledger().records().size() == 3, "Healthy benchmark records did not retire within the fixed window");
        measuring = false; done = true;
        report.put("status", "NATIVE_MEASURED"); writeReport();
        Path result = server.getWorldPath(LevelResource.ROOT).resolve("escrow-benchmark.csv");
        try (var output = Files.newBufferedWriter(result)) {
            output.write("phase,value\n"); for (var sample : samples) { output.write(sample.phase() + "," + sample.nanos() + "\n"); }
        } catch (java.io.IOException failure) { throw new IllegalStateException(failure); }
        AdvancedRocketryCommunity.LOGGER.info("ARCE_ESCROW_BENCH_DONE ticks={} records=3 sync={} samples={} gc_count={} gc_ms={}",
                elapsed, server.forceSynchronousWrites(), sampleCount.get(), gcCount() - gcStartCount, gcMillis() - gcStartMillis);
    }
    private void writeReport() {
        report.put("admitted_records", prepared); report.put("acknowledged_records", Math.min(acknowledged, 64));
        report.put("elapsed_ticks", elapsed); report.put("retired_records", aged.size());
        report.put("retained_records", data == null ? 0 : data.ledger().records().size());
        report.put("pending_records", coordinator == null ? 0 : coordinator.admissionAuthority().pendingCount());
        report.put("heap_max_bytes", Runtime.getRuntime().maxMemory());
        try { Files.writeString(server.getWorldPath(LevelResource.ROOT).resolve("cargo-pressure.json"),
                new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(report)); }
        catch (java.io.IOException failure) { throw new IllegalStateException("Pressure evidence export failed", failure); }
    }
    private static long gcCount() { return ManagementFactory.getGarbageCollectorMXBeans().stream().mapToLong(bean -> Math.max(0, bean.getCollectionCount())).sum(); }
    private static long gcMillis() { return ManagementFactory.getGarbageCollectorMXBeans().stream().mapToLong(bean -> Math.max(0, bean.getCollectionTime())).sum(); }
}
