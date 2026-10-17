package io.github.sunthemoon.advancedrocketrycommunity.resourceescrow;

import io.github.sunthemoon.advancedrocketrycommunity.AdvancedRocketryCommunity;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.entity.RocketEntity;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFlightPlan;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFlightPlanner;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFlightState;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketPropulsionDebit;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.persistence.RocketTransferSavedData;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketNativeResources;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketPosition;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.phys.AABB;

/** Native eight-flight workload, deliberately separate from BENCH's saturated worker experiment. */
final class EscrowFlightBenchmarkProbe {
    static final String CASE = "GOVERNED_BENCH";
    static final int WARMUP_TICKS = 100, MEASURE_TICKS = 1400, DEADLINE_TICKS = 4000;
    final EscrowFlightBenchmarkMetrics metrics = new EscrowFlightBenchmarkMetrics();
    private final MinecraftServer server;
    private final EscrowFlightBenchmarkLoad load;
    private final boolean recovery;
    private final List<RocketEntity> sources = new ArrayList<>();
    private final List<UUID> logical = new ArrayList<>();
    private final boolean[] requested = new boolean[8];
    private final long[] requestedAt = new long[8];
    private ResourceEscrowSavedData data;
    private EscrowLedger ceiling;
    private EscrowFlightBenchmarkWork competing;
    private int age, warmup, elapsed, accepted, peakConcurrent, saves;
    private long savedTick = -1;
    private boolean initialized, measuring, done, finishing;
    private final boolean[] completed = new boolean[8];

    EscrowFlightBenchmarkProbe(MinecraftServer server) {
        EscrowTestEndpoints.requireEnabled(server); this.server = server;
        recovery = "recover".equals(System.getProperty("advancedrocketrycommunity.escrowScenario"));
        load = new EscrowFlightBenchmarkLoad(server);
    }
    void tick() {
        try { tickWork(); }
        catch (RuntimeException failure) {
            try { failureSnapshot(failure); }
            catch (Exception observationFailure) { failure.addSuppressed(observationFailure); }
            throw failure;
        }
    }
    private void tickWork() {
        if (done || finishing) { return; }
        EscrowTestEndpoints.requireEnabled(server);
        EscrowTags.require(++age <= DEADLINE_TICKS, "Governed benchmark exceeded its fixed 4000-tick deadline");
        if (!load.setup(recovery)) { return; }
        if (data == null) { data = ResourceEscrowSavedData.forTests(server); data.bind(server); }
        var coordinator = data.bind(server); var journal = RocketTransferSavedData.get(server);
        if (!journal.retryChecked(server) || !coordinator.operational()) { return; }
        if (recovery) { restart(); return; }
        if (!initialized) {
            EscrowTags.require(journal.entries().isEmpty() && data.ledger().issuedLaunchSerial() == 0,
                    "Governed benchmark needs a fresh world");
            ceiling = EscrowFlightBenchmarkCeiling.create();
            for (int i = 0; i < 8; i++) {
                var pos = EscrowFlightBenchmarkLoad.source(i); UUID id = EscrowFlightBenchmarkLoad.id("flight-" + i); logical.add(id);
                var rocket = EscrowRocketAuditProbe.createRocket(load.earth, id, true, new RocketPosition(pos.getX(), pos.getY(), pos.getZ()));
                var flight = rocket.flightData().orElseThrow();
                rocket.updateFlightData(flight.withFuel(flight.fuel().withFluidBank(false,
                        ResourceLocation.tryParse("minecraft:water"), null, 1000), load.earth.getGameTime()));
                var tag = rocket.saveWithoutId(new CompoundTag());
                tag.getCompound("RocketEntityData").put("resource_escrow", new RocketEscrowSection(data.ledger().id(), 0, List.of()).encode());
                rocket.load(tag); EscrowTags.require(load.earth.addFreshEntity(rocket), "Benchmark source publication refused"); sources.add(rocket);
            }
            competing = new EscrowFlightBenchmarkWork(server, data, metrics); competing.create();
            initialized = true;
        }
        competing.tick();
        if (!measuring) {
            if (!competing.ready() || ++warmup < WARMUP_TICKS) { return; }
            EscrowTags.require(server.saveEverything(false, true, true), "Benchmark baseline save refused");
            // Arm at END, then begin work on the next fully observed START-to-END tick.
            measuring = true; metrics.start(); return;
        }
        if (elapsed == 0) {
            competing.start(); load.assemble();
            AdvancedRocketryCommunity.LOGGER.info("ARCE_GOVERNED_BENCH_STARTED players=2 oxygen=16 stations=10 tasks=100");
        }
        elapsed++;
        if (elapsed % 100 == 1) {
            long allocated = EscrowFlightBenchmarkMetrics.allocated(), start = System.nanoTime();
            var encoded = EscrowCodec.encode(ceiling);
            metrics.sample("root_1mib_encode_main", System.nanoTime() - start);
            metrics.sample("root_1mib_allocation_bytes", EscrowFlightBenchmarkMetrics.allocated() - allocated);
            EscrowTags.require(io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketNbtSize.uncompressedBytes(encoded)
                    == EscrowLimits.ROOT_BYTES, "Ceiling encode changed size");
        }
        for (int i = 0; i < 8; i++) { if (!requested[i]) { launch(i); } }
        peakConcurrent = Math.max(peakConcurrent, (int) data.ledger().armedFlights().stream().filter(arm -> logical.contains(arm.logical())).count());
        if (elapsed % 200 == 0) {
            load.refill(); savedTick = server.getTickCount(); long start = System.nanoTime();
            EscrowTags.require(server.saveEverything(false, true, true), "Benchmark periodic save refused");
            metrics.sample("autosave_main", System.nanoTime() - start); saves++;
        }
        for (int i = 0; i < 8; i++) {
            UUID flightId = logical.get(i);
            if (requested[i] && !completed[i] && journal.findByLogicalRocket(flightId).isEmpty()
                    && data.ledger().armedFlights().stream().noneMatch(arm -> arm.logical().equals(flightId))) {
                var rocket = survivor(i);
                if (rocket != null) { checkSurvivor(rocket); completed[i] = true; metrics.sample("flight_handoff_latency", System.nanoTime() - requestedAt[i]); }
            }
        }
        if (elapsed >= MEASURE_TICKS && allCompleted() && competing.done() && coordinator.writerIdle()) {
            EscrowTags.require(accepted == 8 && peakConcurrent == 8 && load.assembled() && load.activeVents() == 16,
                    "Benchmark did not retain the required concurrent load");
            finishing = true;
        }
    }
    private void failureSnapshot(RuntimeException failure) throws java.io.IOException {
        metrics.stop();
        var root = server.getWorldPath(LevelResource.ROOT);
        metrics.write(root.resolve("governed-benchmark.csv"));
        var report = new com.google.gson.JsonObject();
        report.addProperty("status", "FAILED_PARTIAL_OBSERVATION"); report.addProperty("error", failure.toString());
        report.addProperty("age", age); report.addProperty("elapsed", elapsed); report.addProperty("accepted", accepted);
        report.addProperty("peak_concurrent", peakConcurrent); report.addProperty("saves", saves);
        report.addProperty("all_flights_completed", allCompleted());
        report.addProperty("competing_done", competing != null && competing.done());
        var gson = new com.google.gson.GsonBuilder().setPrettyPrinting().create();
        report.add("load", gson.toJsonTree(load.diagnostics()));
        if (data != null) {
            var journal = RocketTransferSavedData.get(server);
            report.addProperty("journal_acknowledged", journal.acknowledgedCurrent());
            report.addProperty("ledger_operational", data.bind(server).operational());
            report.addProperty("writer_idle", data.bind(server).writerIdle());
            report.addProperty("records", data.ledger().records().size());
            report.addProperty("armed", data.ledger().armedFlights().size());
            report.add("journal_phases", gson.toJsonTree(journal.entries().stream().map(record -> record.phase().name()).toList()));
            report.add("flight_readiness", gson.toJsonTree(journal.entries().stream().map(record ->
                    java.util.Map.of("logical", record.logicalRocketId().toString(),
                            "classification", EscrowGovernedDispatch.decide(data.ledger(), record).toString(),
                            "source", readiness(record.sourceSnapshot()), "destination", readiness(record.destinationSnapshot()))).toList()));
            report.add("sources", gson.toJsonTree(sources.stream().map(rocket -> java.util.Map.of(
                    "logical", rocket.assemblyTransactionId().orElseThrow().toString(), "ticks", rocket.tickCount,
                    "state", rocket.flightData().orElseThrow().state().name())).toList()));
        }
        report.add("generation_topology", gson.toJsonTree(metrics.topology()));
        report.add("checkpoints", gson.toJsonTree(metrics.checkpoints()));
        Files.writeString(root.resolve("governed-benchmark.json"), gson.toJson(report));
        AdvancedRocketryCommunity.LOGGER.info("ARCE_GOVERNED_BENCH_FAILED age={} elapsed={} accepted={} saves={} load={}",
                age, elapsed, accepted, saves, load.diagnostics());
    }
    private java.util.List<java.util.Map<String, Object>> readiness(
            io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketStructureSnapshot snapshot) {
        var level = server.getLevel(net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.DIMENSION,
                snapshot.sourceDimension()));
        var region = io.github.sunthemoon.advancedrocketrycommunity.rocket.transaction.RocketRegion.fromSnapshot(snapshot);
        var result = new java.util.ArrayList<java.util.Map<String, Object>>();
        // Same bounded discovery margin as RocketTransferEntities.endAlreadyLoaded; observation only.
        for (int x = (region.minimum().x() - 1) >> 4; x <= (region.maximum().x() + 2) >> 4; x++) {
            for (int z = (region.minimum().z() - 1) >> 4; z <= (region.maximum().z() + 2) >> 4; z++) {
                EscrowTags.require(result.size() < 16, "Benchmark diagnostic footprint exceeded its bound");
                result.add(java.util.Map.of("x", x, "z", z, "loaded", level != null
                        && level.areEntitiesLoaded(net.minecraft.world.level.ChunkPos.asLong(x, z)), "entity_ticking", level != null
                        && level.isPositionEntityTicking(new net.minecraft.core.BlockPos(x << 4, snapshot.sourceOrigin().y(), z << 4))));
            }
        }
        return result;
    }
    private void launch(int i) {
        var rocket = sources.get(i); var from = rocket.snapshot().orElseThrow(); var pos = EscrowFlightBenchmarkLoad.destination(i);
        long now = load.earth.getGameTime(); var fuel = rocket.flightData().orElseThrow().fuel();
        var to = from.relocated(UUID.randomUUID(), load.moon.dimension().location(), new RocketPosition(pos.getX(), pos.getY(), pos.getZ()), now);
        var plan = new RocketFlightPlan(3, UUID.randomUUID(), RocketFlightPlanner.EARTH.bodyId(), RocketFlightPlanner.MOON.bodyId(),
                from.sourceDimension(), to.sourceDimension(), 500, now).withPropulsionDebit(new RocketPropulsionDebit(500, 0,
                fuel.propulsion().orElseThrow().revision(), fuel.resourceSignature(), RocketNativeResources.hash("governed benchmark")));
        requested[i] = EscrowGovernedPreparation.begin(rocket, rocket.flightData().orElseThrow().withPlan(plan).startCountdown(now), to,
                record -> load.loaded(), record -> accepted++, () -> { throw new IllegalStateException("Benchmark lost an F1/F2 callback"); });
        if (requested[i]) { requestedAt[i] = System.nanoTime(); }
    }
    private RocketEntity survivor(int index) {
        var pos = EscrowFlightBenchmarkLoad.destination(index); var id = EscrowFlightBenchmarkLoad.id("flight-" + index);
        var found = load.moon.getEntitiesOfClass(RocketEntity.class, new AABB(pos).inflate(8)).stream()
                .filter(rocket -> rocket.assemblyTransactionId().filter(id::equals).isPresent()).toList();
        EscrowTags.require(found.size() <= 1, "Benchmark duplicated a survivor");
        return found.isEmpty() ? null : found.get(0);
    }
    private static void checkSurvivor(RocketEntity rocket) {
        var flight = rocket.flightData().orElseThrow();
        EscrowTags.require(rocket.operational() && flight.state() == RocketFlightState.LANDED && flight.fuel().totalAmount() == 500
                && flight.fuel().committedDebits().size() == 1 && RocketEscrowGate.idle(rocket), "Benchmark survivor changed authority/resources");
    }
    private boolean allCompleted() { for (boolean value : completed) { if (!value) { return false; } } return true; }
    private void restart() {
        var journal = RocketTransferSavedData.get(server);
        EscrowTags.require(journal.entries().isEmpty() && data.ledger().armedFlights().isEmpty()
                && data.ledger().issuedLaunchSerial() == 8 && data.ledger().records().isEmpty(), "Restart retained or replayed benchmark work");
        for (int i = 0; i < 8; i++) { var rocket = survivor(i); if (rocket == null) { return; } checkSurvivor(rocket); }
        EscrowTags.require(load.assembled(), "Restart lost the 2048-block assembly");
        AdvancedRocketryCommunity.LOGGER.info("ARCE_GOVERNED_BENCH_RESTART survivors=8 player_ticks={} assembly=2048", load.tickDelta()); done = true;
    }
    void startTick() { metrics.startTick(); }
    void endTick() {
        metrics.endTick(savedTick == server.getTickCount());
        if (!finishing || done) { return; }
        metrics.stop();
        try {
            var root = server.getWorldPath(LevelResource.ROOT);
            metrics.write(root.resolve("governed-benchmark.csv"));
            var report = new com.google.gson.JsonObject();
            report.addProperty("schema_version", 1); report.addProperty("ticks", elapsed);
            report.addProperty("players", server.getPlayerCount()); report.addProperty("player_tick_delta", load.tickDelta());
            report.addProperty("active_oxygen", load.activeVents()); report.addProperty("stations", 10); report.addProperty("satellite_tasks", 100);
            report.addProperty("flights_completed", accepted); report.addProperty("peak_concurrent", peakConcurrent);
            report.addProperty("assembly_blocks", load.assembled() ? 2048 : 0); report.addProperty("dimension_transfers", 8);
            report.addProperty("periodic_saves", saves); report.addProperty("ordinary_retired", competing.done());
            report.addProperty("root_encode_bytes", EscrowLimits.ROOT_BYTES);
            report.addProperty("root_encode_kind", "encode-only synthetic ceiling; never admitted or installed");
            report.add("generation_topology", new com.google.gson.Gson().toJsonTree(metrics.topology()));
            report.add("checkpoints", new com.google.gson.Gson().toJsonTree(metrics.checkpoints()));
            Files.writeString(root.resolve("governed-benchmark.json"), new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(report));
        } catch (java.io.IOException failure) { throw new IllegalStateException("Benchmark export failed", failure); }
        AdvancedRocketryCommunity.LOGGER.info("ARCE_GOVERNED_BENCH_DONE ticks={} flights=8 peak=8 saves={}", elapsed, saves); done = true;
    }
}
