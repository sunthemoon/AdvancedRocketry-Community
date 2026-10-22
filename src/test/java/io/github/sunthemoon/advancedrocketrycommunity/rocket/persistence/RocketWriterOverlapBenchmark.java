package io.github.sunthemoon.advancedrocketrycommunity.rocket.persistence;

import io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.CheckedSavedDataFile;
import io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.CheckedSavedDataPolicy;
import io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.ManagedSavedDataType;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.RocketLimits;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketTransferPhase;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.persistence.RocketTransferSavedData;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.persistence.TransferWriterBenchmarkAccess;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketNbtSize;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.transaction.RocketRegion;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.transaction.RocketTransactionJournal;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.transaction.RocketTransactionPhase;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.transaction.RocketTransactionRecord;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.transaction.RocketTransactionType;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.persistence.SatelliteRegistryBenchmark;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.persistence.SatelliteRegistryResidentFixture;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.LockSupport;
import net.minecraft.server.dedicated.DedicatedServer;

/**
 * W129a B3a (ADR-074 item 13, design sections 5.4 and 6.1): at least 50 rounds in which the new transaction journal
 * writer and the existing transfer writer both write their whole roots at their bounds, overlapping on their two IO
 * threads, while W128's 16 MiB registry stays resident. The owner thread runs a monotonic 50 ms surrogate tick: every
 * tick records its intended deadline, start, end, duration and lateness, and a deadline that passes during work is
 * counted as missed instead of being diluted by an immediate catch-up tick. Some rounds take the readback path (the
 * committer replaces, then throws) and some definitely fail before replacement. Standalone, Xmx4G, heavy-JVM lock.
 */
public final class RocketWriterOverlapBenchmark {
    static final int ROUNDS = 50, SMOKE_ROUNDS = 13;
    /** {@code smoke}: small fixtures and 13 rounds, only to exercise every path; never budget evidence. */
    private static boolean smoke;
    static final long PERIOD_NANOS = TimeUnit.MILLISECONDS.toNanos(50);
    static final double MEAN_MS = 25, SPIKE_MS = 500;
    static final long RSS_BUDGET_BYTES = (long) (5.5 * 1024 * 1024 * 1024);
    private static final ManagedSavedDataType TRANSACTIONS = ManagedSavedDataType.ROCKET_TRANSACTIONS;
    private static final ManagedSavedDataType TRANSFERS = ManagedSavedDataType.ROCKET_TRANSFERS;

    private RocketWriterOverlapBenchmark() { }

    enum Kind { ATOMIC, READBACK, FAIL }

    public static void main(String[] args) throws Exception {
        Path output = Path.of(args[0]), directory = Path.of(args[1]);
        if (Files.exists(directory)) { throw new IllegalArgumentException("Benchmark directory must be new"); }
        if (Runtime.getRuntime().maxMemory() > 4L * 1024 * 1024 * 1024) { throw new IllegalStateException("Heap exceeds Xmx4G"); }
        smoke = args.length > 3 && "smoke".equals(args[3]);
        Files.createDirectory(directory);
        Path transactionFile = directory.resolve(TRANSACTIONS.fileName()), transferFile = directory.resolve(TRANSFERS.fileName());
        var report = new LinkedHashMap<String, Object>();
        report.put("status", "IN_PROGRESS"); report.put("source_commit", args.length > 2 ? args[2] : "unknown");
        report.put("scope", "One owner thread runs a monotonic 50 ms surrogate tick that polls both writers at tick START, "
                + "then starts each round's mutation and requests. Resident: W128's 16 MiB registry (loaded, not written), "
                + "the transaction journal with W128's 256 records at its 64 MiB bound, and the transfer journal with "
                + "W11B2's maximum fixture. Each round writes both whole roots; they overlap on their own IO threads. "
                + "Transaction rounds 4, 14, ... take the readback path and rounds 9, 19, ... fail before replacement; "
                + "transfer rounds 7, 17, ... take the readback path and rounds 12 and 37 fail once, then retry.");
        report.put("budgets", Map.of("mean_ms", MEAN_MS, "p95_ms", WriterBenchmarkEnvironment.P95_MS,
                "p99_ms", WriterBenchmarkEnvironment.P99_MS, "spike_ms", SPIKE_MS, "rss_bytes", RSS_BUDGET_BYTES,
                "heap", "Xmx4G, no OutOfMemoryError"));
        report.put("environment", WriterBenchmarkEnvironment.describe());
        report.put("mode", smoke ? "SMOKE_NOT_EVIDENCE" : "MEASUREMENT");
        try (var environment = new WriterBenchmarkEnvironment()) {
            MinecraftBootstrap.initialize();
            long baseline = WriterBenchmarkEnvironment.retainedAfterGc();
            var registry = smoke ? null : SatelliteRegistryResidentFixture.resident();
            DedicatedServer server = RocketTransactionWriterBenchmark.live();
            var transactions = new RocketTransactionSavedData(); transactions.bindServerForTests(server);
            var records = new ArrayList<RocketTransactionRecord>(); var journals = new ArrayList<RocketTransactionJournal>();
            int padding = smoke ? 0 : RocketTransactionCheckpointBenchmark.padding();
            for (int i = 0; i < (smoke ? 4 : RocketLimits.MAX_ACTIVE_TRANSACTIONS); i++) {
                var snapshot = RocketTransactionCheckpointBenchmark.snapshot(padding, i);
                var record = new RocketTransactionRecord(new UUID(130, i + 1), RocketTransactionType.ASSEMBLY,
                        RocketTransactionPhase.SNAPSHOT_VALIDATED, snapshot.snapshotId(), snapshot.contentHash(),
                        RocketRegion.fromSnapshot(snapshot), 0, null);
                var journal = transactions.journalFor(snapshot, new UUID(131, i + 1)); journal.write(record);
                records.add(record); journals.add(journal);
            }
            var transfers = smoke ? TransferWriterBenchmarkAccess.small(2) : TransferWriterBenchmarkAccess.maximum();
            var fixtures = new LinkedHashMap<String, Object>();
            fixtures.put("registry", registry == null ? Map.of("resident", false) : Map.of(
                    "root_bytes", SatelliteRegistryResidentFixture.rootBytes(registry),
                    "missions", SatelliteRegistryResidentFixture.missions(registry), "written", false));
            fixtures.put("transactions", Map.of("records", records.size(), "padding_bytes_per_record", padding,
                    "root_bytes", RocketNbtSize.uncompressedBytes(transactions.save(new net.minecraft.nbt.CompoundTag())),
                    "bound_bytes", TRANSACTIONS.maxUncompressedBytes()));
            fixtures.put("transfers", Map.of("records", transfers.entries().size(),
                    "root_bytes", TransferWriterBenchmarkAccess.rootBytes(transfers), "bound_bytes", TRANSFERS.maxUncompressedBytes()));
            report.put("fixtures", fixtures);
            long resident = WriterBenchmarkEnvironment.retainedAfterGc();
            report.put("retained_heap", new LinkedHashMap<>(Map.of("baseline_after_gc_bytes", baseline,
                    "resident_roots_after_gc_bytes", resident, "resident_roots_delta_bytes", resident - baseline)));
            report.put("working_set_before_rounds", WriterBenchmarkEnvironment.workingSet());
            SatelliteRegistryBenchmark.persist(output, report);

            // Baseline files for both roots, so that every measured round replaces an existing file.
            int tick = 0;
            RocketTransactionWriterBenchmark.at(server, tick);
            if (!transactions.requestWrite(transactionFile, CheckedSavedDataPolicy.atomic(TRANSACTIONS))
                    || !TransferWriterBenchmarkAccess.request(transfers, transferFile, CheckedSavedDataPolicy.atomic(TRANSFERS))) {
                throw new IllegalStateException("Baseline requests refused");
            }
            long deadline = System.nanoTime() + TimeUnit.MINUTES.toNanos(5);
            while (!transactions.acknowledgedCurrent() || !transfers.acknowledgedCurrent()) {
                if (System.nanoTime() > deadline) { throw new IllegalStateException("Baseline writes did not complete"); }
                Thread.sleep(5); RocketTransactionWriterBenchmark.at(server, ++tick);
                transactions.pollWriter(); transfers.pollWriter(tick);
            }

            var threads = (com.sun.management.ThreadMXBean) ManagementFactory.getThreadMXBean();
            long ownerId = Thread.currentThread().getId();
            long ownerAllocated = threads.getThreadAllocatedBytes(ownerId), ownerCpu = threads.getCurrentThreadCpuTime();
            var transactionsThreadBefore = WriterBenchmarkEnvironment.thread(RocketTransactionWriter.THREAD);
            var transfersThreadBefore = WriterBenchmarkEnvironment.thread("ARCE-rocket-journal");
            environment.resetPoolPeaks();
            int pauseStart = environment.pauseCount(); long gcStart = environment.gcMillis();
            var run = run(server, tick + 20, transactions, records, journals, transactionFile, transfers, transferFile);
            report.put("rounds", run.get("rounds")); report.put("ticks", run.get("ticks"));
            var results = new LinkedHashMap<String, Object>();
            results.put("tick_duration", run.get("duration")); results.put("tick_lateness", run.get("lateness"));
            results.put("ticks_measured", run.get("tick_count")); results.put("missed_deadlines", run.get("missed"));
            results.put("ticks_over_50_ms", run.get("over_50"));
            results.put("transaction_writer", run.get("transaction_writer"));
            results.put("transfer_writer", run.get("transfer_writer"));
            results.put("gc", environment.pauses(pauseStart));
            results.put("gc_collection_time_ms", environment.gcMillis() - gcStart);
            results.put("heap", environment.heap());
            results.put("owner_thread", Map.of("allocated_bytes", threads.getThreadAllocatedBytes(ownerId) - ownerAllocated,
                    "cpu_ms", (threads.getCurrentThreadCpuTime() - ownerCpu) / 1e6));
            results.put("transaction_io_thread_before_and_after", List.of(transactionsThreadBefore,
                    WriterBenchmarkEnvironment.thread(RocketTransactionWriter.THREAD)));
            results.put("transfer_io_thread_before_and_after", List.of(transfersThreadBefore,
                    WriterBenchmarkEnvironment.thread("ARCE-rocket-journal")));
            var workingSet = WriterBenchmarkEnvironment.workingSet();
            results.put("working_set_after_rounds", workingSet);
            results.put("retained_heap_after_plannedbytes", WriterBenchmarkEnvironment.retainedAfterGc());
            report.put("results", results);
            @SuppressWarnings("unchecked") var duration = (Map<String, Object>) run.get("duration");
            boolean rss = !"MEASURED".equals(workingSet.get("status"))
                    || ((Number) workingSet.get("peak_working_set_bytes")).longValue() <= RSS_BUDGET_BYTES;
            boolean within = (double) duration.get("mean_ms") <= MEAN_MS
                    && (double) duration.get("p95_ms") <= WriterBenchmarkEnvironment.P95_MS
                    && (double) duration.get("p99_ms") <= WriterBenchmarkEnvironment.P99_MS
                    && (double) duration.get("maximum_ms") <= SPIKE_MS && rss;
            report.put("rss_measured", "MEASURED".equals(workingSet.get("status")));
            report.put("status", smoke ? "SMOKE_COMPLETED" : within ? "WITHIN_BUDGET" : "STOP_RULE");
            if (registry != null) { report.put("registry_still_operational", registry.operational()); }
            transfers.stop();
        } catch (Throwable failure) {
            report.put("status", "FAILED"); report.put("error", failure.toString());
            SatelliteRegistryBenchmark.persist(output, report); throw failure;
        } finally {
            SatelliteRegistryBenchmark.persist(output, report);
            for (Path file : List.of(transactionFile, transferFile)) {
                Files.deleteIfExists(file); Files.deleteIfExists(Path.of(file + ".arce-pending"));
            }
            try (var left = Files.list(directory)) { for (Path path : left.toList()) { Files.deleteIfExists(path); } }
            Files.delete(directory);
        }
    }

    private static Map<String, Object> run(DedicatedServer server, int firstTick, RocketTransactionSavedData transactions,
            List<RocketTransactionRecord> records, List<RocketTransactionJournal> journals, Path transactionFile,
            RocketTransferSavedData transfers, Path transferFile) throws IOException {
        var ticks = new ArrayList<long[]>(); var rounds = new ArrayList<Map<String, Object>>();
        var transactionWall = new ArrayList<Double>(); var transactionCpu = new ArrayList<Double>();
        var transactionLatencyTicks = new ArrayList<Double>(); var transferWall = new ArrayList<Double>();
        var transferLatencyTicks = new ArrayList<Double>();
        long origin = System.nanoTime(), k = 0, missed = 0;
        int round = -1, transferAdvances = 0;
        boolean transactionWaiting = false, transferWaiting = false;
        long transactionCount = 0, transferCount = TransferWriterBenchmarkAccess.completedWriteCount(transfers);
        Map<String, Object> current = null;
        int planned = smoke ? SMOKE_ROUNDS : ROUNDS;
        while (round < planned) {
            long intended = origin + k * PERIOD_NANOS;
            while (System.nanoTime() < intended) { LockSupport.parkNanos(intended - System.nanoTime()); }
            long start = System.nanoTime();
            int tick = firstTick + (int) k;
            RocketTransactionWriterBenchmark.at(server, tick);
            transactions.pollWriter();
            transfers.pollWriter(tick);
            if (transactionWaiting && transactions.completedWriteCount() > transactionCount) {
                transactionWaiting = false;
                current.put("transaction_published_tick", tick); current.put("transaction_outcome", transactions.writeOutcome().name());
                transactionWall.add(transactions.lastWriterNanos() / 1e6); transactionCpu.add(transactions.lastWriterCpuNanos() / 1e6);
                transactionLatencyTicks.add((double) (tick - (int) current.get("request_tick")));
            }
            long transfersNow = TransferWriterBenchmarkAccess.completedWriteCount(transfers);
            if (transferWaiting && transfersNow > transferCount) {
                transferWall.add(TransferWriterBenchmarkAccess.lastWriterNanos(transfers) / 1e6); transferCount = transfersNow;
                if (transfers.acknowledgedCurrent()) {
                    transferWaiting = false; current.put("transfer_acknowledged_tick", tick);
                    transferLatencyTicks.add((double) (tick - (int) current.get("request_tick")));
                }
            }
            if (!transactionWaiting && !transferWaiting) {
                if (current != null) { rounds.add(current); }
                round++;
                if (round < planned) {
                    Kind transactionKind = round % 10 == 4 ? Kind.READBACK : round % 10 == 9 ? Kind.FAIL : Kind.ATOMIC;
                    Kind transferKind = round % 10 == 7 ? Kind.READBACK : round == 12 || round == 37 ? Kind.FAIL : Kind.ATOMIC;
                    int index = round % records.size();
                    var next = records.get(index);
                    next = new RocketTransactionRecord(next.transactionId(), next.type(), next.phase(), next.snapshotId(),
                            next.contentHash(), next.region(), next.progress() + 1, next.rocketEntityId());
                    journals.get(index).write(next); records.set(index, next);
                    transactionCount = transactions.completedWriteCount();
                    if (!transactions.requestWrite(transactionFile, policy(TRANSACTIONS, transactionKind))) {
                        throw new IllegalStateException("Transaction request refused");
                    }
                    if (transferKind == Kind.FAIL) {
                        // A definite failure needs content that differs from the file; the retry then commits it.
                        var record = transfers.entries().get(0);
                        transfers.put(transferAdvances++ == 0 ? record.destinationSpawned(new UUID(9, 1))
                                : record.advance(RocketTransferPhase.PASSENGERS_TRANSFERRED));
                    } else {
                        transfers.setDirty();
                    }
                    if (!TransferWriterBenchmarkAccess.request(transfers, transferFile, policy(TRANSFERS, transferKind))) {
                        throw new IllegalStateException("Transfer request refused");
                    }
                    transactionWaiting = transferWaiting = true;
                    current = new LinkedHashMap<>();
                    current.put("round", round); current.put("request_tick", tick);
                    current.put("transaction_kind", transactionKind.name()); current.put("transfer_kind", transferKind.name());
                    current.put("transaction_started_at_request", transactions.writerInFlight());
                }
            }
            long end = System.nanoTime();
            long next = k + 1, skipped = 0;
            while (origin + next * PERIOD_NANOS < end) { next++; skipped++; }
            ticks.add(new long[] {k, intended - origin, start - origin, end - origin, skipped});
            missed += skipped; k = next;
            if (System.nanoTime() - origin > TimeUnit.MINUTES.toNanos(60)) { throw new IllegalStateException("Rounds exceeded one hour"); }
        }
        var result = new LinkedHashMap<String, Object>();
        var durations = ticks.stream().map(t -> (t[3] - t[2]) / 1e6).toList();
        var lateness = ticks.stream().map(t -> (t[2] - t[1]) / 1e6).toList();
        result.put("rounds", rounds);
        result.put("ticks", Map.of("columns", List.of("k", "deadline_ns", "start_ns", "end_ns", "missed_before_next"), "rows", ticks));
        result.put("tick_count", ticks.size()); result.put("missed", missed);
        result.put("over_50", durations.stream().filter(d -> d > 50).count());
        result.put("duration", RocketTransactionWriterBenchmark.plain(durations));
        result.put("lateness", RocketTransactionWriterBenchmark.plain(lateness));
        result.put("transaction_writer", Map.of("wall", RocketTransactionWriterBenchmark.plain(transactionWall),
                "cpu", RocketTransactionWriterBenchmark.plain(transactionCpu),
                "request_to_publication_ticks", RocketTransactionWriterBenchmark.plain(transactionLatencyTicks)));
        result.put("transfer_writer", Map.of("wall", RocketTransactionWriterBenchmark.plain(transferWall),
                "request_to_acknowledgement_ticks", RocketTransactionWriterBenchmark.plain(transferLatencyTicks)));
        return result;
    }

    private static CheckedSavedDataPolicy policy(ManagedSavedDataType type, Kind kind) {
        return switch (kind) {
            case ATOMIC -> CheckedSavedDataPolicy.atomic(type);
            case READBACK -> new CheckedSavedDataPolicy(type, (staged, target) -> {
                CheckedSavedDataFile.atomicMove(staged, target); throw new IOException("W129a B3a readback path");
            });
            case FAIL -> {
                var calls = new AtomicInteger();
                yield new CheckedSavedDataPolicy(type, (staged, target) -> {
                    if (calls.getAndIncrement() == 0) { throw new IOException("W129a B3a definite failure"); }
                    CheckedSavedDataFile.atomicMove(staged, target);
                });
            }
        };
    }
}
