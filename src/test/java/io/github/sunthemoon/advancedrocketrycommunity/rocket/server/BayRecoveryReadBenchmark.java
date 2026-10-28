package io.github.sunthemoon.advancedrocketrycommunity.rocket.server;

import io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.AnvilRecordReader;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFlightLimits;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.persistence.SatelliteRegistryBenchmark;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.storage.RegionFile;

/** Development-host diagnostic: loaded-index census and the actual bounded Anvil reader, not reference-server MSPT. */
public final class BayRecoveryReadBenchmark {
    private BayRecoveryReadBenchmark() { }

    public static void main(String[] args) throws Exception {
        MinecraftBootstrap.initialize();
        var fixture = new BayCopyCensusTest.Fixture(); fixture.add(fixture.before.sourceSnapshot(), BayConsumptionFixtures.SOURCE);
        for (int i = 0; i < RocketFlightLimits.MAX_TRANSFER_ENTITY_MATCHES - 2; i++) {
            fixture.add(i % 2 == 0 ? fixture.before.sourceSnapshot() : fixture.before.destinationSnapshot(), new UUID(185, i));
        }
        var censusTimes = new ArrayList<Double>(); var readTimes = new ArrayList<Double>(); var fullTimes = new ArrayList<Double>();
        var frozen = new CompoundTag(); frozen.putUUID("UUID", BayConsumptionFixtures.SOURCE); frozen.putString("id", "probe:rocket");
        var data = new CompoundTag(); data.putUUID("assembly_transaction_id", BayConsumptionFixtures.LOGICAL);
        frozen.put(RocketRetirementProof.DATA, data);
        var directory = Files.createTempDirectory("arce-w185-read-");
        int slots = 2 * RocketFlightLimits.MAX_LANDING_CHUNKS;
        int decoded = 0, compressed = 0;
        try {
            // Thirty-two distinct actual Anvil slots. The large root is unrelated data that the bounded reader must still decode.
            byte[] noise = new byte[524_288]; new java.util.Random(185).nextBytes(noise);
            try (var region = new RegionFile(directory.resolve("r.0.0.mca"), directory, false)) {
                for (int i = 0; i < slots; i++) {
                    var root = i == 0 ? BaySlotProofsTest.root(frozen) : BaySlotProofsTest.root();
                    root.putByteArray("noise", noise); root.putByteArray("padding", new byte[AnvilRecordReader.DECODED_BYTES - noise.length - 1_024]);
                    try (var output = region.getChunkDataOutputStream(new ChunkPos(i, 0))) { NbtIo.write(root, output); }
                }
                region.flush();
            }
            for (int sample = 0; sample < 55; sample++) {
                long begin = System.nanoTime();
                var scan = fixture.scan();
                double census = (System.nanoTime() - begin) / 1e6;
                if (scan.refusal() != null || scan.copies().size() != 62) { throw new IllegalStateException("Census fixture changed"); }
                if (sample >= 5) { censusTimes.add(census); }
                begin = System.nanoTime();
                for (int i = 0; i < slots; i++) {
                    long started = System.nanoTime(); var read = AnvilRecordReader.read(directory, new ChunkPos(i, 0));
                    var root = read.root();
                    boolean valid = i == 0 ? RocketRetirementProof.survivorSlot(root, BayConsumptionFixtures.LOGICAL, frozen)
                            : RocketRetirementProof.absentSlot(root, BayConsumptionFixtures.LOGICAL, BayConsumptionFixtures.SOURCE);
                    if (!valid) { throw new IllegalStateException("Read validator refused slot " + i); }
                    if (sample >= 5) { readTimes.add((System.nanoTime() - started) / 1e6); }
                    decoded = Math.max(decoded, read.size().decodedBytes()); compressed = Math.max(compressed, read.size().compressedBytes());
                }
                if (sample >= 5) { fullTimes.add((System.nanoTime() - begin) / 1e6); }
            }
        } finally {
            // Only the private temporary directory created above; no source world or committed evidence is touched.
            try (var files = Files.walk(directory)) {
                for (var path : files.sorted(java.util.Comparator.reverseOrder()).toList()) { Files.delete(path); }
            }
        }
        var result = new LinkedHashMap<String, Object>();
        result.put("source_commit", args[1]); result.put("environment", "DEVELOPMENT_HOST_NOT_REFERENCE_SERVER");
        result.put("java", System.getProperty("java.runtime.version")); result.put("heap_bytes", Runtime.getRuntime().maxMemory());
        result.put("fixture", Map.of("matches", 63, "slots", slots, "max_decoded_bytes", decoded, "max_compressed_bytes", compressed,
                "warmup", 5, "samples", 50));
        result.put("loaded_census_ms", summary(censusTimes)); result.put("off_thread_slot_read_ms", summary(readTimes));
        result.put("serial_whole_census_read_ms", summary(fullTimes));
        result.put("server_thread_budget_ms", Map.of("p95", 50, "p99", 100));
        result.put("status", percentile(censusTimes, .95) <= 50 && percentile(censusTimes, .99) <= 100 ? "CENSUS_WITHIN_BUDGET" : "STOP_RULE");
        result.put("limitations", "Warm filesystem cache; no capture/save/force or native barrier scheduling measured. Whole-census IO is off-thread, not one tick.");
        SatelliteRegistryBenchmark.persist(Path.of(args[0]), result);
        System.out.println("BAY_RECOVERY_READ_BENCHMARK " + result);
    }

    private static Map<String, Object> summary(List<Double> values) {
        return Map.of("samples", values.size(), "p50", percentile(values, .5), "p95", percentile(values, .95),
                "p99", percentile(values, .99), "max", values.stream().mapToDouble(Double::doubleValue).max().orElseThrow());
    }
    private static double percentile(List<Double> values, double fraction) {
        return SatelliteRegistryBenchmark.percentile(values.stream().sorted().toList(), fraction);
    }
}
