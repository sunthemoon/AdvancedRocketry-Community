package io.github.sunthemoon.advancedrocketrycommunity.resourceescrow;

import com.google.gson.GsonBuilder;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.persistence.RocketTransferSavedData;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReferenceArray;
import net.minecraft.server.MinecraftServer;

/** Bounded, lock-free native observer; no world state, authority or callbacks are retained. */
final class EscrowConsumerTimingProbe implements EscrowTimingSink {
    static final String PROPERTY = "advancedrocketrycommunity.escrowConsumerTiming";
    static final String FILE = "escrow-consumer-timing.json";
    private static final boolean REQUESTED = Boolean.getBoolean(EscrowTestEndpoints.HOOKS) && Boolean.getBoolean(PROPERTY);
    static final int LIMIT = 32_768;
    private final AtomicReferenceArray<Event> events = new AtomicReferenceArray<>(LIMIT);
    private final AtomicInteger count = new AtomicInteger();
    private volatile boolean capped;
    record Event(String phase, long nanos, long value) { }

    static EscrowTimingSink select(boolean hooks, boolean requested, Runnable requireGate) {
        if (!hooks || !requested) { return EscrowTimingSink.NONE; }
        requireGate.run();
        return new EscrowConsumerTimingProbe();
    }

    @Override public void sample(String phase, long value) {
        if (phase.length() > 160) { capped = true; return; }
        int index = count.getAndUpdate(previous -> Math.min(LIMIT, previous + 1));
        if (index >= LIMIT) { capped = true; return; }
        events.set(index, new Event(phase, System.nanoTime(), value));
    }
    @Override public void written(long generation, long nanos) { sample("ledger_generation_" + generation, nanos); }

    static void mark(MinecraftServer server, String phase) {
        if (!REQUESTED) { return; }
        var sink = EscrowRuntime.of(server).timings();
        if (!(sink instanceof EscrowConsumerTimingProbe)) { return; }
        sink.sample(phase, 0);
        var probe = RocketTransferSavedData.get(server).nativeProbe();
        if (probe != null) { probe.event(null, "consumer", 0, phase, 0); }
    }

    Map<String, Object> snapshot() {
        var rows = new ArrayList<Event>();
        for (int i = 0; i < count.get(); i++) {
            var event = events.get(i);
            if (event == null) { capped = true; } else { rows.add(event); }
        }
        return Map.of("schema", 1, "capped", capped, "events", rows, "limit", LIMIT);
    }

    void write(Path directory) {
        try (var output = Files.newBufferedWriter(directory.resolve(FILE), StandardOpenOption.CREATE_NEW)) {
            new GsonBuilder().create().toJson(snapshot(), output);
        } catch (IOException failure) { throw new IllegalStateException("Consumer timing export failed", failure); }
    }
}
