package io.github.sunthemoon.advancedrocketrycommunity.satellite.persistence;

import static io.github.sunthemoon.advancedrocketrycommunity.satellite.persistence.GroundSurveyFixtures.*;

import io.github.sunthemoon.advancedrocketrycommunity.progression.ResearchAccount;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.mission.*;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.model.*;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import java.lang.management.ManagementFactory;
import java.nio.file.Path;
import java.util.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;

/** Standalone W168 owner-thread preflight measurement; run with Xmx4G under the heavy-JVM lock. */
public final class GroundPreflightBenchmark {
    private static final int SAMPLES = 50, WARMUP = 5;
    private static final int NEAR_BOUND = SatelliteLimits.MAX_REGISTRY_NBT_BYTES - 4 * 1024;
    private static final int SECTION_FIT = 5 * 1024 * 1024;
    private enum Operation { START, ACCOUNT, ADVANCE }

    private GroundPreflightBenchmark() { }

    public static void main(String[] args) throws Exception {
        Path output = Path.of(args[0]);
        long maxHeap = Runtime.getRuntime().maxMemory();
        if (maxHeap > 4L * 1024 * 1024 * 1024) { throw new IllegalStateException("Heap exceeds Xmx4G"); }
        var report = new LinkedHashMap<String, Object>();
        report.put("source_commit", args[1]);
        report.put("max_heap_bytes", maxHeap);
        report.put("java", System.getProperty("java.runtime.version"));
        report.put("os", System.getProperty("os.name") + " " + System.getProperty("os.arch"));
        report.put("processors", Runtime.getRuntime().availableProcessors());
        report.put("scope", "Complete synchronous domain calls, fresh registry per sample; restore and validation outside "
                + "timing. Near-bound roots leave less than 8 KiB for one candidate. Missing-account analysis uses "
                + "the domain restore entry before finishRestore's account repair, not a normal post-load state. "
                + "Section-fit controls use 5 MiB of claimed missions. No writer or world is running.");
        report.put("warmup_per_case", WARMUP);
        report.put("budgets_ms", Map.of("p95", 50, "p99", 100));
        var results = new LinkedHashMap<String, Object>();
        report.put("results", results);
        report.put("status", "IN_PROGRESS");
        try {
            MinecraftBootstrap.initialize();
            var large = missions(NEAR_BOUND);
            var fit = missions(SECTION_FIT);
            for (Operation operation : Operation.values()) {
                results.put("near_bound_" + operation.name().toLowerCase(Locale.ROOT), measure(large, operation));
                SatelliteRegistryBenchmark.persist(output, report);
            }
            for (Operation operation : List.of(Operation.START, Operation.ACCOUNT)) {
                results.put("section_fit_" + operation.name().toLowerCase(Locale.ROOT), measure(fit, operation));
                SatelliteRegistryBenchmark.persist(output, report);
            }
            boolean within = results.values().stream().allMatch(r -> Boolean.TRUE.equals(((Map<?, ?>) r).get("within")));
            report.put("status", within ? "WITHIN_BUDGET" : "STOP_RULE");
        } catch (Throwable failure) {
            report.put("status", "FAILED");
            report.put("error", failure.toString());
            throw failure;
        } finally {
            SatelliteRegistryBenchmark.persist(output, report);
        }
    }

    private static Map<String, Object> measure(List<MissionState> missions, Operation operation) {
        var samples = new ArrayList<double[]>();
        int slowPaths = 0, accepted = 0;
        var fixture = new LinkedHashMap<String, Object>();
        var threads = ManagementFactory.getThreadMXBean();
        for (int sample = -WARMUP; sample < SAMPLES; sample++) {
            CountingSizer sizer = new CountingSizer();
            SatelliteMissionRegistry registry = registry(missions, operation, sizer);
            if (sample == 0) {
                fixture.put("root_bytes_before", bytes(registry));
                fixture.put("reservation_bound_before", reservationBound(registry));
                fixture.put("mission_reservation_bytes", registry.reservedBytes("MISSIONS"));
                fixture.put("missions", missions.size());
                fixture.put("bound_bytes", SatelliteLimits.MAX_REGISTRY_NBT_BYTES);
            }
            long cpu = threads.getCurrentThreadCpuTime(), wall = System.nanoTime();
            GroundSurveys.Result result = switch (operation) {
                case START -> registry.groundSurveys().start(start(), 0);
                case ACCOUNT -> registry.groundSurveys().advanceAnalysis(id(100), device(300), 30);
                case ADVANCE -> registry.groundSurveys().advanceSurvey(id(100), device(200), 24_000, 1);
            };
            double elapsed = (System.nanoTime() - wall) / 1e6;
            double cpuElapsed = (threads.getCurrentThreadCpuTime() - cpu) / 1e6;
            if (result.code() != GroundSurveys.Code.SUCCESS || !result.changed()) {
                throw new IllegalStateException(operation + " refused: " + result.code());
            }
            if (operation == Operation.ACCOUNT && registry.account(OWNER).balance() != 120) {
                throw new IllegalStateException("Account was not created and credited");
            }
            if (sample == 0) { fixture.put("root_bytes_after", bytes(registry)); }
            if (sample >= 0) {
                samples.add(new double[] {elapsed, cpuElapsed});
                slowPaths += sizer.rootChecks;
                accepted++;
            }
        }
        var result = SatelliteRegistryBenchmark.statistics(samples);
        result.put("fixture", fixture);
        result.put("whole_root_preflights", slowPaths);
        result.put("accepted", accepted);
        System.out.println("ARCE_W168 " + operation + " missions=" + missions.size() + " p95=" + result.get("p95_ms")
                + " p99=" + result.get("p99_ms") + " slow_paths=" + slowPaths + "/" + SAMPLES);
        return result;
    }

    private static SatelliteMissionRegistry registry(List<MissionState> missions, Operation operation, RecordSizer sizer) {
        var registry = SatelliteMissionRegistry.restore(0, 0, 2, sizer);
        missions.forEach(registry::restoreMission);
        if (operation != Operation.ACCOUNT) { registry.restoreAccount(ResearchAccount.empty(OWNER)); }
        if (operation != Operation.START) {
            GroundSurveyJob job = active(0, OWNER);
            if (operation == Operation.ACCOUNT) {
                job = running(0, OWNER, device(300));
                job = job.analyzed(job.analysis().with(GroundSurveyJob.AnalysisState.RUNNING, 1,
                        job.analysis().claim()), 20);
            }
            registry.restoreInstance(instance(job, operation == Operation.ACCOUNT ? InstanceState.AVAILABLE : InstanceState.PENDING));
            registry.groundSurveys().restore(job);
        }
        return registry;
    }

    private static List<MissionState> missions(int targetBytes) {
        var registry = SatelliteMissionRegistry.restore(0, 0, 2, CodecRecordSizer.INSTANCE);
        int missionBytes = SatelliteNbtSize.uncompressedBytes(SatelliteNbtCodec.encodeMission(claimed(0))) - 3;
        int count = (targetBytes - bytes(registry)) / missionBytes;
        for (int i = 0; i < count; i++) { registry.restoreMission(claimed(i)); }
        // Establish that this is a loadable production root, not padding or an impossible graph.
        return SatelliteRegistryPayload.decodeCurrent(SatelliteRegistryPayload.encodeCurrent(registry, new CompoundTag())).missions();
    }

    /** W128's large claimed gas-record shape, with no live satellite or account dependency. */
    static MissionState claimed(int index) {
        List<RewardEntry> reward = new ArrayList<>();
        for (int entry = 0; entry < SatelliteLimits.MAX_REWARD_ENTRIES; entry++) {
            reward.add(new RewardEntry(ResourceLocation.tryParse(String.format(Locale.ROOT, "x:%05d_%02d_", index, entry)
                    + "i".repeat(115)), 1));
        }
        return new MissionState(SatelliteLimits.MISSION_SCHEMA_VERSION, new UUID(31, index), new UUID(32, index),
                new UUID(33, index % 256), TYPE, MissionKind.GAS, SYSTEM, Optional.empty(), index, 0, 20, 1,
                MissionStatus.CLAIMED, OptionalLong.of(20), OptionalLong.of(20), "r".repeat(200), Optional.empty(),
                new MissionPayload.Resource(MissionKind.GAS, reward, new UUID(34, index), Optional.empty(), false,
                        Optional.empty(), false, OptionalLong.empty()));
    }

    static GroundSurveys.Start start() {
        return new GroundSurveys.Start(id(100), OWNER, device(200), pending(0, OWNER, InstanceOrigin.ground(id(100))),
                20, 20, 2, 20, 120);
    }

    static long reservationBound(SatelliteMissionRegistry registry) {
        long result = RecordSizer.ROOT_FRAMING_BYTES + (long) registry.accounts().size() * RecordSizer.ACCOUNT_BYTES;
        for (String section : List.of("SATELLITES", "MISSIONS", "INSTANCES", "GROUND_SURVEYS")) {
            result += registry.reservedBytes(section);
        }
        return result;
    }

    private static int bytes(SatelliteMissionRegistry registry) {
        return SatelliteNbtSize.uncompressedBytes(SatelliteRegistryPayload.encodeCurrent(registry, new CompoundTag()));
    }

    static class CountingSizer implements RecordSizer {
        int rootChecks;
        public int satelliteBytes(SatelliteState value) { return CodecRecordSizer.INSTANCE.satelliteBytes(value); }
        public int missionBytes(MissionState value) { return CodecRecordSizer.INSTANCE.missionBytes(value); }
        public int instanceBytes(AsteroidInstance value) { return CodecRecordSizer.INSTANCE.instanceBytes(value); }
        public int groundSurveyBytes(GroundSurveyJob value) { return CodecRecordSizer.INSTANCE.groundSurveyBytes(value); }
        public boolean groundMutationFits(SatelliteMissionRegistry registry, GroundSurveyJob job,
                AsteroidInstance instance, boolean removeInstance, ResearchAccount account, long now, long observedTime) {
            rootChecks++;
            return CodecRecordSizer.INSTANCE.groundMutationFits(registry, job, instance, removeInstance, account, now, observedTime);
        }
    }
}
