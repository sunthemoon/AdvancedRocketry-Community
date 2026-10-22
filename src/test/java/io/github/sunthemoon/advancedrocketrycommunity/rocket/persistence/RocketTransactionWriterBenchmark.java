package io.github.sunthemoon.advancedrocketrycommunity.rocket.persistence;

import io.github.sunthemoon.advancedrocketrycommunity.persistence.CheckedJournalCheckpoint;
import io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.CheckedSavedDataPolicy;
import io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.ManagedSavedDataType;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.RocketLimits;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketNbtSize;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.transaction.RocketRegion;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.transaction.RocketTransactionJournal;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.transaction.RocketTransactionPhase;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.transaction.RocketTransactionRecord;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.transaction.RocketTransactionType;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.persistence.SatelliteRegistryBenchmark;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.UnstartedServer;
import java.io.DataOutputStream;
import java.io.OutputStream;
import java.lang.management.ManagementFactory;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.DigestOutputStream;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.dedicated.DedicatedServer;

/**
 * W129a B2 (ADR-074 item 13, design section 6.1): the server-thread cost of the off-thread transaction journal writer.
 * Each sample makes one record mutation (untimed), then times the request (capture and IO start) on one tick and the
 * publication on a later tick, each a whole server-thread operation held to docs/17's P95 50 ms and P99 100 ms. The
 * fixtures are W128's: one 2,048-block assembly record, and 256 records at the 64 MiB checked bound. Writer time and
 * peak heap are reported beside them. A standalone diagnostic run with Xmx4G under the heavy-JVM lock.
 */
public final class RocketTransactionWriterBenchmark {
    static final int SAMPLES = 50;
    /** {@code smoke}: three samples on small fixtures, only to exercise the code path; never budget evidence. */
    private static boolean smoke;
    private static final ManagedSavedDataType TYPE = ManagedSavedDataType.ROCKET_TRANSACTIONS;

    private RocketTransactionWriterBenchmark() { }

    public static void main(String[] args) throws Exception {
        Path output = Path.of(args[0]), directory = Path.of(args[1]);
        if (Files.exists(directory)) { throw new IllegalArgumentException("Benchmark directory must be new"); }
        if (Runtime.getRuntime().maxMemory() > 4L * 1024 * 1024 * 1024) { throw new IllegalStateException("Heap exceeds Xmx4G"); }
        smoke = args.length > 3 && "smoke".equals(args[3]);
        Files.createDirectory(directory);
        Path file = directory.resolve(TYPE.fileName());
        var report = new LinkedHashMap<String, Object>();
        report.put("status", "IN_PROGRESS"); report.put("source_commit", args.length > 2 ? args[2] : "unknown");
        report.put("scope", "RocketTransactionSavedData with its off-thread writer, bound to an unstarted dedicated server "
                + "that supplies the tick. Timed on the calling (server) thread: requestWrite (capture and IO start) and "
                + "pollWriter (publication), on separate ticks. The wait for the IO thread is not server-thread work. "
                + "Writer wall and CPU time come from the IO thread. One journal with one 2,048-block assembly record, "
                + "then a fresh journal with W128's 256 records at the 64 MiB bound.");
        report.put("budgets_ms", Map.of("p95", WriterBenchmarkEnvironment.P95_MS, "p99", WriterBenchmarkEnvironment.P99_MS));
        report.put("environment", WriterBenchmarkEnvironment.describe());
        report.put("mode", smoke ? "SMOKE_NOT_EVIDENCE" : "MEASUREMENT");
        try (var environment = new WriterBenchmarkEnvironment()) {
            MinecraftBootstrap.initialize();
            var results = new LinkedHashMap<String, Object>();
            results.put("one_assembly_record", measure(1, 0, file, report, "typical_fixture", environment));
            Files.deleteIfExists(file);
            results.put("checked_bound", smoke ? measure(4, 0, file, report, "maximum_fixture", environment)
                    : measure(RocketLimits.MAX_ACTIVE_TRANSACTIONS, RocketTransactionCheckpointBenchmark.padding(), file,
                    report, "maximum_fixture", environment));
            report.put("results", results);
            report.put("working_set", WriterBenchmarkEnvironment.workingSet());
            boolean within = results.values().stream().allMatch(value -> Boolean.TRUE.equals(((Map<?, ?>) value).get("within")));
            report.put("status", smoke ? "SMOKE_COMPLETED" : within ? "WITHIN_BUDGET" : "STOP_RULE");
        } catch (Throwable failure) {
            report.put("status", "FAILED"); report.put("error", failure.toString());
            SatelliteRegistryBenchmark.persist(output, report); throw failure;
        } finally {
            SatelliteRegistryBenchmark.persist(output, report);
            Files.deleteIfExists(file); Files.deleteIfExists(Path.of(file + ".arce-pending"));
            try (var left = Files.list(directory)) { for (Path path : left.toList()) { Files.deleteIfExists(path); } }
            Files.delete(directory);
        }
    }

    private static Map<String, Object> measure(int records, int padding, Path file, Map<String, Object> report,
            String fixtureKey, WriterBenchmarkEnvironment environment) throws Exception {
        DedicatedServer server = live();
        var data = new RocketTransactionSavedData(); data.bindServerForTests(server);
        var bound = new ArrayList<RocketTransactionRecord>(); var journals = new ArrayList<RocketTransactionJournal>();
        for (int i = 0; i < records; i++) {
            var snapshot = RocketTransactionCheckpointBenchmark.snapshot(padding, i);
            var record = new RocketTransactionRecord(new UUID(130, i + 1), RocketTransactionType.ASSEMBLY,
                    RocketTransactionPhase.SNAPSHOT_VALIDATED, snapshot.snapshotId(), snapshot.contentHash(),
                    RocketRegion.fromSnapshot(snapshot), 0, null);
            var journal = data.journalFor(snapshot, new UUID(131, i + 1)); journal.write(record);
            bound.add(record); journals.add(journal);
        }
        String synchronous = digest(data.save(new CompoundTag()));
        String captured = digest(RocketTransactionWriter.encode(data.captureForTests().entries(), new CompoundTag()));
        if (!synchronous.equals(captured)) { throw new IllegalStateException("Capture encoding differs from save(CompoundTag)"); }
        report.put(fixtureKey, Map.of("records", records, "root_bytes", RocketNbtSize.uncompressedBytes(data.save(new CompoundTag())),
                "bound_bytes", TYPE.maxUncompressedBytes(), "padding_bytes_per_record", padding,
                "capture_encoding_sha256_and_bytes", captured, "byte_identical_to_save", true));
        var policy = CheckedSavedDataPolicy.atomic(TYPE);
        var requests = new ArrayList<double[]>(); var publications = new ArrayList<double[]>(); var combined = new ArrayList<double[]>();
        var writerWall = new ArrayList<Double>(); var writerCpu = new ArrayList<Double>();
        environment.resetPoolPeaks();
        int tick = 0;
        for (int sample = 0; sample < (smoke ? 3 : SAMPLES); sample++) {
            int index = sample % records;
            var next = bound.get(index);
            next = new RocketTransactionRecord(next.transactionId(), next.type(), next.phase(), next.snapshotId(),
                    next.contentHash(), next.region(), next.progress() + 1, next.rocketEntityId());
            journals.get(index).write(next); bound.set(index, next);
            tick += 20; at(server, tick);
            double[] request = timed(() -> {
                if (!data.requestWrite(file, policy)) { throw new IllegalStateException("Request refused"); }
            });
            if (!data.writerInFlight()) { throw new IllegalStateException("The request did not start at once"); }
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(120);
            while (!data.writerCompletionReady()) {
                if (System.nanoTime() > deadline) { throw new IllegalStateException("The IO thread did not complete"); }
                Thread.sleep(1);
            }
            at(server, ++tick);
            double[] publication = timed(data::pollWriter);
            if (data.writeOutcome() != CheckedJournalCheckpoint.Outcome.COMMITTED || !data.acknowledgedCurrent()) {
                throw new IllegalStateException("The checked transaction write was not acknowledged: " + data.writeOutcome());
            }
            requests.add(request); publications.add(publication);
            combined.add(new double[] {request[0] + publication[0], request[1] + publication[1]});
            writerWall.add(data.lastWriterNanos() / 1e6); writerCpu.add(data.lastWriterCpuNanos() / 1e6);
        }
        var result = new LinkedHashMap<String, Object>();
        var requestStats = SatelliteRegistryBenchmark.statistics(requests);
        var publicationStats = SatelliteRegistryBenchmark.statistics(publications);
        result.put("request", requestStats);
        result.put("publication", publicationStats);
        result.put("request_plus_publication", SatelliteRegistryBenchmark.statistics(combined));
        result.put("writer_wall", plain(writerWall)); result.put("writer_cpu", plain(writerCpu));
        result.put("heap", environment.heap());
        result.put("file_bytes", Files.size(file));
        result.put("within", Boolean.TRUE.equals(requestStats.get("within")) && Boolean.TRUE.equals(publicationStats.get("within")));
        return result;
    }

    static DedicatedServer live() {
        DedicatedServer server = UnstartedServer.owned();
        UnstartedServer.set(MinecraftServer.class, server, "running", true);
        return server;
    }

    static void at(MinecraftServer server, int tick) { UnstartedServer.set(MinecraftServer.class, server, "tickCount", tick); }

    /** {wall ms, thread CPU ms} of one call on this thread. */
    static double[] timed(Runnable operation) {
        var threads = ManagementFactory.getThreadMXBean();
        long cpu = threads.getCurrentThreadCpuTime(), wall = System.nanoTime();
        operation.run();
        return new double[] {(System.nanoTime() - wall) / 1e6, (threads.getCurrentThreadCpuTime() - cpu) / 1e6};
    }

    static Map<String, Object> plain(List<Double> raw) {
        var sorted = raw.stream().sorted().toList();
        var result = new LinkedHashMap<String, Object>();
        result.put("samples", sorted.size()); result.put("ms", raw);
        result.put("mean_ms", raw.stream().mapToDouble(Double::doubleValue).average().orElse(0));
        result.put("p50_ms", SatelliteRegistryBenchmark.percentile(sorted, .50));
        result.put("p95_ms", SatelliteRegistryBenchmark.percentile(sorted, .95));
        result.put("p99_ms", SatelliteRegistryBenchmark.percentile(sorted, .99));
        result.put("maximum_ms", sorted.get(sorted.size() - 1));
        return result;
    }

    /** SHA-256 and length of the uncompressed NBT bytes, streamed. */
    static String digest(CompoundTag tag) throws Exception {
        var sha = MessageDigest.getInstance("SHA-256");
        try (var out = new DataOutputStream(new DigestOutputStream(OutputStream.nullOutputStream(), sha))) {
            NbtIo.write(tag, out);
            return HexFormat.of().formatHex(sha.digest()) + ":" + Integer.toUnsignedLong(out.size());
        }
    }
}
