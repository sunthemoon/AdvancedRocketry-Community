package io.github.sunthemoon.advancedrocketrycommunity.satellite.persistence;

import com.google.gson.GsonBuilder;
import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.CheckedSavedDataPolicy;
import io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.ManagedSavedDataType;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.mission.*;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.model.*;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import java.lang.management.ManagementFactory;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;

/**
 * W128 (Root decision R49 Q6): the cost of the bay's three registry write entries at a 16 MiB root, measured before any
 * coordinator code. Each sample is one whole server-thread operation, so its P95 and P99 are held to docs/17's 50 and
 * 100 ms tick budgets. A standalone diagnostic run with Xmx4G under the heavy-JVM lock, not a unit test.
 */
public final class SatelliteRegistryBenchmark {
    static final int SAMPLES = 50, REGISTRATIONS = 32;
    static final double P95_MS = 50, P99_MS = 100;
    private static final ManagedSavedDataType TYPE = ManagedSavedDataType.SATELLITE_MISSIONS;
    private static final ResourceLocation DEFINITION = ModIdentity.id("asteroid_miner");
    private static final ResourceLocation EARTH = ModIdentity.id("earth");
    private static final UUID OWNER = new UUID(128, 1);

    private SatelliteRegistryBenchmark() { }

    public static void main(String[] args) throws Exception {
        Path output = Path.of(args[0]), directory = Path.of(args[1]);
        if (Files.exists(directory)) { throw new IllegalArgumentException("Benchmark directory must be new"); }
        if (Runtime.getRuntime().maxMemory() > 4L * 1024 * 1024 * 1024) { throw new IllegalStateException("Heap exceeds Xmx4G"); }
        Files.createDirectory(directory);
        Path file = directory.resolve(TYPE.fileName());
        var report = new LinkedHashMap<String, Object>();
        report.put("status", "IN_PROGRESS"); report.put("source_commit", args.length > 2 ? args[2] : "unknown");
        report.put("scope", "One SatelliteMissionSavedData loaded from a root filled with claimed missions to the 16 MiB bound "
                + "minus the registrations' margin. Each checkpoint sample follows 32 real launchIdle registrations and proves "
                + "all 32 watches; each flush and autosave sample follows one registration. Registrations, watches and "
                + "decommissions are outside the timed intervals. Wall time per call; thread CPU time is reported beside it.");
        report.put("budgets_ms", Map.of("p95", P95_MS, "p99", P99_MS));
        report.put("max_heap_bytes", Runtime.getRuntime().maxMemory());
        try {
            MinecraftBootstrap.initialize();
            SatelliteMissionSavedData data = SatelliteMissionSavedData.load(root());
            if (!data.operational()) { throw new IllegalStateException("The 16 MiB fixture did not load"); }
            report.put("fixture", Map.of("root_bytes", rootBytes(data), "missions", data.missions().size(),
                    "bound_bytes", SatelliteLimits.MAX_REGISTRY_NBT_BYTES, "dirty_after_load", data.isDirty()));
            persist(output, report);
            var policy = CheckedSavedDataPolicy.atomic(TYPE);
            var checkpoints = new ArrayList<double[]>(); var flushes = new ArrayList<double[]>(); var saves = new ArrayList<double[]>();
            int minimum = Integer.MAX_VALUE, maximum = 0; long serial = 0;
            for (int sample = 0; sample < SAMPLES; sample++) {
                var ids = new ArrayList<UUID>(); var watches = new ArrayList<SatelliteRegistryProof.Watch>();
                for (int i = 0; i < REGISTRATIONS; i++) { UUID id = register(data, ++serial); ids.add(id); watches.add(data.watch(id)); }
                int bytes = rootBytes(data); minimum = Math.min(minimum, bytes); maximum = Math.max(maximum, bytes);
                double[] timed = timed(() -> {
                    var completion = data.checkpoint(file, watches, policy, CheckedSavedDataPolicy.DirectoryForce.PLATFORM);
                    if (!watches.stream().allMatch(completion::proves)) { throw new IllegalStateException("Checkpoint did not prove 32 registrations"); }
                });
                checkpoints.add(timed);
                ids.forEach(id -> decommission(data, id));

                UUID flushed = register(data, ++serial);
                flushes.add(timed(() -> data.flush(file)));
                decommission(data, flushed);

                UUID saved = register(data, ++serial);
                if (!data.isDirty()) { throw new IllegalStateException("Autosave fixture is not dirty"); }
                saves.add(timed(() -> data.save(file.toFile())));
                if (data.isDirty()) { throw new IllegalStateException("Autosave did not acknowledge its capture"); }
                decommission(data, saved);
            }
            report.put("sample_root_bytes", Map.of("minimum", minimum, "maximum", maximum));
            var results = new LinkedHashMap<String, Object>();
            results.put("checkpoint_32_registrations", statistics(checkpoints));
            results.put("coalesced_flush", statistics(flushes));
            results.put("autosave", statistics(saves));
            report.put("results", results);
            report.put("file_bytes", Files.size(file));
            boolean within = results.values().stream().allMatch(value -> Boolean.TRUE.equals(((Map<?, ?>) value).get("within")));
            report.put("status", within ? "WITHIN_BUDGET" : "STOP_RULE");
        } catch (Throwable failure) {
            report.put("status", "FAILED"); report.put("error", failure.toString()); persist(output, report); throw failure;
        } finally {
            persist(output, report);
            // Only this fresh diagnostic directory's own files are removed; never a world or evidence.
            Files.deleteIfExists(file); Files.deleteIfExists(Path.of(file + ".arce-pending"));
            try (var left = Files.list(directory)) { for (Path path : left.toList()) { Files.deleteIfExists(path); } }
            Files.delete(directory);
        }
    }

    /** Claimed gas missions of about 3 KiB each (the GroundSurveyScaleTest fixture) up to the bound less a margin. */
    static CompoundTag root() {
        var registry = SatelliteMissionRegistry.restore(0, 0, 1, CodecRecordSizer.INSTANCE);
        int empty = SatelliteNbtSize.uncompressedBytes(SatelliteRegistryPayload.encodeCurrent(registry, new CompoundTag()));
        int mission = SatelliteNbtSize.uncompressedBytes(SatelliteNbtCodec.encodeMission(claimed(0))) - 3;
        int satellite = SatelliteNbtSize.uncompressedBytes(SatelliteNbtCodec.encodeSatellite(idle(new UUID(0, 0), 0)));
        int margin = (REGISTRATIONS + 2) * satellite + 64 * 1024;
        int count = (SatelliteLimits.MAX_REGISTRY_NBT_BYTES - margin - empty) / mission;
        for (int index = 0; index < count; index++) { registry.restoreMission(claimed(index)); }
        registry.finishRestore();
        return SatelliteRegistryPayload.encodeCurrent(registry, new CompoundTag());
    }

    private static MissionState claimed(int index) {
        List<RewardEntry> reward = new ArrayList<>();
        for (int entry = 0; entry < SatelliteLimits.MAX_REWARD_ENTRIES; entry++) {
            reward.add(new RewardEntry(ResourceLocation.tryParse(String.format("x:%05d_%02d_", index, entry) + "i".repeat(115)), 1));
        }
        return new MissionState(SatelliteLimits.MISSION_SCHEMA_VERSION, new UUID(31, index), new UUID(32, index),
                new UUID(33, index % 256), GroundSurveyFixtures.TYPE, MissionKind.GAS, GroundSurveyFixtures.SYSTEM, Optional.empty(),
                index, 0, 20, 1, MissionStatus.CLAIMED, OptionalLong.of(20), OptionalLong.of(20), "r".repeat(200), Optional.empty(),
                new MissionPayload.Resource(MissionKind.GAS, reward, new UUID(34, index), Optional.empty(), false,
                        Optional.empty(), false, OptionalLong.empty()));
    }

    private static SatelliteState idle(UUID id, long time) {
        var blueprint = new SatelliteBlueprint(List.of(ModIdentity.id("satellite_chassis"), ModIdentity.id("asteroid_drill_module"),
                ModIdentity.id("satellite_cargo_hold")), false, new SatelliteStats(4, 10_720, 1_000, 9, 10));
        return SatelliteState.launchIdle(id, DEFINITION, OWNER, time, EARTH, blueprint, new SatelliteKindState.Plain(SatelliteKind.ASTEROID_MINER));
    }

    private static UUID register(SatelliteMissionSavedData data, long serial) {
        UUID id = new UUID(129, serial);
        var result = data.launchIdle(time -> idle(id, time), serial);
        if (result.code() != SatelliteOperationCode.SUCCESS) { throw new IllegalStateException("Registration refused: " + result.code()); }
        return id;
    }

    private static void decommission(SatelliteMissionSavedData data, UUID id) {
        var result = data.decommission(id, OWNER, true, false);
        if (result.code() != SatelliteOperationCode.SUCCESS) { throw new IllegalStateException("Decommission refused: " + result.code()); }
    }

    private static int rootBytes(SatelliteMissionSavedData data) {
        return SatelliteNbtSize.uncompressedBytes(data.save(new CompoundTag()));
    }

    /** {wall ms, thread CPU ms} of one call on this thread. */
    private static double[] timed(Runnable operation) {
        var threads = ManagementFactory.getThreadMXBean();
        long cpu = threads.getCurrentThreadCpuTime(), wall = System.nanoTime();
        operation.run();
        return new double[] {(System.nanoTime() - wall) / 1e6, (threads.getCurrentThreadCpuTime() - cpu) / 1e6};
    }

    public static Map<String, Object> statistics(List<double[]> samples) {
        var wall = samples.stream().map(sample -> sample[0]).toList();
        var cpu = samples.stream().map(sample -> sample[1]).sorted().toList();
        var sorted = wall.stream().sorted().toList();
        double p95 = percentile(sorted, .95), p99 = percentile(sorted, .99);
        var result = new LinkedHashMap<String, Object>();
        result.put("samples", sorted.size()); result.put("wall_ms", wall);
        result.put("mean_ms", wall.stream().mapToDouble(Double::doubleValue).average().orElseThrow());
        result.put("p50_ms", percentile(sorted, .50)); result.put("p95_ms", p95); result.put("p99_ms", p99);
        result.put("maximum_ms", sorted.get(sorted.size() - 1));
        result.put("cpu_p50_ms", percentile(cpu, .50)); result.put("cpu_p95_ms", percentile(cpu, .95));
        result.put("within", p95 <= P95_MS && p99 <= P99_MS);
        return result;
    }

    public static double percentile(List<Double> sorted, double p) { return sorted.get((int) Math.ceil(p * sorted.size()) - 1); }

    public static void persist(Path output, Map<String, Object> report) throws java.io.IOException {
        Files.writeString(output, new GsonBuilder().setPrettyPrinting().create().toJson(report));
    }
}
