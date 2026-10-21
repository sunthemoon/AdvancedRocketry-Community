package io.github.sunthemoon.advancedrocketrycommunity.rocket.persistence;

import io.github.sunthemoon.advancedrocketrycommunity.persistence.CheckedJournalCheckpoint;
import io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.CheckedSavedDataPolicy;
import io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.ManagedSavedDataType;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.RocketLimits;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.*;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.stats.RocketStats;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.transaction.RocketRegion;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.transaction.RocketTransactionPhase;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.transaction.RocketTransactionRecord;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.transaction.RocketTransactionType;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.persistence.SatelliteRegistryBenchmark;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import java.lang.management.ManagementFactory;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;

/**
 * W128 (Root decision R49 Q6, R-040): the synchronous checked transaction-journal checkpoint that D0 can request, timed
 * with one ordinary assembly record and at the journal's checked bound (256 records within 64 MiB). Each sample follows
 * one record mutation and is one whole server-thread operation. A standalone diagnostic, not a unit test.
 */
public final class RocketTransactionCheckpointBenchmark {
    static final int TYPICAL_SAMPLES = 50, MAXIMUM_SAMPLES = 20;
    private static final ManagedSavedDataType TYPE = ManagedSavedDataType.ROCKET_TRANSACTIONS;
    private static final ResourceLocation OVERWORLD = ResourceLocation.tryParse("minecraft:overworld");

    private RocketTransactionCheckpointBenchmark() { }

    public static void main(String[] args) throws Exception {
        Path output = Path.of(args[0]), directory = Path.of(args[1]);
        if (Files.exists(directory)) { throw new IllegalArgumentException("Benchmark directory must be new"); }
        if (Runtime.getRuntime().maxMemory() > 4L * 1024 * 1024 * 1024) { throw new IllegalStateException("Heap exceeds Xmx4G"); }
        Files.createDirectory(directory);
        Path file = directory.resolve(TYPE.fileName());
        var report = new LinkedHashMap<String, Object>();
        report.put("status", "IN_PROGRESS"); report.put("source_commit", args.length > 2 ? args[2] : "unknown");
        report.put("scope", "RocketTransactionSavedData.saveChecked, the body of the server checkpoint D0 requests: encode, "
                + "per-record canonical digests, staging, force, readback and replacement. One journal with one 2,048-block "
                + "assembly record, then a fresh journal with 256 records at its 64 MiB checked bound. Each sample follows one "
                + "progress mutation, outside the timed interval.");
        report.put("max_heap_bytes", Runtime.getRuntime().maxMemory());
        try {
            MinecraftBootstrap.initialize();
            var results = new LinkedHashMap<String, Object>();
            results.put("one_assembly_record", measure(1, 0, TYPICAL_SAMPLES, file, report, "typical_fixture"));
            Files.deleteIfExists(file);
            results.put("checked_bound", measure(RocketLimits.MAX_ACTIVE_TRANSACTIONS, padding(), MAXIMUM_SAMPLES, file, report, "maximum_fixture"));
            report.put("results", results);
            boolean within = results.values().stream().allMatch(value -> Boolean.TRUE.equals(((Map<?, ?>) value).get("within")));
            report.put("status", within ? "WITHIN_BUDGET" : "STOP_RULE");
        } catch (Throwable failure) {
            report.put("status", "FAILED"); report.put("error", failure.toString()); SatelliteRegistryBenchmark.persist(output, report); throw failure;
        } finally {
            SatelliteRegistryBenchmark.persist(output, report);
            Files.deleteIfExists(file); Files.deleteIfExists(Path.of(file + ".arce-pending"));
            try (var left = Files.list(directory)) { for (Path path : left.toList()) { Files.deleteIfExists(path); } }
            Files.delete(directory);
        }
    }

    private static Map<String, Object> measure(int records, int padding, int samples, Path file,
            Map<String, Object> report, String fixtureKey) throws java.io.IOException {
        var data = new RocketTransactionSavedData();
        var bound = new ArrayList<RocketTransactionRecord>(); var journals = new ArrayList<io.github.sunthemoon.advancedrocketrycommunity.rocket.transaction.RocketTransactionJournal>();
        for (int i = 0; i < records; i++) {
            var snapshot = snapshot(padding, i);
            var record = new RocketTransactionRecord(new UUID(130, i + 1), RocketTransactionType.ASSEMBLY,
                    RocketTransactionPhase.SNAPSHOT_VALIDATED, snapshot.snapshotId(), snapshot.contentHash(), RocketRegion.fromSnapshot(snapshot), 0, null);
            var journal = data.journalFor(snapshot, new UUID(131, i + 1)); journal.write(record);
            bound.add(record); journals.add(journal);
        }
        int bytes = io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketNbtSize.uncompressedBytes(data.save(new CompoundTag()));
        report.put(fixtureKey, Map.of("records", records, "root_bytes", bytes, "bound_bytes", TYPE.maxUncompressedBytes(),
                "padding_bytes_per_record", padding));
        var policy = CheckedSavedDataPolicy.atomic(TYPE);
        var timings = new ArrayList<double[]>();
        for (int sample = 0; sample < samples; sample++) {
            int index = sample % records;
            var next = bound.get(index); next = new RocketTransactionRecord(next.transactionId(), next.type(), next.phase(), next.snapshotId(),
                    next.contentHash(), next.region(), next.progress() + 1, next.rocketEntityId());
            journals.get(index).write(next); bound.set(index, next);
            var threads = ManagementFactory.getThreadMXBean();
            long cpu = threads.getCurrentThreadCpuTime(), wall = System.nanoTime();
            var outcome = data.saveChecked(file, policy);
            timings.add(new double[] {(System.nanoTime() - wall) / 1e6, (threads.getCurrentThreadCpuTime() - cpu) / 1e6});
            if (outcome != CheckedJournalCheckpoint.Outcome.COMMITTED || !data.acknowledgedCurrent()) {
                throw new IllegalStateException("Checked transaction checkpoint was not acknowledged: " + outcome);
            }
        }
        var statistics = SatelliteRegistryBenchmark.statistics(timings);
        statistics = new LinkedHashMap<>(statistics); statistics.put("file_bytes", Files.size(file));
        return statistics;
    }

    /** The largest equal per-record padding whose 256 records fit the checked bound, found by bisection. */
    private static int padding() {
        int low = 0, high = RocketLimits.MAX_TOTAL_NBT_BYTES;
        long bound = TYPE.maxUncompressedBytes() - 64 * 1024;
        int empty = entryBytes(0);
        while (low < high) {
            int middle = (low + high + 1) >>> 1;
            boolean fits;
            try { fits = (long) entryBytes(middle) * RocketLimits.MAX_ACTIVE_TRANSACTIONS + empty <= bound; }
            catch (RocketSnapshotException tooLarge) { fits = false; }
            if (fits) { low = middle; } else { high = middle - 1; }
        }
        return low;
    }

    private static int entryBytes(int padding) {
        var data = new RocketTransactionSavedData(); var snapshot = snapshot(padding, 0);
        data.journalFor(snapshot, new UUID(131, 1)).write(new RocketTransactionRecord(new UUID(130, 1), RocketTransactionType.ASSEMBLY,
                RocketTransactionPhase.SNAPSHOT_VALIDATED, snapshot.snapshotId(), snapshot.contentHash(), RocketRegion.fromSnapshot(snapshot), 0, null));
        return io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketNbtSize.uncompressedBytes(data.save(new CompoundTag()));
    }

    /** RocketJournalBenchmark's 2,048-block snapshot, with its padding split over four block-entity payloads. */
    private static RocketStructureSnapshot snapshot(int padding, int index) {
        byte[] entropy = new byte[RocketLimits.MAX_BLOCK_ENTITY_NBT_BYTES]; new Random(20261021L + index).nextBytes(entropy);
        var blocks = new ArrayList<RocketBlock>();
        for (int i = 0; i < RocketLimits.MAX_BLOCKS; i++) {
            RocketBlockEntityPayload payload = null;
            if (i < 4 && padding != 0) {
                var tag = new CompoundTag(); tag.putByteArray("benchmark", Arrays.copyOf(entropy, padding / 4 + (i < padding % 4 ? 1 : 0)));
                payload = new RocketBlockEntityPayload(ResourceLocation.tryParse("advancedrocketrycommunity:benchmark"), tag);
            }
            blocks.add(new RocketBlock(new RocketPosition(i % 16, i / 256, (i / 16) % 16),
                    new RocketBlockState(ResourceLocation.tryParse("minecraft:iron_block"), Map.of()), payload));
        }
        return RocketStructureSnapshot.create(new UUID(132, index + 1), OVERWORLD, new RocketPosition(index * 64, 80, 0), blocks,
                List.of(new RocketPosition(0, 1, 0)), new RocketStats(RocketLimits.MAX_BLOCKS, 1_000, 2_000, 1_000, 1, 1, 1, padding == 0 ? 0 : 4), 0);
    }
}
