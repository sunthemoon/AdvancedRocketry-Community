package io.github.sunthemoon.advancedrocketrycommunity.satellite.persistence;

import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import io.github.sunthemoon.advancedrocketrycommunity.persistence.CheckedRootWriter;
import io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.CheckedSavedDataPolicy;
import io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.ManagedSavedDataType;
import io.github.sunthemoon.advancedrocketrycommunity.progression.ResearchAccount;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.mission.*;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.model.*;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.UnstartedServer;
import java.lang.management.ManagementFactory;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BooleanSupplier;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.dedicated.DedicatedServer;

/**
 * W129b B1 (ADR-074 item 13, design section 6.1): the server-thread cost of the off-thread registry writer, held to
 * docs/17's P95 50 ms and P99 100 ms. On the calling thread it times the request (which takes the capture) and the
 * tick-START publication for three compositions: W128's byte-bound root (the 32-registration checkpoint, the coalesced
 * request and the autosave request), the maximum reference counts (20,480 records), and the maximum epochAdvanced
 * publication work (8,192 acknowledged claims that become durable at once). Writer-thread time, the sampled peak heap,
 * the 16 MiB byte identity of the capture's encoding and its over-bound refusal are reported beside them. A standalone
 * main class with Xmx4G under the heavy-JVM lock, not a unit test.
 */
public final class SatelliteRegistryWriterBenchmark {
    static final int SAMPLES = 50, REGISTRATIONS = 32;
    private static final ManagedSavedDataType TYPE = ManagedSavedDataType.SATELLITE_MISSIONS;
    private static final ResourceLocation DEFINITION = ModIdentity.id("asteroid_miner");
    private static final ResourceLocation EARTH = ModIdentity.id("earth");
    private static final UUID OWNER = new UUID(128, 1);
    private static boolean smoke;

    private SatelliteRegistryWriterBenchmark() { }

    public static void main(String[] args) throws Exception {
        Path output = Path.of(args[0]), directory = Path.of(args[1]);
        if (Files.exists(directory)) { throw new IllegalArgumentException("Benchmark directory must be new"); }
        if (Runtime.getRuntime().maxMemory() > 4L * 1024 * 1024 * 1024) { throw new IllegalStateException("Heap exceeds Xmx4G"); }
        smoke = args.length > 3 && args[3].contains("smoke");
        Files.createDirectory(directory);
        var report = new LinkedHashMap<String, Object>();
        report.put("status", "IN_PROGRESS"); report.put("source_commit", args.length > 2 ? args[2] : "unknown");
        report.put("mode", smoke ? "SMOKE_NOT_EVIDENCE" : "MEASUREMENT");
        report.put("scope", "Server-thread wall and CPU time of each registry write entry on a live-like registry (a real but "
                + "unstarted dedicated server supplies the tick): the request, which takes the capture, and the tick-START "
                + "poll that publishes its completion. Registrations, watches and decommissions are outside the timed calls. "
                + "Writer-thread wall and CPU time per write are reported, not budgeted.");
        report.put("budgets_ms", Map.of("p95", SatelliteRegistryBenchmark.P95_MS, "p99", SatelliteRegistryBenchmark.P99_MS));
        report.put("environment", environment());
        var heap = new HeapSampler();
        try {
            MinecraftBootstrap.initialize();
            var results = new LinkedHashMap<String, Object>();
            results.put("byte_bound", byteBound(directory.resolve("byte-bound"), report));
            SatelliteRegistryBenchmark.persist(output, report);
            results.put("maximum_references", maximumReferences(directory.resolve("references")));
            results.put("maximum_epoch_advanced", maximumEpochAdvanced(directory.resolve("epoch-advanced")));
            report.put("results", results);
            report.put("sampled_peak_heap_bytes", heap.peak());
            boolean within = true;
            for (Object composition : results.values()) {
                for (Object entry : ((Map<?, ?>) composition).values()) {
                    if (entry instanceof Map<?, ?> figures && figures.containsKey("within")) {
                        within &= Boolean.TRUE.equals(figures.get("within"));
                    }
                }
            }
            report.put("status", smoke ? "SMOKE_COMPLETED" : within ? "WITHIN_BUDGET" : "STOP_RULE");
        } catch (Throwable failure) {
            report.put("status", "FAILED"); report.put("error", failure.toString());
            SatelliteRegistryBenchmark.persist(output, report); throw failure;
        } finally {
            heap.close();
            SatelliteRegistryBenchmark.persist(output, report);
            try (var tree = Files.walk(directory)) {
                for (Path path : tree.sorted(Comparator.reverseOrder()).toList()) { Files.deleteIfExists(path); }
            }
        }
    }

    /** W128's 16 MiB root: the checkpoint after 32 registrations, the coalesced request and the autosave request. */
    private static Map<String, Object> byteBound(Path directory, Map<String, Object> report) throws Exception {
        Files.createDirectories(directory);
        Path file = directory.resolve(TYPE.fileName());
        CompoundTag root = smoke ? RegistryWriterFixtures.padded(200).save(new CompoundTag()) : SatelliteRegistryBenchmark.root();
        var data = SatelliteMissionSavedData.load(root);
        if (!data.operational()) { throw new IllegalStateException("The byte-bound fixture did not load"); }
        var result = new LinkedHashMap<String, Object>();
        result.put("fixture", Map.of("root_bytes", rootBytes(data), "missions", data.missions().size(),
                "satellites", data.satellites().size(), "bound_bytes", SatelliteLimits.MAX_REGISTRY_NBT_BYTES));
        result.put("capture_byte_identity", identity(data));
        result.put("over_bound_refusal", overBound(data, file));
        var server = live(); data.bindServerForTests(server);
        var tick = new long[] {0};
        write(server, data, tick, () -> data.requestWrite(file, CheckedSavedDataPolicy.atomic(TYPE)));
        var checkpointRequest = new ArrayList<double[]>(); var checkpointPublish = new ArrayList<double[]>();
        var coalescedRequest = new ArrayList<double[]>(); var coalescedPublish = new ArrayList<double[]>();
        var autosaveRequest = new ArrayList<double[]>(); var autosavePublish = new ArrayList<double[]>();
        var writer = new ArrayList<double[]>();
        long serial = 0;
        int samples = smoke ? 5 : SAMPLES;
        for (int sample = 0; sample < samples; sample++) {
            var ids = new ArrayList<UUID>(); var watches = new ArrayList<SatelliteRegistryProof.Watch>();
            for (int i = 0; i < REGISTRATIONS; i++) { UUID id = register(data, ++serial); ids.add(id); watches.add(data.watch(id)); }
            var checkpoint = new SatelliteRegistryProof.Checkpoint[1];
            cadence(server, tick);
            checkpointRequest.add(timed(() -> checkpoint[0] = data.requestCheckpoint(file, watches,
                    CheckedSavedDataPolicy.DirectoryForce.PLATFORM)));
            checkpointPublish.add(publish(server, data, tick));
            writer.add(writerTime(data));
            if (!watches.stream().allMatch(watch -> checkpoint[0].answer().filter(answer -> answer.proves(watch)).isPresent())) {
                throw new IllegalStateException("The checkpoint did not prove 32 registrations");
            }
            ids.forEach(id -> decommission(data, id));

            UUID flushed = register(data, ++serial);
            cadence(server, tick);
            coalescedRequest.add(timed(() -> require(data.requestWrite(file, CheckedSavedDataPolicy.atomic(TYPE)))));
            coalescedPublish.add(publish(server, data, tick));
            writer.add(writerTime(data));
            decommission(data, flushed);

            UUID saved = register(data, ++serial);
            cadence(server, tick);
            autosaveRequest.add(timed(() -> data.save(file.toFile())));
            if (!data.writerBusy()) { throw new IllegalStateException("Autosave made no request"); }
            autosavePublish.add(publish(server, data, tick));
            writer.add(writerTime(data));
            if (data.isDirty()) { throw new IllegalStateException("Autosave did not acknowledge its capture"); }
            decommission(data, saved);
        }
        result.put("checkpoint_32_registrations_request", SatelliteRegistryBenchmark.statistics(checkpointRequest));
        result.put("checkpoint_32_registrations_publication", SatelliteRegistryBenchmark.statistics(checkpointPublish));
        result.put("coalesced_request", SatelliteRegistryBenchmark.statistics(coalescedRequest));
        result.put("coalesced_publication", SatelliteRegistryBenchmark.statistics(coalescedPublish));
        result.put("autosave_request", SatelliteRegistryBenchmark.statistics(autosaveRequest));
        result.put("autosave_publication", SatelliteRegistryBenchmark.statistics(autosavePublish));
        result.put("writer_thread_not_budgeted", writerStatistics(writer));
        result.put("file_bytes", Files.size(file));
        data.close();
        return result;
    }

    /** 4,096 satellites, 8,192 missions, 4,096 accounts, 2,048 instances and 2,048 ground jobs: 20,480 references. */
    private static Map<String, Object> maximumReferences(Path directory) throws Exception {
        Files.createDirectories(directory);
        Path file = directory.resolve(TYPE.fileName());
        int scale = smoke ? 16 : 1;
        var registry = SatelliteMissionRegistry.restore(0, 0, 1, CodecRecordSizer.INSTANCE);
        for (int i = 0; i < SatelliteLimits.MAX_RESEARCH_ACCOUNTS / scale; i++) {
            registry.restoreAccount(new ResearchAccount(1, owner(i), 0, 0, 0));
        }
        for (int i = 0; i < SatelliteLimits.MAX_SATELLITES / scale; i++) { registry.restoreSatellite(idle(new UUID(42, i), owner(i), 0)); }
        for (int i = 0; i < SatelliteLimits.MAX_MISSIONS / scale; i++) { registry.restoreMission(leanClaimed(i, false, 1)); }
        for (int i = 0; i < SatelliteLimits.MAX_GROUND_SURVEYS / scale; i++) {
            GroundSurveyJob job = GroundSurveyFixtures.active(i, owner(i));
            registry.restoreInstance(GroundSurveyFixtures.pending(i, owner(i), InstanceOrigin.ground(job.jobId())));
            registry.groundSurveys().restore(job);
        }
        if (registry.finishRestore().changed()) { throw new IllegalStateException("The reference fixture needed a repair"); }
        var data = SatelliteMissionSavedData.load(SatelliteRegistryPayload.encodeCurrent(registry, new CompoundTag()));
        if (!data.operational() || data.isDirty()) { throw new IllegalStateException("The reference fixture did not load cleanly"); }
        var result = new LinkedHashMap<String, Object>();
        var sections = data.captureForTests().sections();
        int references = sections.satellites().size() + sections.missions().size() + sections.accounts().size()
                + sections.instances().size() + sections.groundSurveys().size();
        result.put("fixture", Map.of("references", references, "satellites", sections.satellites().size(),
                "missions", sections.missions().size(), "accounts", sections.accounts().size(),
                "instances", sections.instances().size(), "ground_surveys", sections.groundSurveys().size(),
                "root_bytes", rootBytes(data)));
        var server = live(); data.bindServerForTests(server);
        var tick = new long[] {0};
        write(server, data, tick, () -> data.requestWrite(file, CheckedSavedDataPolicy.atomic(TYPE)));
        var request = new ArrayList<double[]>(); var publication = new ArrayList<double[]>(); var writer = new ArrayList<double[]>();
        long serial = 1_000_000;
        for (int sample = 0; sample < (smoke ? 5 : SAMPLES); sample++) {
            // A removal and the same identity's relaunch: two state changes, and the counts stay at the bound.
            int index = sample % sections.satellites().size();
            UUID satellite = new UUID(42, index);
            require(data.decommission(satellite, owner(index), true, true).changed());
            require(data.launchIdle(time -> idle(satellite, owner(index), time), ++serial).changed());
            cadence(server, tick);
            request.add(timed(() -> require(data.requestWrite(file, CheckedSavedDataPolicy.atomic(TYPE)))));
            publication.add(publish(server, data, tick));
            writer.add(writerTime(data));
        }
        result.put("request", SatelliteRegistryBenchmark.statistics(request));
        result.put("publication", SatelliteRegistryBenchmark.statistics(publication));
        result.put("writer_thread_not_budgeted", writerStatistics(writer));
        data.close();
        return result;
    }

    /** 8,192 acknowledged claims, stamped at the file's epoch, all become durable at the next commit's publication. */
    private static Map<String, Object> maximumEpochAdvanced(Path directory) throws Exception {
        Files.createDirectories(directory);
        int missions = smoke ? 256 : SatelliteLimits.MAX_MISSIONS;
        var registry = SatelliteMissionRegistry.restore(0, 0, 1, CodecRecordSizer.INSTANCE);
        registry.restoreAccount(new ResearchAccount(1, OWNER, 0, 0, 0));
        for (int i = 0; i < Math.min(missions, SatelliteLimits.MAX_RESEARCH_ACCOUNTS - 1); i++) {
            registry.restoreAccount(new ResearchAccount(1, owner(i), 0, 0, 0));
        }
        registry.restoreSatellite(idle(new UUID(43, 0), OWNER, 0));
        for (int i = 0; i < missions; i++) { registry.restoreMission(leanClaimed(i, true, 1)); }
        if (registry.finishRestore().changed()) { throw new IllegalStateException("The epoch fixture needed a repair"); }
        CompoundTag root = SatelliteRegistryPayload.encodeCurrent(registry, new CompoundTag());
        var result = new LinkedHashMap<String, Object>();
        result.put("fixture", Map.of("acknowledged_claims_waiting", missions, "root_bytes", SatelliteNbtSize.uncompressedBytes(root)));
        var request = new ArrayList<double[]>(); var publication = new ArrayList<double[]>(); var writer = new ArrayList<double[]>();
        for (int sample = 0; sample < (smoke ? 3 : SAMPLES); sample++) {
            Path file = Files.createDirectories(directory.resolve("sample-" + sample)).resolve(TYPE.fileName());
            var data = SatelliteMissionSavedData.load(root.copy());
            var server = live(); data.bindServerForTests(server);
            var tick = new long[] {0};
            long durable = data.saveEpoch();
            require(data.decommission(new UUID(43, 0), OWNER, true, true).changed());
            cadence(server, tick);
            request.add(timed(() -> require(data.requestWrite(file, CheckedSavedDataPolicy.atomic(TYPE)))));
            publication.add(publish(server, data, tick));
            writer.add(writerTime(data));
            if (data.saveEpoch() != durable + 1) { throw new IllegalStateException("The epoch did not advance"); }
            data.close();
            Files.deleteIfExists(file);
        }
        result.put("request", SatelliteRegistryBenchmark.statistics(request));
        result.put("publication_with_epoch_advanced", SatelliteRegistryBenchmark.statistics(publication));
        result.put("writer_thread_not_budgeted", writerStatistics(writer));
        return result;
    }

    /** The capture's encoding against save(CompoundTag), streamed through SHA-256, at the byte-bound root. */
    private static Map<String, Object> identity(SatelliteMissionSavedData data) throws Exception {
        byte[] saved = RegistryWriterFixtures.bytes(data.save(new CompoundTag()));
        byte[] captured = RegistryWriterFixtures.bytes(SatelliteRegistryCapture.encode(data.captureForTests()));
        String savedHash = sha256(saved), capturedHash = sha256(captured);
        if (!savedHash.equals(capturedHash) || !Arrays.equals(saved, captured)) {
            throw new IllegalStateException("The capture's encoding differs from save(CompoundTag)");
        }
        return Map.of("bytes", saved.length, "sha256", savedHash, "identical", true);
    }

    /** A capture with the fixture's sections plus enough W128 missions to pass the bound is refused before any replacement. */
    private static Map<String, Object> overBound(SatelliteMissionSavedData data, Path file) {
        var capture = data.captureForTests();
        var missions = new ArrayList<>(capture.sections().missions());
        int start = missions.size();
        int extra = (SatelliteLimits.MAX_REGISTRY_NBT_BYTES - rootBytes(data)) / 3_000 + 64;
        for (int i = 0; i < extra; i++) { missions.add(RegistryWriterFixtures.claimed(100_000 + start + i)); }
        var sections = capture.sections();
        var oversized = new SatelliteRegistryCapture.Capture(capture.sequence(), capture.stateSequence(), capture.epoch(),
                new RegistrySections(sections.logicalGameTime(), sections.lastObservedGameTime(), sections.satellites(),
                        missions, sections.accounts(), sections.instances(), sections.groundSurveys()), null, null);
        var attempt = SatelliteRegistryCapture.write(oversized, new SatelliteRegistryCapture.Attributes(
                CheckedSavedDataPolicy.atomic(TYPE), null), file);
        if (attempt.status() != CheckedRootWriter.Status.FAILED || Files.exists(file)) {
            throw new IllegalStateException("An over-bound capture was not refused before replacement");
        }
        return Map.of("missions", missions.size(), "status", attempt.status().name(),
                "failure", String.valueOf(attempt.payload().failure()));
    }

    private static DedicatedServer live() {
        DedicatedServer server = UnstartedServer.owned();
        UnstartedServer.set(MinecraftServer.class, server, "running", true);
        return server;
    }

    private static void cadence(MinecraftServer server, long[] tick) {
        tick[0] += CheckedRootWriter.CADENCE_TICKS;
        UnstartedServer.set(MinecraftServer.class, server, "tickCount", (int) tick[0]);
    }

    /** Polls once per millisecond tick and returns the {wall, cpu} of the poll that published the completion. */
    private static double[] publish(MinecraftServer server, SatelliteMissionSavedData data, long[] tick) throws InterruptedException {
        long before = data.writerForTests().completedWrites();
        long deadline = System.nanoTime() + 600_000_000_000L;
        while (true) {
            if (System.nanoTime() > deadline) { throw new IllegalStateException("The registry write did not publish"); }
            tick[0]++;
            UnstartedServer.set(MinecraftServer.class, server, "tickCount", (int) tick[0]);
            if (!data.writerForTests().completionReady()) { Thread.sleep(1); continue; }
            double[] timed = timed(data::pollWriter);
            if (data.writerForTests().completedWrites() == before) { throw new IllegalStateException("A ready poll published nothing"); }
            if (data.writerBusy()) { throw new IllegalStateException("A pending request remained after publication"); }
            return timed;
        }
    }

    private static void write(MinecraftServer server, SatelliteMissionSavedData data, long[] tick, BooleanSupplier request)
            throws InterruptedException {
        cadence(server, tick);
        require(request.getAsBoolean());
        publish(server, data, tick);
    }

    private static double[] writerTime(SatelliteMissionSavedData data) {
        var writer = data.writerForTests();
        return new double[] {writer.lastWriterNanos() / 1e6, writer.lastWriterCpuNanos() / 1e6};
    }

    private static Map<String, Object> writerStatistics(List<double[]> samples) {
        var statistics = new LinkedHashMap<>(SatelliteRegistryBenchmark.statistics(samples));
        statistics.remove("within"); // Writer-thread time is reported, not budgeted.
        return statistics;
    }

    private static MissionState leanClaimed(int index, boolean acknowledged, long ackEpoch) {
        var reward = List.of(new RewardEntry(ResourceLocation.tryParse("minecraft:iron_ore"), 1));
        UUID terminal = new UUID(34, 0);
        return new MissionState(SatelliteLimits.MISSION_SCHEMA_VERSION, new UUID(44, index), new UUID(45, index),
                owner(index % SatelliteLimits.MAX_RESEARCH_ACCOUNTS), GroundSurveyFixtures.TYPE, MissionKind.GAS,
                GroundSurveyFixtures.SYSTEM, Optional.empty(), index, 0, 20, 1, MissionStatus.CLAIMED, OptionalLong.of(20),
                OptionalLong.of(20), "gas-v1/b1", Optional.empty(), new MissionPayload.Resource(MissionKind.GAS, reward, terminal,
                Optional.empty(), false, Optional.of(terminal), acknowledged,
                acknowledged ? OptionalLong.of(ackEpoch) : OptionalLong.empty()));
    }

    private static UUID owner(int index) { return new UUID(41, index); }

    private static SatelliteState idle(UUID id, UUID owner, long time) {
        var blueprint = new SatelliteBlueprint(List.of(ModIdentity.id("satellite_chassis"), ModIdentity.id("asteroid_drill_module"),
                ModIdentity.id("satellite_cargo_hold")), false, new SatelliteStats(4, 10_720, 1_000, 9, 10));
        return SatelliteState.launchIdle(id, DEFINITION, owner, time, EARTH, blueprint, new SatelliteKindState.Plain(SatelliteKind.ASTEROID_MINER));
    }

    private static UUID register(SatelliteMissionSavedData data, long serial) {
        UUID id = new UUID(129, serial);
        var result = data.launchIdle(time -> idle(id, OWNER, time), serial);
        if (result.code() != SatelliteOperationCode.SUCCESS) { throw new IllegalStateException("Registration refused: " + result.code()); }
        return id;
    }

    private static void decommission(SatelliteMissionSavedData data, UUID id) {
        var result = data.decommission(id, OWNER, true, false);
        if (result.code() != SatelliteOperationCode.SUCCESS) { throw new IllegalStateException("Decommission refused: " + result.code()); }
    }

    private static int rootBytes(SatelliteMissionSavedData data) { return SatelliteNbtSize.uncompressedBytes(data.save(new CompoundTag())); }

    private static double[] timed(Runnable operation) {
        var threads = ManagementFactory.getThreadMXBean();
        long cpu = threads.getCurrentThreadCpuTime(), wall = System.nanoTime();
        operation.run();
        return new double[] {(System.nanoTime() - wall) / 1e6, (threads.getCurrentThreadCpuTime() - cpu) / 1e6};
    }

    private static void require(boolean condition) { if (!condition) { throw new IllegalStateException("A benchmark step was refused"); } }

    private static String sha256(byte[] bytes) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    }

    private static Map<String, Object> environment() {
        var runtime = ManagementFactory.getRuntimeMXBean();
        var os = (com.sun.management.OperatingSystemMXBean) ManagementFactory.getOperatingSystemMXBean();
        var environment = new LinkedHashMap<String, Object>();
        environment.put("java", System.getProperty("java.vm.vendor") + " " + System.getProperty("java.vm.name") + " "
                + System.getProperty("java.runtime.version"));
        environment.put("jvm_arguments", runtime.getInputArguments());
        environment.put("collectors", ManagementFactory.getGarbageCollectorMXBeans().stream().map(java.lang.management.GarbageCollectorMXBean::getName).toList());
        environment.put("max_heap_bytes", Runtime.getRuntime().maxMemory());
        environment.put("processors", Runtime.getRuntime().availableProcessors());
        environment.put("processor", System.getenv().getOrDefault("PROCESSOR_IDENTIFIER", "unknown"));
        environment.put("physical_memory_bytes", os.getTotalMemorySize());
        environment.put("os", System.getProperty("os.name") + " " + System.getProperty("os.version") + " " + System.getProperty("os.arch"));
        return environment;
    }

    /** The used heap sampled every millisecond: a pressure figure that includes uncollected garbage. */
    private static final class HeapSampler implements AutoCloseable {
        private final AtomicLong peak = new AtomicLong();
        private final AtomicBoolean running = new AtomicBoolean(true);
        private final Thread thread = new Thread(() -> {
            var memory = ManagementFactory.getMemoryMXBean();
            while (running.get()) {
                peak.accumulateAndGet(memory.getHeapMemoryUsage().getUsed(), Math::max);
                try { Thread.sleep(1); } catch (InterruptedException stop) { return; }
            }
        }, "W129b-heap-sampler");
        HeapSampler() { thread.setDaemon(true); thread.start(); }
        long peak() { return peak.get(); }
        @Override public void close() throws InterruptedException { running.set(false); thread.interrupt(); thread.join(); }
    }
}
