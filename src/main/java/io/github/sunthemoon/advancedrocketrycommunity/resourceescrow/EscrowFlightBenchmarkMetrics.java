package io.github.sunthemoon.advancedrocketrycommunity.resourceescrow;

import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;

/** Bounded, server-lifetime release-test observations. Values are never authority or permits. */
final class EscrowFlightBenchmarkMetrics implements EscrowTimingSink {
    static final int MAX_SAMPLES = 131_072;
    record Sample(String phase, long generation, long value) { }
    private final ConcurrentLinkedQueue<Sample> samples = new ConcurrentLinkedQueue<>();
    private final AtomicInteger count = new AtomicInteger();
    private final Map<Long, Long> captured = new TreeMap<>();
    private final Map<Long, Long> written = new TreeMap<>();
    private final Map<String, Integer> checkpoints = new TreeMap<>();
    private final Map<Long, Map<String, Integer>> topology = new TreeMap<>();
    private volatile boolean active;
    private long tickStart, allocationStart;
    private int defersAtTickStart;

    void start() { active = true; }
    void stop() { active = false; }
    boolean active() { return active; }
    @Override public void sample(String phase, long value) { add(phase, -1, value); }
    private void add(String phase, long generation, long value) {
        if (!active) { return; }
        if (!phase.matches("[a-zA-Z0-9_]+") || value < 0) {
            throw new IllegalArgumentException("Invalid governed benchmark sample");
        }
        if (count.incrementAndGet() > MAX_SAMPLES) {
            throw new IllegalStateException("Governed benchmark sample bound exceeded");
        }
        samples.add(new Sample(phase, generation, value));
    }
    @Override public synchronized void captured(EscrowLedgerWriter.Snapshot snapshot, long nanos) {
        if (!active) { return; }
        long generation = snapshot.ledger().writerGeneration();
        if (captured.putIfAbsent(generation, System.nanoTime()) != null) {
            throw new IllegalStateException("Duplicate generation capture");
        }
        add("generation_capture_main", generation, nanos);
        var root = snapshot.ledger();
        topology.put(generation, Map.of("records", root.records().size(), "floors", root.floors().size(),
                "receipts", root.tombstones().size(), "heads", root.rocketAuthorities().size(), "armed", root.armedFlights().size()));
        add("generation_root_bytes", generation,
                io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketNbtSize.uncompressedBytes(snapshot.payload()));
    }
    @Override public void encoded(EscrowLedgerWriter.Snapshot snapshot, long nanos) {
        add("generation_encode_main", snapshot.ledger().writerGeneration(), nanos);
    }
    @Override public void queued(long generation, long nanos) { add("generation_request_queue_age", generation, nanos); }
    @Override public synchronized void writing(long generation) {
        if (!active || !captured.containsKey(generation)) { return; }
        add("generation_queue_age", generation, System.nanoTime() - captured.get(generation));
    }
    @Override public synchronized void written(long generation, long nanos) {
        if (!active || !captured.containsKey(generation)) { return; }
        written.put(generation, System.nanoTime());
        add("generation_writer_io", generation, nanos);
    }
    @Override public synchronized void acknowledged(long generation) {
        if (!active || !captured.containsKey(generation)) { return; }
        Long completed = written.get(generation);
        if (completed == null) { throw new IllegalStateException("Acknowledgement lacks writer observation"); }
        add("generation_handoff", generation, System.nanoTime() - completed);
        add("generation_total_latency", generation, System.nanoTime() - captured.get(generation));
    }
    synchronized void checkpoint(String name) {
        if (!active) { return; }
        if (!name.matches("[A-Z0-9_]+") || checkpoints.size() >= 128 && !checkpoints.containsKey(name)) {
            throw new IllegalStateException("Invalid benchmark checkpoint");
        }
        checkpoints.merge(name, 1, Math::addExact);
    }
    synchronized Map<String, Integer> checkpoints() { return Map.copyOf(checkpoints); }
    private synchronized int deferCount() { return checkpoints.getOrDefault("WORLD_FIRST_DEFER", 0); }
    synchronized Map<Long, Map<String, Integer>> topology() { return Map.copyOf(topology); }
    void startTick() {
        if (!active) { return; }
        tickStart = System.nanoTime(); allocationStart = allocated();
        defersAtTickStart = deferCount();
    }
    void endTick(boolean save) {
        if (!active || tickStart == 0) { return; }
        long elapsed = System.nanoTime() - tickStart;
        sample("tick_main", elapsed);
        if (!save) { sample("tick_main_nonsave", elapsed); }
        // Whole ticks containing actual deferred visits, not an attributed per-visit executor cost.
        if (deferCount() > defersAtTickStart) { sample("tick_main_world_first_defer", elapsed); }
        sample("allocation_bytes", allocated() - allocationStart);
        tickStart = 0;
    }
    static long allocated() {
        var bean = ManagementFactory.getThreadMXBean();
        if (!(bean instanceof com.sun.management.ThreadMXBean memory)
                || !memory.isThreadAllocatedMemorySupported() || !memory.isThreadAllocatedMemoryEnabled()) {
            throw new IllegalStateException("Main-thread allocation accounting is unavailable");
        }
        long bytes = memory.getThreadAllocatedBytes(Thread.currentThread().getId());
        if (bytes < 0) { throw new IllegalStateException("Main-thread allocation accounting returned no value"); }
        return bytes;
    }
    List<Sample> samples() { return List.copyOf(samples); }
    void write(Path target) throws IOException {
        if (active) { throw new IllegalStateException("Stop sampling before export"); }
        var lines = new ArrayList<String>(); lines.add("phase,generation,value");
        for (var value : samples) { lines.add(value.phase() + "," + value.generation() + "," + value.value()); }
        Files.write(target, lines);
    }
}
