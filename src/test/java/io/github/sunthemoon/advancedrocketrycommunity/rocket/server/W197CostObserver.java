package io.github.sunthemoon.advancedrocketrycommunity.rocket.server;

import io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.EscrowBarrier;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Supplier;
import net.minecraft.server.MinecraftServer;

/** Diagnostic-only transparent port observer. No result, request, ticket, quota or deadline is replaced. */
final class W197CostObserver {
    final List<String> ticks = new ArrayList<>(List.of(
            "phase,round,tick,tick_ns,observed_ns,pending,active,vanilla_core_ns,forge_end_ns,late_start_interval_ns,end_epoch_ms"));
    final List<String> steps = new ArrayList<>(List.of("phase,round,tick,step,calls,exclusive_ns,max_inclusive_ns"));
    final List<String> proofs = new ArrayList<>(List.of(
            "round,index,admitted_tick,attempt,started_tick,terminal_tick,attempt_ticks,queue_ticks,slots,accepted"));
    final List<String> events = new ArrayList<>(List.of("phase,round,tick,index,event,value"));
    final Map<UUID, Boolean> prepared = new LinkedHashMap<>();
    final Map<UUID, Boolean> handedOff = new LinkedHashMap<>();
    final Map<String, long[]> costs = new LinkedHashMap<>();
    private final ArrayDeque<long[]> stack = new ArrayDeque<>();
    private final MinecraftServer server;
    String phase = "SETUP";
    int round, peakPending, peakActive, confirmedCaptures, refusedCaptures;
    long tickStart, endStart, observed;

    W197CostObserver(MinecraftServer server) { this.server = server; }

    // Earlier HIGHEST START listeners can already have delivered journal callbacks. Do not erase their costs here.
    void start() { tickStart = System.nanoTime(); }
    void beginEnd() { endStart = System.nanoTime(); }
    void reset() { observed = 0; costs.clear(); }

    static long fullTickNanos(long vanillaCore, long forgeEnd) {
        if (vanillaCore < 0 || forgeEnd < 0) { throw new IllegalArgumentException("Negative tick duration"); }
        return Math.addExact(vanillaCore, forgeEnd);
    }

    <T> T timed(String name, Supplier<T> action) {
        if (!server.isSameThread()) { throw new IllegalStateException("Cost observer left the server thread: " + name); }
        var frame = new long[] {System.nanoTime(), 0};
        stack.push(frame);
        try { return action.get(); }
        finally {
            long elapsed = System.nanoTime() - frame[0]; stack.pop();
            if (stack.isEmpty()) { observed += elapsed; } else { stack.peek()[1] += elapsed; }
            var cost = costs.computeIfAbsent(name, unused -> new long[3]);
            cost[0]++; cost[1] += elapsed - frame[1]; cost[2] = Math.max(cost[2], elapsed);
        }
    }

    void event(int index, String name, Object value) {
        events.add(phase + "," + round + "," + server.getTickCount() + "," + index + "," + name + "," + value);
    }

    void end(RocketOrdinaryRetirement coordinator) {
        long now = System.nanoTime(), lateStartInterval = now - tickStart, forgeEnd = now - endStart;
        // Forge 47.4.10 records vanilla tickTimes before posting END. Its timer starts BEFORE posting START,
        // so it includes journal polling by earlier same-priority START listeners that our late timer cannot see.
        long vanillaCore = server.tickTimes[server.getTickCount() % server.tickTimes.length];
        long elapsed = fullTickNanos(vanillaCore, forgeEnd);
        int pending = coordinator.size(), active = coordinator.activeAttempts();
        peakPending = Math.max(peakPending, pending); peakActive = Math.max(peakActive, active);
        if (measuring()) {
            ticks.add(phase + "," + round + "," + server.getTickCount() + "," + elapsed + "," + observed + "," + pending + "," + active
                    + "," + vanillaCore + "," + forgeEnd + "," + lateStartInterval + "," + System.currentTimeMillis());
            costs.forEach((name, cost) -> steps.add(phase + "," + round + "," + server.getTickCount() + "," + name
                    + "," + cost[0] + "," + cost[1] + "," + cost[2]));
        }
        reset();
    }

    boolean measuring() {
        return phase.equals("BASELINE") || phase.equals("PREPARATION") || phase.equals("PROOF") || phase.equals("DRAIN");
    }

    /** Installed entry points enqueue demand without starting it; attach before the following coordinator tick. */
    @SuppressWarnings("unchecked")
    void attach(RocketOrdinaryRetirement coordinator, UUID logical, int index) {
        Map<UUID, Object> pending = RocketSourceRetirementFixtures.field(coordinator, "pending");
        var entry = pending.get(logical);
        if (entry == null) { throw new IllegalStateException("No admitted demand " + index); }
        RocketOrdinaryRetirement.Port real = RocketSourceRetirementFixtures.field(entry, "port");
        if (!(real instanceof RocketI6Retirement)) { throw new IllegalStateException("Not the production I6 port"); }
        long admitted = server.getTickCount();
        var proxy = Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[] {RocketOrdinaryRetirement.Port.class},
                (ignored, method, args) -> {
                    Object[] forwarded = args == null ? null : args.clone();
                    if (method.getName().equals("capture")) {
                        Consumer<EscrowBarrier.Result> callback = (Consumer<EscrowBarrier.Result>) args[4];
                        // The validator also runs on the IO worker. Leave it untouched: only owner-thread cost is timed.
                        forwarded[4] = (Consumer<EscrowBarrier.Result>) result -> timed("capture.callback", () -> {
                            if (result.confirmed()) { confirmedCaptures++; } else { refusedCaptures++; }
                            event(index, "capture_result", result.reason()); callback.accept(result); return null;
                        });
                    }
                    try {
                        Object result = timed(method.getName(), () -> {
                            try { return method.invoke(real, forwarded); }
                            catch (InvocationTargetException failure) { throw new InvocationFailure(failure.getCause()); }
                            catch (IllegalAccessException failure) { throw new IllegalStateException(failure); }
                        });
                        if (method.getName().equals("preparationReady") && Boolean.TRUE.equals(result)) {
                            prepared.put(logical, true); event(index, "preparation_ready", server.getTickCount() - admitted + 1);
                        }
                        if (method.getName().equals("terminal")) {
                            int attempt = RocketSourceRetirementFixtures.field(entry, "attempts");
                            long started = RocketSourceRetirementFixtures.field(entry, "started");
                            List<?> census = RocketSourceRetirementFixtures.field(entry, "census");
                            int now = server.getTickCount();
                            proofs.add(round + "," + index + "," + admitted + "," + attempt + "," + started + "," + now
                                    + "," + (now - started + 1) + "," + (now - admitted + 1) + "," + census.size() + "," + result);
                            if (Boolean.TRUE.equals(result)) { handedOff.put(logical, true); }
                        }
                        if (method.getName().equals("capture")) { peakActive = Math.max(peakActive, coordinator.activeAttempts()); }
                        if (method.getName().equals("releaseTickets")) {
                            event(index, "release_reason", RocketSourceRetirementFixtures.field(entry, "reason"));
                        }
                        if (method.getName().equals("requestCurrent") && Boolean.FALSE.equals(result)) {
                            event(index, "request_not_current", true);
                        }
                        return result;
                    } catch (InvocationFailure failure) { throw failure.getCause(); }
                });
        try {
            var field = entry.getClass().getDeclaredField("port"); field.setAccessible(true); field.set(entry, proxy);
        } catch (ReflectiveOperationException failure) { throw new IllegalStateException(failure); }
    }

    private static final class InvocationFailure extends RuntimeException {
        InvocationFailure(Throwable cause) { super(cause); }
    }
}
