package io.github.sunthemoon.advancedrocketrycommunity.resourceescrow;

import static org.junit.jupiter.api.Assertions.*;

import io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.CheckedSavedDataFile;
import io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.CheckedSavedDataPolicy;
import io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.ManagedSavedDataType;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.persistence.RocketJournalTestAccess;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.persistence.RocketTransferSavedData;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketNbtSize;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.server.W231BayCapacityFixture;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.persistence.SatelliteRegistryBenchmark;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import java.lang.management.ManagementFactory;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** W231 repeatable A0 measurements; budget observations are reported, never hidden by timing assertions. */
final class W231BayCapacityTest {
    static final int WARMUP = 10, SAMPLES = 100;
    @TempDir Path temp;
    private static volatile Object sink;
    @BeforeAll static void bootstrap() { MinecraftBootstrap.initialize(); }

    @Test void measureBothOwnerDistributionsAtEveryRequestedPendingCount() throws Exception {
        var report = new LinkedHashMap<String, Object>();
        var os = (com.sun.management.OperatingSystemMXBean) ManagementFactory.getOperatingSystemMXBean();
        report.put("schema_version", 1);
        report.put("status", "IN_PROGRESS");
        report.put("environment", Map.of("kind", "A0_UNSTARTED_SERVER_NOT_REFERENCE_LOAD", "os", os.getName(),
                "java", System.getProperty("java.runtime.version"), "processors", os.getAvailableProcessors(),
                "processor", System.getenv().getOrDefault("PROCESSOR_IDENTIFIER", "unknown"),
                "max_heap_bytes", Runtime.getRuntime().maxMemory(), "physical_memory_bytes", os.getTotalMemorySize(),
                "jvm_arguments", ManagementFactory.getRuntimeMXBean().getInputArguments()));
        report.put("budgets_ms", Map.of("p95", 50, "p99", 100, "mean_reference_mspt", 25));
        report.put("warmup", WARMUP); report.put("samples", SAMPLES);
        report.put("fixture", "Paired owner distributions with the same rocket topology: 4, 4, 4 and 8 rockets at 8, 16, 32 and 64 pending. "
                + "Each 2048-block rocket has eight bay payloads and 2, 4, 8 or 8 INTENT receipts respectively; "
                + "one owner or one owner per rocket (4, 4, 4, 8 owners). "
                + "No retained backlog. Journal is ROCKET_TRANSFERS, one PREPARED record per rocket. "
                + "Coordinator ticks are real BayTransferRecovery.tick in REGISTRY_BLOCKED restart claims awaiting T acknowledgement. "
                + "No worlds, census, entities, physical saves, registry writes or full-server MSPT are measured. "
                + "Checked saves include IO completion; they are not main-thread tick timings. Loads use a warm OS cache.");
        var cases = new ArrayList<Map<String, Object>>(); report.put("cases", cases);
        Path output = Path.of("build/reports/w231/capacity.json"); Files.createDirectories(output.getParent());
        try {
            for (int count : List.of(8, 16, 32, 64)) {
                for (boolean multiple : List.of(false, true)) {
                    cases.add(measure(count, multiple)); SatelliteRegistryBenchmark.persist(output, report);
                }
            }
            report.put("status", "MEASURED_NOT_A_RELEASE_GATE");
        } finally { SatelliteRegistryBenchmark.persist(output, report); }
        assertEquals(8, cases.size());
    }

    private Map<String, Object> measure(int count, boolean multiple) throws Exception {
        Path directory = Files.createDirectory(temp.resolve(count + "-" + multiple));
        var result = new LinkedHashMap<String, Object>();
        int rockets = Math.max(4, count / 8), pendingPerRocket = count / rockets;
        result.put("pending", count); result.put("owners", multiple ? rockets : 1); result.put("rockets", rockets);
        result.put("pending_per_rocket", pendingPerRocket);
        var journal = new RocketTransferSavedData();
        var receipts = new ArrayList<BayReceipt>();
        for (int r = 0; r < rockets; r++) {
            var record = W231BayCapacityFixture.record(r, W231BayCapacityFixture.id(90_000 + (multiple ? r : 0)));
            journal.put(record);
            for (int b = 0; b < pendingPerRocket; b++) {
                receipts.add(W231BayCapacityFixture.receipt(record, b, r * (long) pendingPerRocket + b + 1));
            }
        }
        Path journalFile = directory.resolve(ManagedSavedDataType.ROCKET_TRANSFERS.fileName());
        // Above-cap distributions are historical-save measurements, not newly admitted workloads under R244.
        try (var harness = BayReceiptHarness.grandfathered(directory, receipts)) {
            harness.settle(); assertFalse(harness.store().holdsWork());
            assertEquals(count, harness.authority.pendingCount());
            var encodedJournal = journal.save(new CompoundTag());
            result.put("receipt_root_nbt_bytes", RocketNbtSize.uncompressedBytes(BayReceiptCodec.encode(harness.store().durable())));
            result.put("journal_root_nbt_bytes", RocketNbtSize.uncompressedBytes(encodedJournal));
            result.put("receipt_root_limit_bytes", BayReceiptCodec.ROOT_BYTES);
            result.put("journal_file_limit_bytes", ManagedSavedDataType.ROCKET_TRANSFERS.maxUncompressedBytes());
            var waitingJournal = RocketTransferSavedData.load(encodedJournal);
            var server = W231BayCapacityFixture.server(directory, waitingJournal);
            try (var coordinator = W231BayCapacityFixture.coordinator(server, harness.receipts.session(server), receipts)) {
                var metrics = new LinkedHashMap<String, List<double[]>>();
                for (String name : List.of("receipt_save_e2e", "receipt_request", "receipt_max_publication_tick", "receipt_load",
                        "journal_save_e2e", "journal_request", "journal_load", "coordinator_tick", "admission_check")) {
                    metrics.put(name, new ArrayList<>());
                }
                for (int sample = -WARMUP; sample < SAMPLES; sample++) {
                    int iteration = sample + WARMUP + 2;
                    var changed = receipts.get(0).withPhase(BayReceipt.Phase.INTENT, "NONE", iteration);
                    long start = System.nanoTime();
                    double[] request = timed(() -> assertTrue(harness.put(changed).queued()));
                    double maximumTick = 0;
                    for (int tick = 0; harness.store().holdsWork() && tick < AwaitedLedgerIo.TICK_BOUND; tick++) {
                        harness.io.awaitWrites();
                        maximumTick = Math.max(maximumTick, timed(() -> harness.store().tick(harness.tick++))[0]);
                    }
                    double elapsed = (System.nanoTime() - start) / 1e6;
                    assertFalse(harness.store().holdsWork()); assertTrue(harness.store().acknowledged(changed.op()));
                    add(metrics, sample, "receipt_save_e2e", elapsed); add(metrics, sample, "receipt_request", request);
                    add(metrics, sample, "receipt_max_publication_tick", maximumTick);
                    add(metrics, sample, "receipt_load", timed(() -> {
                        var loaded = BayReceiptSavedData.load(CheckedSavedDataFile.readPayload(harness.file,
                                ManagedSavedDataType.BAY_RECEIPTS).orElseThrow());
                        assertEquals(count, loaded.root().receipts().size()); sink = loaded;
                    }));
                    start = System.nanoTime();
                    double[] journalRequest = timed(() -> RocketJournalTestAccess.request(journal, journalFile,
                            CheckedSavedDataPolicy.atomic(ManagedSavedDataType.ROCKET_TRANSFERS)));
                    RocketJournalTestAccess.awaitWriter(journal);
                    add(metrics, sample, "journal_save_e2e", (System.nanoTime() - start) / 1e6);
                    add(metrics, sample, "journal_request", journalRequest);
                    assertTrue(journal.acknowledgedCurrent());
                    add(metrics, sample, "journal_load", timed(() -> {
                        var loaded = RocketTransferSavedData.load(CheckedSavedDataFile.readPayload(journalFile,
                                ManagedSavedDataType.ROCKET_TRANSFERS).orElseThrow());
                        assertTrue(loaded.operational()); assertEquals(rockets, loaded.entries().size()); sink = loaded;
                    }));
                    // One hundred genuine per-tick calls per IO sample; no timed reflection or empty-queue substitution.
                    for (int tick = 0; tick < 100; tick++) {
                        add(metrics, sample, "coordinator_tick", timed(coordinator::tick));
                    }
                    add(metrics, sample, "admission_check", timed(() -> sink = harness.authority.checkReserve(
                            W231BayCapacityFixture.id(99_000), W231BayCapacityFixture.id(99_001),
                            W231BayCapacityFixture.id(99_002), EscrowAdmissionAuthority.Consumer.PROPULSION)));
                }
                assertEquals(rockets, coordinator.claims()); assertEquals(rockets, coordinator.queued());
                assertTrue(coordinator.held()); assertEquals(count, harness.authority.pendingCount());
                var stats = new LinkedHashMap<String, Object>();
                metrics.forEach((key, samples) -> {
                    var summary = SatelliteRegistryBenchmark.statistics(samples);
                    summary.keySet().removeIf(name -> name.startsWith("cpu_")); // wall time only
                    stats.put(key, summary);
                });
                result.put("metrics", stats); result.put("coordinator_claims", coordinator.claims());
                result.put("receipt_file_bytes", Files.size(harness.file)); result.put("journal_file_bytes", Files.size(journalFile));
            } finally { waitingJournal.abort(); }
        } finally { journal.abort(); sink = null; }
        return result;
    }

    private static double[] timed(Runnable operation) {
        long start = System.nanoTime(); operation.run(); return new double[] {(System.nanoTime() - start) / 1e6, 0};
    }
    private static void add(Map<String, List<double[]>> metrics, int sample, String name, double value) {
        add(metrics, sample, name, new double[] {value, 0});
    }
    private static void add(Map<String, List<double[]>> metrics, int sample, String name, double[] value) {
        if (sample >= 0) { metrics.get(name).add(value); }
    }
}
