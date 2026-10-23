package io.github.sunthemoon.advancedrocketrycommunity.resourceescrow;

import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;

/** Bounded, server-lifetime release-test observations. Values are never authority or permits. */
final class EscrowFlightBenchmarkMetrics implements EscrowTimingSink {
    static final int MAX_SAMPLES = 131_072;
    // Release-test vocabulary, inventoried from the emitters below. No wildcard phase acceptance.
    static final Set<String> PHASES = phases();
    private static Set<String> phases() {
        var phases = new java.util.HashSet<String>(List.of(
                // EscrowBarrier, EscrowLedgerWriter and EscrowFlightWrites.
                "capture_main", "own_store_io", "force_io", "readback_io", "ledger_capture_main", "ledger_io",
                "flight_writer_queue_age",
                // This collector, EscrowFlightBenchmarkProbe and EscrowFlightBenchmarkWork.
                "generation_capture_main", "generation_root_bytes", "generation_encode_main", "generation_request_queue_age",
                "generation_queue_age", "generation_writer_io", "generation_handoff", "generation_total_latency",
                "tick_main", "tick_main_nonsave", "tick_main_world_first_defer", "allocation_bytes",
                "root_1mib_encode_main", "root_1mib_allocation_bytes", "autosave_main", "flight_handoff_latency", "ordinary_start_to_c8",
                // EscrowFlightCheckpoint.
                "f1.refused.NO_LEDGER", "f1.refused.RETRY_WAIT_OR_CAPACITY", "f1.refused.COORDINATOR_JOURNAL_OR_RESOURCE_AUTHORITY",
                "f1.refused.BARRIER_BUDGET", "f1.refused.BINDING_OR_PREFLIGHT", "f1.refused.SOURCE_OR_JOURNAL_CHANGED",
                "f1.refused.BARRIER_BEGIN", "f1.barrier", "f1.stall.SOURCE_OR_JOURNAL_CHANGED", "f1.stall.LEDGER_WRITE_FAILED",
                "f1.stall.LEDGER_QUEUE_WAIT",
                // EscrowPreF2Cancellation.
                "cancel.wait.PENDING_OR_RETRY", "cancel.wait.JOURNAL", "cancel.wait.NOT_ARMED_OR_NO_TRANSFER",
                "cancel.wait.COORDINATOR", "cancel.wait.SOURCE_UNLOADED", "cancel.wait.BARRIER_BUDGET",
                "cancel.wait.BARRIER_BEGIN", "cancel.wait.LEDGER_QUEUE_OR_SYNC_COMPLETION", "cancel.barrier",
                "cancel.stall.SOURCE_OR_AUTHORITY_CHANGED", "cancel.stall.SOURCE_OR_JOURNAL_CHANGED", "cancel.stall.LEDGER_WRITE_FAILED",
                // EscrowSettlementRecovery and EscrowSettlementCompletionProbe.
                "settlement_pruning_main", "settlement.attempt", "settlement.barrier", "settlement.confirm_attempt",
                "settlement.refused.O1_QUEUE", "settlement.wait.COORDINATOR_OR_WRITER", "settlement.wait.WRITING_PROOF_OR_RETRY",
                "settlement.wait.JOURNAL", "settlement.wait.BARRIER_OR_WRITER_BUDGET", "settlement.wait.NORMALIZATION",
                "settlement.wait.BARRIER_BEGIN", "settlement.wait.INVALID_PROOF", "settlement.wait.O3_QUEUE",
                "settlement.wait.JOURNAL_REMOVAL", "settlement.wait.MEMBER_AUTHORITY", "settlement.wait.O4_QUEUE"));
        // Barrier results have these seven refusal reasons; NONE is mapped to SUPERSEDING_SAVE by F1/cancel.
        for (var reason : EscrowReason.values()) {
            if (reason.retryableProofFailure()) {
                for (var prefix : List.of("f1.stall.", "cancel.stall.", "settlement.stall.")) { phases.add(prefix + reason.name()); }
            }
        }
        // Settlement also reports typed readback/relocation refusals and caught live-observation diagnostics.
        for (var reason : List.of("NONE", "OTHER_STATE", "RELOCATED_ENDPOINT", "INVALID_ENDPOINT", "WAITING_UNLOADED",
                "O3_WRITE_UNACKNOWLEDGED", "O4_WRITE_UNACKNOWLEDGED",
                // EscrowSettlementRecovery, Observer and Normalization.
                "Unacknowledged journal", "Frozen matching journal changed", "Frozen settlement endpoint changed",
                "Cargo survivor unavailable", "Unknown proof endpoint", "Invalid observation scope", "Endpoint Level is unavailable",
                "WAITING_UNLOADED: endpoint slot must be FULL, entity-loaded and ticking", "Source identity/type changed",
                "Settlement Level observation bound exceeded", "Endpoint identity relocated to another Level",
                "Unsupported settlement entity adapter", "Rocket placement, occupancy or stationary state changed",
                "Foreign rocket ledger", "Duplicate logical identity or observation bound exceeded",
                "Expired settlement normalization permit", "Changed neutral chosen survivor", "Normalization requires acknowledged O1",
                "Chosen survivor changed before normalization", "Chosen survivor unavailable",
                // Native endpoint observation adapters; Optional.orElseThrow() in their live reads.
                "Loader device identity changed", "Foreign loader ledger", "Unsupported or duplicated loader root",
                "Cargo device identity/role changed", "Foreign cargo device ledger", "Cargo device type changed",
                "Unsupported or duplicated cargo device root", "No value present")) { phases.add("settlement.stall." + reason); }
        return Set.copyOf(phases);
    }
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
        if (phase == null || !PHASES.contains(phase) || value < 0) {
            throw new IllegalArgumentException("Invalid governed benchmark sample: " + phase);
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
        for (var value : samples) {
            String phase = value.phase();
            if (phase.contains(",") || phase.contains("\"")) { phase = "\"" + phase.replace("\"", "\"\"") + "\""; }
            lines.add(phase + "," + value.generation() + "," + value.value());
        }
        Files.write(target, lines);
    }
}
