package io.github.sunthemoon.advancedrocketrycommunity.resourceescrow;

import static io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.CargoBenchmarkFixtures.require;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.cargo.*;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketNbtSize;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.network.RocketVisualSnapshot;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.network.RocketVisualSnapshotCodec;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.persistence.SatelliteRegistryBenchmark;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import java.lang.management.ManagementFactory;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import net.minecraft.nbt.CompoundTag;

/** Standalone W144 diagnostic. No test-admission result is native device or docs/17 reference-load evidence. */
public final class CargoBenchmark {
    static final int WARMUP = 5, SAMPLES = 50;
    private static final long MIB = 1_048_576;
    private CargoBenchmark() { }

    public static void main(String[] args) throws Exception {
        Path output = Path.of(args[0]);
        var report = new LinkedHashMap<String, Object>();
        report.put("schema_version", 1); report.put("source_commit", args[1]);
        report.put("status", "IN_PROGRESS"); report.put("environment", "A0_MICROBENCHMARK");
        report.put("max_heap_bytes", Runtime.getRuntime().maxMemory());
        report.put("jvm_flags", ManagementFactory.getRuntimeMXBean().getInputArguments());
        report.put("java_version", System.getProperty("java.runtime.version"));
        report.put("collectors", ManagementFactory.getGarbageCollectorMXBeans().stream().map(b -> b.getName()).toList());
        report.put("warmup_operations", WARMUP); report.put("measured_operations", SAMPLES);
        report.put("fixture_family", "One addressed tank or chest, 127 vanilla chests each with three tagged stone stacks, "
                + "1920 iron blocks. Maxima are for this family, not all possible NBT trees or palette strings.");
        var results = new ArrayList<Map<String, Object>>(); report.put("fixtures", results);
        report.put("not_measured", new ArrayList<>(List.of("native_reference_load", "four_device_cycle_times",
                "mixed_fuel_cargo_queue", "64_competing_records", "native_commit_and_reconciliation_counts",
                "native_detached_memory", "native_visual_republication", "effective_depth_and_node_maxima")));
        try {
            require(Runtime.getRuntime().maxMemory() <= 4L * 1024 * MIB, "Heap exceeds 4 GiB");
            MinecraftBootstrap.initialize();
            report.put("effective_boundaries", CargoBenchmarkBoundaries.measure());
            for (boolean item : List.of(false, true)) {
                for (boolean load : List.of(true, false)) {
                    var result = new LinkedHashMap<String, Object>(); results.add(result);
                    result.put("device", (item ? "item_" : "fluid_") + (load ? "loader" : "unloader"));
                    var seed = CargoBenchmarkFixtures.small(item, load, -1);
                    int largest = CargoBenchmarkFixtures.largest(seed);
                    var candidate = CargoBenchmarkFixtures.expand(seed, largest);
                    result.put("blocks", candidate.before().blocks().size()); result.put("payloads", 128);
                    result.put("before_aggregate_bytes", CargoBenchmarkFixtures.aggregate(candidate.before()));
                    result.put("after_aggregate_bytes", CargoBenchmarkFixtures.aggregate(candidate.after()));
                    result.put("before_snapshot_bytes", RocketNbtSize.uncompressedBytes(candidate.rocketBefore().getCompound("snapshot")));
                    result.put("after_snapshot_bytes", RocketNbtSize.uncompressedBytes(candidate.rocketAfter().getCompound("snapshot")));
                    result.put("adjacent_aggregate_refused", !CargoBenchmarkFixtures.fits(seed, largest + 1));
                    var boundary = CargoBenchmarkFixtures.snapshot(seed, CargoBenchmarkFixtures.AGGREGATE);
                    result.put("oversized_aggregate_bytes", CargoBenchmarkFixtures.aggregate(boundary));
                    try {
                        io.github.sunthemoon.advancedrocketrycommunity.rocket.persistence.RocketSnapshotNbtCodec.encode(boundary);
                        throw new IllegalStateException("Aggregate boundary unexpectedly encoded");
                    } catch (io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketSnapshotException refused) {
                        require(refused.code() == io.github.sunthemoon.advancedrocketrycommunity.rocket.validation.RocketValidationCode.SNAPSHOT_DATA_TOO_LARGE,
                                "Unexpected aggregate-boundary refusal");
                        result.put("oversized_codec_refusal", refused.getMessage());
                    }
                    result.put("one_mib_aggregate_refused", !CargoBenchmarkFixtures.fits(seed, CargoBenchmarkFixtures.AGGREGATE));
                    require(Boolean.TRUE.equals(result.get("one_mib_aggregate_refused")), "Boundary unexpectedly admitted");
                    var warmup = new ArrayList<Map<String, Object>>(); var samples = new ArrayList<Map<String, Object>>();
                    result.put("warmup", warmup); result.put("samples", samples);
                    for (int i = 0; i < WARMUP + SAMPLES; i++) {
                        measure(candidate, i, i < WARMUP ? warmup : samples);
                    }
                    var summaries = summarize(samples); result.put("phases", summaries);
                    SatelliteRegistryBenchmark.persist(output, report);
                    if (!within(summaries)) {
                        report.put("status", "STOP_BUDGET");
                        report.put("stop_reason", "Owner-thread microbenchmark tick exceeds docs/17 thresholds; later fixtures and native runs not attempted.");
                        return;
                    }
                }
            }
            report.put("status", "MICRO_MEASURED");
            report.put("remaining_stages", "Native reference-load and pressure measurements run separately; not a C-B/D-B pass.");
        } catch (Throwable failure) {
            report.put("status", "STOP_MEASUREMENT_ERROR"); report.put("error", failure.toString()); throw failure;
        } finally {
            report.put("gc", ManagementFactory.getGarbageCollectorMXBeans().stream().map(b -> Map.of(
                    "collector", b.getName(), "count", b.getCollectionCount(), "time_ms", b.getCollectionTime())).toList());
            SatelliteRegistryBenchmark.persist(output, report);
        }
    }

    static void measure(CargoBenchmarkFixtures.Candidate c, int operation, List<Map<String, Object>> samples) throws Exception {
        var originals = c.inputs().stream().map(CompoundTag::copy).toList();
        long tick = 1;
        try (var session = c.ticket()) {
            tick = prepare(session, "prepare_tick", operation, tick, samples);
            require(session.cursor().after().contentHash().equals(c.after().contentHash()), "Prepared output differs");
            // Suppliers model detached envelope copies, NOT a native Entity/BlockEntity save.
            var capture = new EscrowCargoPreparedCapture(session.ticket, c.op(), c.inputs().stream()
                    .<java.util.function.Supplier<CompoundTag>>map(input -> input::copy).toList(), () -> true, 64_000);
            boolean done;
            do {
                require(tick < 100, "Capture failed to finish within 100 logical ticks"); session.at(tick++);
                long start = System.nanoTime(); done = capture.advance(EscrowTestFixtures.ledger(), List.of());
                samples.add(sample("capture_tick", operation, tick - 1, System.nanoTime() - start, session));
            } while (!done);
            require(capture.result().status() == RocketCargoSelection.Status.ELIGIBLE, "Capture refused: " + capture.result().status());
            capture.discard();
        }
        try (var recompute = c.ticket()) {
            prepare(recompute, "recompute_tick", operation, 1, samples);
            require(recompute.cursor().after().contentHash().equals(c.after().contentHash()), "Recomputation differs");
        }
        // The real off-thread hashing predicate, on a detached synthetic native entity slot; no disk IO claim.
        var slot = EscrowReconciliationCheckpointTest.rocketSlot(c.op().rocket(), c.rocketAfter());
        var expected = EscrowSettlementDisk.ordinary(slot, c.op().rocket(), EscrowTestFixtures.ledger().id());
        var reader = new EscrowCargoReadback(EscrowTestFixtures.ledger().id(), expected, c.after(), List.of(c.op()));
        var worker = Executors.newSingleThreadExecutor();
        try {
            long nanos = worker.submit(() -> {
                long start = System.nanoTime(); require(reader.test(slot), "Readback decode refused"); return System.nanoTime() - start;
            }).get(30, TimeUnit.SECONDS);
            require(reader.confirmed(), "Owner did not confirm readback");
            samples.add(Map.of("phase", "readback_hash_io", "operation", operation, "wall_ns", nanos));
        } finally {
            worker.shutdownNow(); require(worker.awaitTermination(30, TimeUnit.SECONDS), "Readback worker did not stop");
        }
        long start = System.nanoTime();
        var visual = RocketVisualSnapshot.fromServerSnapshot(c.after());
        var bytes = RocketVisualSnapshotCodec.encode(visual);
        long nanos = System.nanoTime() - start;
        require(RocketVisualSnapshotCodec.decode(bytes).structureContentHash().equals(c.after().contentHash()), "Visual hash differs");
        samples.add(Map.of("phase", "visual_encode_main", "operation", operation, "wall_ns", nanos, "bytes", bytes.length));
        require(originals.equals(c.inputs()), "Benchmark mutated a borrowed endpoint");
    }
    private static long prepare(CargoTicketTestFixture session, String phase, int operation, long tick,
                                List<Map<String, Object>> samples) {
        boolean done;
        do {
            require(tick < 100, "Preparation failed to finish within 100 logical ticks");
            long start = System.nanoTime(); done = session.advance(tick++);
            samples.add(sample(phase, operation, tick - 1, System.nanoTime() - start, session));
        } while (!done);
        require(session.cursor().state() == RocketCargoPreparation.State.READY, "Preparation discarded: " + session.cursor().failure());
        return tick;
    }
    private static Map<String, Object> sample(String phase, int operation, long tick, long nanos, CargoTicketTestFixture session) {
        var budget = session.budget();
        require(budget.chargedBytes() <= 16 * MIB && budget.chargedVisits() <= 16_384 && budget.reconstructions() <= 1,
                "Preparation/capture per-tick work budget exceeded");
        require(session.cursor().retainedNativeBytes() <= 16 * MIB, "Preparation retained memory exceeded");
        return Map.of("phase", phase, "operation", operation, "tick", tick, "wall_ns", nanos,
                "charged_bytes", budget.chargedBytes(), "visits", budget.chargedVisits(), "reconstructions", budget.reconstructions(),
                "preparation_retained_native_bytes", session.cursor().retainedNativeBytes());
    }
    static Map<String, Object> summarize(List<Map<String, Object>> samples) {
        var grouped = new TreeMap<String, List<double[]>>();
        for (var row : samples) {
            grouped.computeIfAbsent((String) row.get("phase"), ignored -> new ArrayList<>())
                    .add(new double[] {((Number) row.get("wall_ns")).doubleValue() / 1e6, 0});
        }
        var summaries = new TreeMap<String, Object>();
        grouped.forEach((phase, values) -> {
            var stats = new LinkedHashMap<>(SatelliteRegistryBenchmark.statistics(values));
            // No CPU samples were collected; do not retain placeholder zero CPU statistics.
            stats.remove("cpu_p50_ms"); stats.remove("cpu_p95_ms");
            stats.put("within", (double) stats.get("mean_ms") <= 25 && (double) stats.get("p95_ms") <= 50
                    && (double) stats.get("p99_ms") <= 100 && (double) stats.get("maximum_ms") <= 500);
            summaries.put(phase, stats);
        });
        return summaries;
    }
    static boolean within(Map<String, Object> phases) {
        for (String phase : List.of("prepare_tick", "capture_tick", "recompute_tick", "visual_encode_main")) {
            if (!(phases.get(phase) instanceof Map<?, ?> stats) || !Boolean.TRUE.equals(stats.get("within"))) { return false; }
        }
        return true;
    }
}
