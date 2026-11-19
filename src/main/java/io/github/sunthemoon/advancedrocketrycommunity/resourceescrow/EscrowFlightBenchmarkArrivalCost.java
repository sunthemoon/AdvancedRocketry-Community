package io.github.sunthemoon.advancedrocketrycommunity.resourceescrow;

import io.github.sunthemoon.advancedrocketrycommunity.rocket.entity.RocketEntity;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFlightLimits;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFlightState;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketTransferPhase;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketTransferRecord;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.persistence.RocketTransferSavedData;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.server.RocketRuntime;
import java.io.IOException;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;

/** Opt-in N6 attribution, never installed in the normal workload or product dispatch. */
final class EscrowFlightBenchmarkArrivalCost {
    static final String SCENARIO = "arrival-second-call-cost";
    private final List<String> rows = new ArrayList<>();
    private final Map<Integer, Integer> concurrency = new java.util.TreeMap<>();
    private final Field bindings, observation, authority, lastTime, phaseBase, levelBase;
    private final MethodHandle boundary;
    private final Object bays;
    private int comparedResults;

    static boolean selected(String scenario) { return SCENARIO.equals(scenario); }
    static String lifecycleScenario(String scenario) { return selected(scenario) ? "start" : scenario; }

    EscrowFlightBenchmarkArrivalCost(MinecraftServer server) {
        EscrowTestEndpoints.requireEnabled(server);
        EscrowTags.require(selected(System.getProperty("advancedrocketrycommunity.escrowScenario")), "Attribution scenario is disabled");
        try {
            bindings = field(EscrowGovernedArrival.class, "bindings");
            var binding = Class.forName(EscrowGovernedArrival.class.getName() + "$Binding");
            observation = field(binding, "observation"); authority = field(binding, "authority");
            lastTime = field(binding, "lastTime"); phaseBase = field(binding, "phaseBase"); levelBase = field(binding, "levelBase");
            // Use the installed dispatcher's exact bay instance and boundary, without adding a product test API.
            var installed = RocketRuntime.class.getDeclaredMethod("installedTransfers"); installed.setAccessible(true);
            Object transfers = installed.invoke(null);
            Object recovery = field(transfers.getClass(), "recovery").get(transfers);
            bays = field(recovery.getClass(), "bays").get(recovery);
            var type = Class.forName("io.github.sunthemoon.advancedrocketrycommunity.rocket.server.RocketGovernedLaunchBoundary");
            var method = type.getDeclaredMethod("allows", MinecraftServer.class, RocketTransferSavedData.class,
                    RocketTransferRecord.class, RocketEntity.class, RocketEntity.class, bays.getClass());
            method.setAccessible(true);
            boundary = MethodHandles.lookup().unreflect(method).asType(MethodType.methodType(boolean.class,
                    MinecraftServer.class, RocketTransferSavedData.class, RocketTransferRecord.class,
                    RocketEntity.class, RocketEntity.class, Object.class));
        } catch (ReflectiveOperationException failure) { throw new IllegalStateException("Cannot bind benchmark arrival observation", failure); }
    }

    private static Field field(Class<?> type, String name) throws NoSuchFieldException {
        var field = type.getDeclaredField(name); field.setAccessible(true); return field;
    }

    private boolean allows(MinecraftServer server, RocketTransferSavedData journal, RocketTransferRecord record, RocketEntity arrival) {
        try { return (boolean) boundary.invokeExact(server, journal, record, (RocketEntity) null, arrival, bays); }
        catch (RuntimeException | Error failure) { throw failure; }
        catch (Throwable failure) { throw new IllegalStateException("Benchmark boundary invocation failed", failure); }
    }

    void endTick(MinecraftServer server, List<UUID> logical) {
        EscrowTestEndpoints.requireEnabled(server);
        var runtime = EscrowRuntime.of(server); var arrivals = runtime.arrivals();
        var journal = RocketTransferSavedData.get(server);
        int concurrent = 0;
        for (var record : journal.entries()) {
            if (!logical.contains(record.logicalRocketId()) || record.phase() != RocketTransferPhase.COMMITTED) { continue; }
            var sourceLevel = server.getLevel(ResourceKey.create(Registries.DIMENSION, record.sourceSnapshot().sourceDimension()));
            var level = server.getLevel(ResourceKey.create(Registries.DIMENSION, record.destinationSnapshot().sourceDimension()));
            if (sourceLevel == null || sourceLevel.getEntity(record.sourceEntityId()) != null || level == null
                    || !(level.getEntity(record.nativeTransfer().orElseThrow().armed().destination()) instanceof RocketEntity rocket)
                    || rocket.flightData().orElseThrow().state() != RocketFlightState.DESCENT || !arrivals.bound(record.transferId())) { continue; }
            concurrent++;
            try {
                var map = (Map<?, ?>) bindings.get(arrivals); var binding = map.get(record.transferId());
                // Serialization or invalidation since dispatch needs a cold rebind, not the N6 warm path.
                if (observation.getLong(binding) != rocket.governedObservation()
                        || !((EscrowGovernedDispatch.Authority) authority.get(binding)).valid()) { continue; }
                var position = rocket.position(); long observed = rocket.governedObservation();
                var flight = rocket.flightData().orElseThrow(); long captures = rocket.governedCaptures();
                var frame = EscrowGovernedEnvelope.frame(rocket);
                var hold = runtime.executorHolds().reason(record.transferId());
                // No dispatcher hook: infer its result only when this binding's clock was visited in this tick.
                var expected = expectedResult(lastTime.getLong(binding) == level.getGameTime(),
                        Math.addExact(phaseBase.getLong(binding), level.getGameTime() - levelBase.getLong(binding)),
                        flight.stateStartedGameTime());
                BooleanSupplier checkedBoundary = () -> allows(server, journal, record, rocket);
                long start = System.nanoTime();
                var result = arrivals.tick(server, journal, record, rocket, checkedBoundary);
                long nanos = System.nanoTime() - start;
                requireUnchanged(observed == rocket.governedObservation() && position.equals(rocket.position())
                        && flight == rocket.flightData().orElseThrow() && frame.equals(EscrowGovernedEnvelope.frame(rocket))
                        && captures == rocket.governedCaptures() && map.get(record.transferId()) == binding
                        && hold.equals(runtime.executorHolds().reason(record.transferId())) && hold.isEmpty(), result);
                if (expected != null) {
                    EscrowTags.require(result == expected, "Attribution changed known dispatcher result");
                    comparedResults++;
                }
                EscrowTags.require(rows.size() < EscrowFlightBenchmarkMetrics.MAX_SAMPLES, "Arrival attribution sample bound exceeded");
                rows.add(server.getTickCount() + "," + record.logicalRocketId() + "," + nanos + "," + result + "," + (expected == null ? "" : expected));
            } catch (ReflectiveOperationException failure) { throw new IllegalStateException("Cannot inspect warm arrival binding", failure); }
        }
        concurrency.merge(concurrent, 1, Math::addExact);
    }

    static void requireUnchanged(boolean unchanged, EscrowGovernedArrival.Result result) {
        EscrowTags.require(unchanged && (result == EscrowGovernedArrival.Result.DESCENDING
                || result == EscrowGovernedArrival.Result.READY_TO_LAND), "Arrival attribution changed warm state or added a hold");
    }

    static EscrowGovernedArrival.Result expectedResult(boolean visitedThisTick, long now, long started) {
        if (!visitedThisTick) { return null; }
        EscrowTags.require(now >= started, "Attribution descent clock moved backwards");
        return now - started >= RocketFlightLimits.DESCENT_TICKS
                ? EscrowGovernedArrival.Result.READY_TO_LAND : EscrowGovernedArrival.Result.DESCENDING;
    }

    Map<String, Object> report() {
        EscrowTags.require(!rows.isEmpty() && comparedResults > 0, "Arrival attribution lacks warm calls or known dispatcher results");
        return Map.of("timed_calls", rows.size(), "known_results_compared", comparedResults,
                "concurrent_committed_descents_per_tick", Map.copyOf(concurrency), "binding_capacity", RocketFlightLimits.MAX_ACTIVE_TRANSFERS);
    }

    void write(Path root) throws IOException {
        var lines = new ArrayList<String>(); lines.add("tick,logical,nanos,result,expected"); lines.addAll(rows);
        Files.write(root.resolve("arrival-second-call-cost.csv"), lines);
    }
}
