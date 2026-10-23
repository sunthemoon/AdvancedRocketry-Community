package io.github.sunthemoon.advancedrocketrycommunity.resourceescrow;

import io.github.sunthemoon.advancedrocketrycommunity.AdvancedRocketryCommunity;
import java.nio.file.Files;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/** Bounded, test-artifact-only save attribution. Does not schedule saves, retries or operator actions. */
final class CargoNativeSaveTrace {
    static final String PROPERTY = "advancedrocketrycommunity.cargoSaveTrace";
    static final int TAIL_TICKS = 2400;
    private final MinecraftServer server;
    private final List<Map<String, Object>> events = new ArrayList<>();
    private final Map<AtomicLong, Integer> watches = new IdentityHashMap<>();
    private final Map<UUID, Map<String, Object>> operations = new LinkedHashMap<>();
    private final Map<UUID, Integer> dispatches = new LinkedHashMap<>();
    private final List<Map<String, Object>> snapshots = new ArrayList<>();
    private UUID context;
    private int captures, loadEnd = -1;
    private boolean done;
    private String error;

    CargoNativeSaveTrace(MinecraftServer server) {
        EscrowTestEndpoints.requireEnabled(server);
        if (!Boolean.getBoolean(PROPERTY)) { throw new IllegalStateException("Save trace flag required"); }
        this.server = server;
        EscrowRuntime.of(server).monitor().observerForTests(server, this::observe);
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.register(this);
    }

    void attempt(EscrowReconciliationScheduler.Driver driver, EscrowOperation op,
            List<EscrowOperation> records, Consumer<EscrowReconciliationScheduler.Result> completion) {
        operations.computeIfAbsent(op.id(), ignored -> Map.of("operation", op.id(), "sequence", op.sequence(),
                "kind", op.kind().name(), "device", op.device().identity(),
                "source", op.source().toString(), "destination", op.destination().toString()));
        dispatches.merge(op.id(), 1, Integer::sum);
        UUID previous = context; context = op.id(); int before = captures;
        // This array belongs to this dispatch, including its asynchronous callback.
        int[] captured = {0}; boolean[] returned = {false};
        try {
            driver.attempt(op, records, result -> {
                int count = returned[0] ? captured[0] : captures - before;
                completion.accept(result);
                if (count != 0 || result.reason() != EscrowReason.NONE || result.proved()) {
                    var row = row("RESULT"); row.put("operation", op.id()); row.put("captures", count);
                    row.put("reason", result.reason().name()); row.put("proved", result.proved());
                    row.put("retry", retry(op.id())); add(row);
                    if (result.reason() != EscrowReason.NONE || result.proved()) {
                        AdvancedRocketryCommunity.LOGGER.info("ARCE_CARGO_SAVE_RESULT {}", new com.google.gson.Gson().toJson(row));
                    }
                }
            });
        } finally { captured[0] = captures - before; returned[0] = true; context = previous; }
    }

    private Map<String, Object> retry(UUID id) {
        var view = EscrowRuntime.of(server).reconciliationScheduler().view(id);
        return Map.of("phase", view.phase(), "reason", view.reason(), "failures", view.failures(),
                "next_tick", view.nextTick(), "active", view.active());
    }

    private void observe(EscrowSaveMonitor.Observation value) {
        var row = row(value.cause()); var pos = new ChunkPos(value.slot().chunk());
        row.put("watch", watches.computeIfAbsent(value.counter(), ignored -> watches.size() + 1));
        row.put("generation", value.generation()); row.put("level", value.slot().level());
        row.put("entities", value.slot().entities()); row.put("chunk_x", pos.x); row.put("chunk_z", pos.z);
        row.put("operation", context == null ? "" : context.toString());
        row.put("stack", Arrays.stream(Thread.currentThread().getStackTrace()).skip(2).limit(24).map(Object::toString).toList());
        if ("CAPTURE".equals(value.cause())) { captures++; }
        add(row);
    }

    void checkpoint(String label) {
        if (!server.isSameThread() || done || context == null) { return; }
        if (label.startsWith("C3_") || label.startsWith("C4_") || label.startsWith("C7_") || label.startsWith("C8_")) {
            var row = row("CHECKPOINT"); row.put("label", label); row.put("operation", context); add(row);
        }
    }

    private Map<String, Object> row(String type) {
        var row = new LinkedHashMap<String, Object>(); row.put("event", events.size());
        row.put("tick", server.getTickCount()); row.put("type", type); return row;
    }
    private void add(Map<String, Object> row) {
        if (events.size() >= 32768) { error = "Observation capacity exceeded"; return; }
        events.add(row);
    }

    private void snapshot(String label) {
        var data = ResourceEscrowSavedData.existing(server);
        var row = row("SNAPSHOT"); row.put("label", label); row.put("ledger", EscrowCodec.encode(data.ledger()).toString());
        row.put("records", data.ledger().records().stream().map(op -> Map.of("operation", op.id(),
                "phase", op.phase().name(), "reason", op.reason().name(), "retry", retry(op.id()))).toList());
        snapshots.add(row);
    }

    @SubscribeEvent public void tick(TickEvent.ServerTickEvent event) {
        if (done || event.getServer() != server || event.phase != TickEvent.Phase.END) { return; }
        var reference = CargoNativeBenchmark.field(EscrowRuntime.of(server), "flightBenchmark");
        if (reference == null) { return; }
        if (loadEnd < 0 && (boolean) CargoNativeBenchmark.field(reference, "done")) {
            loadEnd = server.getTickCount(); snapshot("load_end");
        }
        // Diagnostic tail is separate from the unchanged benchmark window and verdict.
        if (loadEnd >= 0 && server.getTickCount() - loadEnd >= TAIL_TICKS) {
            snapshot("tail_end"); done = true;
            EscrowRuntime.of(server).monitor().observerForTests(server, null);
            var report = new LinkedHashMap<String, Object>(); report.put("schema_version", 1);
            report.put("source_commit", System.getProperty("advancedrocketrycommunity.benchmarkCommit"));
            report.put("load_end_tick", loadEnd); report.put("tail_ticks", TAIL_TICKS);
            report.put("status", error == null ? "OBSERVED" : "INCOMPLETE"); report.put("error", error);
            report.put("operations", operations.values()); report.put("dispatches", dispatches);
            report.put("snapshots", snapshots); report.put("events", events);
            try { Files.writeString(server.getWorldPath(LevelResource.ROOT).resolve("cargo-save-trace.json"),
                    new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(report)); }
            catch (java.io.IOException failure) { throw new IllegalStateException("Save trace export failed", failure); }
            AdvancedRocketryCommunity.LOGGER.info("ARCE_CARGO_SAVE_TRACE_DONE status={}", report.get("status"));
        }
    }
}
