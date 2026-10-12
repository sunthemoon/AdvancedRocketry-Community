package io.github.sunthemoon.advancedrocketrycommunity.resourceescrow;

import io.github.sunthemoon.advancedrocketrycommunity.AdvancedRocketryCommunity;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketNativeResources;
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

/** Gated bounded native measurement, not a substitute for docs/17 reference load or four-hour soak. */
final class EscrowBenchmarkProbe implements EscrowTimingSink {
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

    EscrowBenchmarkProbe(MinecraftServer server, EscrowRuntime runtime) {
        EscrowTestEndpoints.requireEnabled(server); this.server = server; this.runtime = runtime; level = server.overworld();
        for (int i = 0; i < 16; i++) {
            var chunk = new ChunkPos(4 + i, 4); level.getChunkSource().addRegionTicket(TicketType.START, chunk, 2, Unit.INSTANCE);
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
    private long allocated() { return threadMemory.isThreadAllocatedMemorySupported() ? threadMemory.getThreadAllocatedBytes(Thread.currentThread().getId()) : 0; }
    void tick() {
        if (done) { return; }
        if (data == null) {
            for (int i = 0; i < 16; i++) { if (!level.areEntitiesLoaded(new ChunkPos(4 + i, 4).toLong())) { return; } }
            data = ResourceEscrowSavedData.forTests(server); coordinator = data.bind(server);
            for (int i = 0; i < 64; i++) {
                var pos = new BlockPos(64 + (i / 4) * 16, 100, 64 + (i % 4) * 2);
                fixtures.add(i > 0 && i < 8 ? EscrowTestEndpoints.chainSource(fixtures.get(0), pos) : EscrowTestEndpoints.create(level, pos, data.ledger().id()));
            }
            fixtures.get(0).bank().getPersistentData().getCompound("arce_escrow_fixture").putInt("capacity", 8000);
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
            if (prepared < 8) {
                var to = op.destination(); int index = prepared;
                var chained = new EscrowEndpoint(to.kind(), to.level(), to.identity(), to.logicalRocket(), to.type(), to.x(), to.y(), to.z(), to.unit(), index,
                        RocketNativeResources.hash("arce-escrow-fixture-1:" + fixture.logical() + ":" + index * 1000),
                        RocketNativeResources.hash("arce-escrow-fixture-1:" + fixture.logical() + ":" + (index + 1) * 1000));
                op = new EscrowOperation(op.id(), op.sequence(), op.owner(), op.createdTick(), op.phase(), op.reason(), op.payload(), op.payloadHash(), op.source(), chained, op.guard());
            }
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
        Path result = server.getWorldPath(LevelResource.ROOT).resolve("escrow-benchmark.csv");
        try (var output = Files.newBufferedWriter(result)) {
            output.write("phase,value\n"); for (var sample : samples) { output.write(sample.phase() + "," + sample.nanos() + "\n"); }
        } catch (java.io.IOException failure) { throw new IllegalStateException(failure); }
        AdvancedRocketryCommunity.LOGGER.info("ARCE_ESCROW_BENCH_DONE ticks={} records=3 sync={} samples={} gc_count={} gc_ms={}",
                elapsed, server.forceSynchronousWrites(), sampleCount.get(), gcCount() - gcStartCount, gcMillis() - gcStartMillis);
    }
    private static long gcCount() { return ManagementFactory.getGarbageCollectorMXBeans().stream().mapToLong(bean -> Math.max(0, bean.getCollectionCount())).sum(); }
    private static long gcMillis() { return ManagementFactory.getGarbageCollectorMXBeans().stream().mapToLong(bean -> Math.max(0, bean.getCollectionTime())).sum(); }
}
