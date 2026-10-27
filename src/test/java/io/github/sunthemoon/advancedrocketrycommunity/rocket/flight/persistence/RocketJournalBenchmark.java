package io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.persistence;

import com.google.gson.GsonBuilder;
import io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.CheckedSavedDataPolicy;
import io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.ManagedSavedDataType;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.RocketLimits;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.*;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.*;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.persistence.RocketSnapshotNbtCodec;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.stats.RocketStats;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import java.lang.management.ManagementFactory;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.resources.ResourceLocation;

/** Standalone cost diagnostic, not a unit test or reference-server performance Gate. */
public final class RocketJournalBenchmark {
    private RocketJournalBenchmark() { }
    public static void main(String[] args) throws Exception {
        Path output = Path.of(args[0]), directory = Path.of(args[1]);
        if (Files.exists(directory)) { throw new IllegalArgumentException("Benchmark directory must be new"); }
        Files.createDirectory(directory);
        if (Runtime.getRuntime().maxMemory() > 4L * 1024 * 1024 * 1024) { throw new IllegalStateException("Heap exceeds Xmx4G"); }
        var report = new LinkedHashMap<String, Object>();
        report.put("status", "IN_PROGRESS"); report.put("source_commit", args[2]); report.put("jar_sha256", args[3]);
        report.put("scope", "Isolated candidate validation plus owner capture/publish, writer and elapsed costs. Candidate put/remove costs are measured separately and included in combined owner time; immutable fixture construction is outside the intervals. No players, reference world load, native watchdog or soak. In-place comparator is the current vanilla NbtIo algorithm, not a pinned old binary.");
        report.put("max_heap_bytes", Runtime.getRuntime().maxMemory());
        var peak = new AtomicLong(); var running = new AtomicBoolean(true);
        var writers = new ArrayList<RocketTransferSavedData>();
        Thread sampler = new Thread(() -> {
            while (running.get()) {
                peak.accumulateAndGet(ManagementFactory.getMemoryMXBean().getHeapMemoryUsage().getUsed(), Math::max);
                try { Thread.sleep(1); } catch (InterruptedException stop) { return; }
            }
        }, "journal-heap-sampler"); sampler.setDaemon(true); sampler.start();
        try {
            MinecraftBootstrap.initialize();
            var cycle = new RocketTransferSavedData(); writers.add(cycle); var small = snapshot(0);
            var records = new ArrayList<RocketTransferRecord>(); var samples = new ArrayList<Timing>();
            Path checked = directory.resolve(ManagedSavedDataType.ROCKET_TRANSFERS.fileName());
            for (int i = 0; i < 8; i++) {
                var record = record(small, i); records.add(record);
                long mutation = System.nanoTime(); cycle.put(record);
                samples.add(write(cycle, checked, System.nanoTime() - mutation));
            }
            for (var phase : List.of(RocketTransferPhase.DESTINATION_SPAWNED, RocketTransferPhase.PASSENGERS_TRANSFERRED,
                    RocketTransferPhase.SOURCE_REMOVED, RocketTransferPhase.COMMITTED)) {
                for (int i = 0; i < records.size(); i++) {
                    var record = records.get(i); record = phase == RocketTransferPhase.DESTINATION_SPAWNED
                            ? record.destinationSpawned(new UUID(9, i + 1)) : record.advance(phase);
                    records.set(i, record);
                    long mutation = System.nanoTime(); cycle.put(record);
                    samples.add(write(cycle, checked, System.nanoTime() - mutation));
                }
            }
            for (var record : records) {
                long mutation = System.nanoTime(); cycle.remove(record.transferId());
                samples.add(write(cycle, checked, System.nanoTime() - mutation));
            }
            report.put("eight_transfer_cycle", Map.of(
                    "main", statistics(samples.stream().map(Timing::mainMs).toList()),
                    "candidate_validation", statistics(samples.stream().map(Timing::mutationMs).toList()),
                    "capture_publication", statistics(samples.stream().map(Timing::captureMs).toList()),
                    "writer", statistics(samples.stream().map(Timing::writerMs).toList()),
                    "elapsed", statistics(samples.stream().map(Timing::elapsedMs).toList())));
            if (samples.stream().mapToDouble(Timing::mainMs).average().orElseThrow() > 25
                    || percentile(samples.stream().map(Timing::mainMs).sorted().toList(), .95) > 50
                    || percentile(samples.stream().map(Timing::mainMs).sorted().toList(), .99) > 100) {
                throw new IllegalStateException("Candidate validation plus owner capture/publish exceeds unchanged docs/17 budgets");
            }
            cycle.stop(); persist(output, report);

            // Binary search the exact legal snapshot byte ceiling, without shrinking any production limit.
            int low = 0, high = RocketLimits.MAX_TOTAL_NBT_BYTES;
            while (low < high) {
                int middle = (low + high + 1) / 2;
                try {
                    var candidate = snapshot(middle); RocketSnapshotNbtCodec.encode(candidate);
                    RocketSnapshotNbtCodec.encode(candidate.relocated(new UUID(3, 1), RocketFlightPlanner.MOON.dimensionId(), new RocketPosition(0, 80, 0), 160));
                    low = middle;
                }
                catch (RocketSnapshotException tooLarge) { high = middle - 1; }
            }
            var maximum = snapshot(low); int bytes = RocketNbtSize.uncompressedBytes(RocketSnapshotNbtCodec.encode(maximum));
            int destinationBytes = RocketNbtSize.uncompressedBytes(RocketSnapshotNbtCodec.encode(maximum.relocated(new UUID(3, 1),
                    RocketFlightPlanner.MOON.dimensionId(), new RocketPosition(0, 80, 0), 160)));
            if (destinationBytes != RocketLimits.MAX_TOTAL_NBT_BYTES) { throw new IllegalStateException("Maximum destination fixture does not reach exact snapshot bound"); }
            var full = new RocketTransferSavedData();
            writers.add(full);
            var maximumAdmissions = new ArrayList<Double>();
            for (int i = 0; i < RocketFlightLimits.MAX_ACTIVE_TRANSFERS; i++) {
                var candidate = record(maximum, i);
                long mutation = System.nanoTime(); full.put(candidate);
                maximumAdmissions.add((System.nanoTime() - mutation) / 1_000_000D);
            }
            report.put("maximum_candidate_validation", statistics(maximumAdmissions));
            report.put("maximum_fixture", Map.of("records", full.entries().size(), "blocks_per_snapshot", maximum.blocks().size(),
                    "source_snapshot_bytes", bytes, "destination_snapshot_bytes", destinationBytes,
                    "journal_bytes", RocketNbtSize.uncompressedBytes(full.save(new CompoundTag())),
                    "journal_limit_bytes", RocketFlightLimits.MAX_TRANSFER_JOURNAL_NBT_BYTES)); persist(output, report);
            // Encode each case inside its timed interval; checked includes digests, stage, force and decoded readback.
            long start = System.nanoTime(); CompoundTag outer = new CompoundTag(); outer.put("data", full.save(new CompoundTag()));
            double vanillaCapture = (System.nanoTime() - start) / 1_000_000D;
            report.put("maximum_in_place_capture_owner_ms", vanillaCapture);
            NbtIo.writeCompressed(outer, directory.resolve("in-place.dat").toFile());
            report.put("maximum_in_place_owner_ms", (System.nanoTime() - start) / 1_000_000D); persist(output, report);
            Timing maximumWrite = write(full, checked, 0);
            report.put("maximum_checked_capture_publish_owner_ms", maximumWrite.mainMs());
            report.put("maximum_checked_writer_ms", maximumWrite.writerMs());
            report.put("maximum_checked_elapsed_ms", maximumWrite.elapsedMs());
            if (maximumWrite.mainMs() > vanillaCapture) { throw new IllegalStateException("Maximum capture exceeds vanilla capture cost"); }
            report.put("maximum_checked_acknowledged", full.acknowledgedCurrent());
            full.stop();
            report.put("status", "MEASURED_WITH_REFERENCE_LOAD_GAPS");
        } catch (Throwable failure) {
            report.put("status", "STOP_RULE"); report.put("error", failure.toString()); throw failure;
        } finally {
            writers.forEach(RocketTransferSavedData::stop);
            running.set(false); sampler.interrupt(); sampler.join(); report.put("sampled_peak_heap_bytes", peak.get()); persist(output, report);
            // These three files belong to this fresh diagnostic directory. Never remove source worlds or evidence.
            Files.deleteIfExists(directory.resolve(ManagedSavedDataType.ROCKET_TRANSFERS.fileName()));
            Files.deleteIfExists(directory.resolve(ManagedSavedDataType.ROCKET_TRANSFERS.fileName() + ".arce-pending"));
            Files.deleteIfExists(directory.resolve("in-place.dat")); Files.delete(directory);
        }
    }
    private static void persist(Path output, Map<String, Object> report) throws Exception {
        Files.writeString(output, new GsonBuilder().setPrettyPrinting().create().toJson(report));
    }
    private static Map<String, Object> statistics(List<Double> raw) {
        var sorted = raw.stream().sorted().toList();
        return Map.of("ms_samples", raw, "writes", raw.size(), "mean_ms", raw.stream().mapToDouble(Double::doubleValue).average().orElseThrow(),
                "p50_ms", percentile(sorted, .50), "p95_ms", percentile(sorted, .95), "p99_ms", percentile(sorted, .99), "maximum_ms", sorted.get(sorted.size() - 1));
    }
    private static double percentile(List<Double> sorted, double p) { return sorted.get((int) Math.ceil(p * sorted.size()) - 1); }
    private record Timing(double mutationMs, double captureMs, double writerMs, double elapsedMs) {
        double mainMs() { return mutationMs + captureMs; }
    }
    private static Timing write(RocketTransferSavedData data, Path file, long mutationNanos) throws InterruptedException {
        long start = System.nanoTime();
        if (!data.requestWrite(file, CheckedSavedDataPolicy.atomic(ManagedSavedDataType.ROCKET_TRANSFERS))) {
            throw new IllegalStateException("Writer rejected the reference cycle");
        }
        long main = System.nanoTime() - start, tick = 0;
        // Pump only owner publication. Sleep/wait and IO are explicitly not owner execution costs.
        while (!data.acknowledgedCurrent()) {
            long poll = System.nanoTime(); data.pollWriter(tick++); main += System.nanoTime() - poll;
            if (System.nanoTime() - start > 30_000_000_000L) { throw new IllegalStateException("Journal benchmark write did not complete"); }
            if (!data.acknowledgedCurrent()) { Thread.sleep(1); }
        }
        return new Timing(mutationNanos / 1_000_000D, main / 1_000_000D, data.lastWriterNanos() / 1_000_000D,
                (System.nanoTime() - start + mutationNanos) / 1_000_000D);
    }
    static RocketStructureSnapshot snapshot(int padding) {
        byte[] entropy = new byte[RocketLimits.MAX_BLOCK_ENTITY_NBT_BYTES]; new Random(20261013).nextBytes(entropy);
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
        return RocketStructureSnapshot.create(new UUID(1, 1), RocketFlightPlanner.EARTH.dimensionId(), new RocketPosition(0, 80, 0), blocks,
                List.of(new RocketPosition(0, 1, 0)), new RocketStats(RocketLimits.MAX_BLOCKS, 1_000, 2_000, 1_000, 1, 1, 1, padding == 0 ? 0 : 4), 0);
    }
    static RocketTransferRecord record(RocketStructureSnapshot source, int i) {
        // Disjoint source and landing regions; no relaxed journal authority-conflict check.
        source = source.relocated(new UUID(2, i + 1), RocketFlightPlanner.EARTH.dimensionId(), new RocketPosition(i * 64, 80, 0), 0);
        var destination = source.relocated(new UUID(3, i + 1), RocketFlightPlanner.MOON.dimensionId(), new RocketPosition(i * 64, 80, 0), 160);
        UUID transfer = new UUID(4, i + 1), logical = new UUID(5, i + 1);
        var fuel = RocketFuelState.empty(1_000).fill(1_000).state();
        var plan = new RocketFlightPlan(3, transfer, RocketFlightPlanner.EARTH.bodyId(), RocketFlightPlanner.MOON.bodyId(),
                RocketFlightPlanner.EARTH.dimensionId(), RocketFlightPlanner.MOON.dimensionId(), 500, 0);
        var flight = RocketFlightData.initial(logical, 1_000, 1, RocketFlightPlanner.EARTH.bodyId(), RocketFlightPlanner.EARTH.dimensionId(), source.sourceOrigin(), 0)
                .withFuel(fuel, 0).withPlan(plan).startCountdown(0).completeCountdown(60).beginTransit(transfer, 140);
        var arrival = flight.arriveAtDestination(fuel.debit(plan).state(), RocketFlightPlanner.MOON.bodyId(), RocketFlightPlanner.MOON.dimensionId(), destination.sourceOrigin(), 160);
        return RocketTransferRecord.create(transfer, logical, new UUID(6, i + 1), new UUID(7, i + 1), source, destination, flight, arrival, 500, 0);
    }
}
