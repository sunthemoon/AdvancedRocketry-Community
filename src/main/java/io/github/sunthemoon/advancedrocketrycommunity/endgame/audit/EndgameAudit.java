package io.github.sunthemoon.advancedrocketrycommunity.endgame.audit;

import io.github.sunthemoon.advancedrocketrycommunity.endgame.model.EndgameCode;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.model.EndgameLimits;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * ADR-054 section 13: one {@code ARCE_ENDGAME} line of at most 512 bytes per state-changing intent, refusal code
 * change, operator command, quarantine or ledger anomaly; at most 64 lines per tick, the rest counted and reported
 * with the next summary; routine work aggregated into one summary line per system every 1,200 ticks; the last 512
 * lines kept in memory (never persisted, cleared at stop) for {@code /arce endgame audit}.
 */
public final class EndgameAudit {
    private static final int MAX_PROTECTION_KEYS = 4096;

    private final Consumer<String> sink;
    private final Deque<String> ring = new ArrayDeque<>();
    private final Map<String, Map<String, Long>> counters = new TreeMap<>();
    private final Map<String, Long> protectionNotices = new LinkedHashMap<>(16, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, Long> eldest) {
            return size() > MAX_PROTECTION_KEYS;
        }
    };
    private long tick = Long.MIN_VALUE;
    private int linesThisTick;
    private long suppressed;
    private long lastSummary;

    public EndgameAudit(Consumer<String> sink) {
        this.sink = Objects.requireNonNull(sink, "sink");
    }

    /**
     * Writes one line {@code ARCE_ENDGAME system action result device owner actor fields}; returns false when the
     * per-tick limit suppressed it (it is then counted).
     */
    public boolean line(long gameTime, String system, String action, String result, UUID device, UUID owner,
                        UUID actor, String fields) {
        if (gameTime != tick) {
            tick = gameTime;
            linesThisTick = 0;
        }
        if (linesThisTick >= EndgameLimits.AUDIT_LINES_PER_TICK) {
            suppressed++;
            return false;
        }
        linesThisTick++;
        String text = bound("ARCE_ENDGAME system=" + token(system) + " action=" + token(action) + " result="
                + token(result) + " device=" + id(device) + " owner=" + id(owner) + " actor=" + id(actor)
                + (fields == null || fields.isEmpty() ? "" : " " + fields));
        sink.accept(text);
        ring.addLast(text);
        while (ring.size() > EndgameLimits.AUDIT_RING_LINES) {
            ring.removeFirst();
        }
        return true;
    }

    /** A protection refusal is audited once per device and code per 1,200 ticks (section 5). */
    public boolean protectionNoticeDue(long gameTime, UUID device, EndgameCode code) {
        String key = device + "/" + code.name();
        Long last = protectionNotices.get(key);
        if (last != null && gameTime - last < EndgameLimits.PROTECTION_AUDIT_INTERVAL_TICKS) {
            return false;
        }
        protectionNotices.put(key, gameTime);
        return true;
    }

    /** Routine work (escrow, claims, effect batches...) is only counted, then summarized. */
    public void count(String system, String counter, long amount) {
        counters.computeIfAbsent(token(system), ignored -> new TreeMap<>()).merge(token(counter), amount, Long::sum);
    }

    /** One summary line per system with counters every 1,200 ticks, plus the suppressed-line count. */
    public void summarizeIfDue(long gameTime) {
        if (gameTime - lastSummary < EndgameLimits.AUDIT_SUMMARY_INTERVAL_TICKS) {
            return;
        }
        lastSummary = gameTime;
        for (Map.Entry<String, Map<String, Long>> system : counters.entrySet()) {
            StringBuilder fields = new StringBuilder();
            system.getValue().forEach((name, value) -> fields.append(fields.isEmpty() ? "" : " ").append(name)
                    .append('=').append(value));
            line(gameTime, system.getKey(), "summary", "OK", null, null, null, fields.toString());
        }
        counters.clear();
        if (suppressed > 0) {
            long count = suppressed;
            suppressed = 0;
            line(gameTime, "endgame", "summary", "OK", null, null, null, "suppressed_lines=" + count);
        }
    }

    /** Newest first, 16 lines per page, optionally only one system's lines. */
    public List<String> page(String system, int page) {
        List<String> matching = new ArrayList<>();
        Iterator<String> newest = ring.descendingIterator();
        String marker = system == null ? null : "system=" + token(system) + " ";
        while (newest.hasNext()) {
            String line = newest.next();
            if (marker == null || line.contains(marker)) {
                matching.add(line);
            }
        }
        int from = Math.max(0, page) * EndgameLimits.AUDIT_PAGE_LINES;
        return from >= matching.size() ? List.of()
                : List.copyOf(matching.subList(from, Math.min(matching.size(), from + EndgameLimits.AUDIT_PAGE_LINES)));
    }

    public int ringSize() {
        return ring.size();
    }

    public long suppressedLines() {
        return suppressed;
    }

    public void clear() {
        ring.clear();
        counters.clear();
        protectionNotices.clear();
        suppressed = 0;
        linesThisTick = 0;
        tick = Long.MIN_VALUE;
        lastSummary = 0;
    }

    private static String id(UUID id) {
        return id == null ? "-" : id.toString();
    }

    /** Field values never carry spaces or control characters, so one line stays one parsable record. */
    private static String token(String value) {
        if (value == null || value.isEmpty()) {
            return "-";
        }
        StringBuilder out = new StringBuilder(Math.min(value.length(), 64));
        for (int i = 0; i < value.length() && out.length() < 64; i++) {
            char c = value.charAt(i);
            out.append(c > ' ' && c < 127 && c != '=' ? c : '_');
        }
        return out.toString();
    }

    /** Truncates to at most 512 UTF-8 bytes without splitting a character. */
    static String bound(String line) {
        byte[] bytes = line.getBytes(StandardCharsets.UTF_8);
        if (bytes.length <= EndgameLimits.AUDIT_LINE_BYTES) {
            return line;
        }
        int end = EndgameLimits.AUDIT_LINE_BYTES - 3;
        while (end > 0 && (bytes[end] & 0xC0) == 0x80) {
            end--;
        }
        return new String(bytes, 0, end, StandardCharsets.UTF_8) + "...";
    }
}
