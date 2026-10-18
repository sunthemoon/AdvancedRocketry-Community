package io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.persistence;

import com.google.gson.GsonBuilder;
import io.github.sunthemoon.advancedrocketrycommunity.AdvancedRocketryCommunity;
import io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.EscrowTestEndpoints;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.server.MinecraftServer;

/** Owner-thread-only observations. No world lookup, locks, callbacks or authority in the recorder. */
public final class RocketJournalNativeProbe {
    public static final String FILE = "rocket-journal-probe.json";
    static final boolean REQUESTED = Boolean.getBoolean(EscrowTestEndpoints.HOOKS)
            && List.of("OBSERVE", "HOLD").contains(System.getProperty(RocketJournalFailurePolicy.PROPERTY, ""));
    static final int MAX_RECORDS = 128, MAX_EVENTS_PER_RECORD = 4_096, MAX_EVENTS = 32_768, MAX_TICKS = 12_000;
    private final Map<UUID, List<Event>> records = new LinkedHashMap<>();
    private final List<Event> global = new ArrayList<>();
    private final List<Tick> ticks = new ArrayList<>();
    private final long began = System.nanoTime();
    private long tick, tickStart;
    private int eventCount;
    private boolean capped, written;

    record Event(long nanos, long tick, String kind, long sequence, String detail, long value) { }
    record Tick(long tick, long nanos) { }

    static void requireGate(MinecraftServer server) {
        String mode = System.getProperty(RocketJournalFailurePolicy.PROPERTY, "");
        if (!Boolean.getBoolean(EscrowTestEndpoints.HOOKS) || !(mode.equals("OBSERVE") || mode.equals("HOLD"))) {
            throw new IllegalStateException("Journal observation requires releaseTestHooks and OBSERVE or HOLD policy");
        }
        EscrowTestEndpoints.requireEnabled(server);
    }

    public void event(UUID logical, String kind, long sequence, String detail, long value) {
        if (written) { return; }
        if (eventCount >= MAX_EVENTS || ticks.size() >= MAX_TICKS) { capped = true; return; }
        List<Event> events = global;
        if (logical != null) {
            events = records.get(logical);
            if (events == null) {
                if (records.size() >= MAX_RECORDS) { capped = true; return; }
                events = new ArrayList<>(); records.put(logical, events);
            }
        }
        if (events.size() >= MAX_EVENTS_PER_RECORD) { capped = true; return; }
        // Every caller supplies a fixed kind/enum or UUID, never player-controlled strings.
        if (kind.length() > 64 || detail.length() > 160) { capped = true; return; }
        events.add(new Event(System.nanoTime() - began, tick, kind, sequence, detail, value)); eventCount++;
    }

    void tickStart(long now) { if (!written) { tick = now; tickStart = System.nanoTime(); } }
    void tickEnd() {
        if (written || tickStart == 0) { return; }
        if (ticks.size() >= MAX_TICKS) { capped = true; }
        else { ticks.add(new Tick(tick, System.nanoTime() - tickStart)); }
        tickStart = 0;
    }

    Map<String, Object> snapshot() {
        return Map.of("schema", 1, "capped", capped, "records", records, "global", global, "ticks", ticks,
                "event_count", eventCount, "elapsed_nanos", System.nanoTime() - began,
                "caps", Map.of("records", MAX_RECORDS, "events_per_record", MAX_EVENTS_PER_RECORD,
                        "events", MAX_EVENTS, "ticks", MAX_TICKS));
    }

    void write(Path directory) throws IOException {
        if (written) { return; }
        Path file = directory.resolve(FILE);
        try (var output = Files.newBufferedWriter(file, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)) {
            new GsonBuilder().create().toJson(snapshot(), output);
        }
        written = true;
        AdvancedRocketryCommunity.LOGGER.info("ARCE_JOURNAL_PROBE_WRITTEN file={} capped={}", FILE, capped);
    }
}
