package io.github.sunthemoon.advancedrocketrycommunity.progression.classic.firstevent;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * W44 section 3.5: the bounded deferred-grant queue. One pending bit per kind for one connected participant's
 * connection instance, created only when a strictly decoded receipt names that participant. Entries never expire while
 * connected; a disconnect removes only that connection's entries and nothing is kept for offline players. The queue is
 * fair and bounded: FIFO by due tick, at most {@link #PER_TICK} examinations per END tick, and an entry is rechecked at
 * most once per {@link #RECHECK_TICKS}.
 */
final class FirstEventGrantQueue {
    /** 4,096 warp participants plus 16 Moon participants; receipts bound the participants, not a configuration. */
    static final int MAX_ENTRIES = FirstEventKind.FIRST_STATION_WARP.maxParticipants()
            + FirstEventKind.FIRST_MOON_LANDING.maxParticipants();
    static final int PER_TICK = 16;
    static final long RECHECK_TICKS = 20L;

    /** What one examination concluded. */
    enum Outcome { SETTLED, RETRY }

    @FunctionalInterface
    interface Handler {
        Outcome examine(UUID player, FirstEventKind kind, int connection);
    }

    private record Key(UUID player, FirstEventKind kind) { }

    private static final class Entry {
        final Key key;
        final int connection;
        long due;

        Entry(Key key, int connection, long due) {
            this.key = key;
            this.connection = connection;
            this.due = due;
        }
    }

    /** Ascending by due tick: new and woken entries are due now, rechecked ones move to the tail. */
    private final ArrayDeque<Entry> order = new ArrayDeque<>();
    private final Map<Key, Entry> entries = new HashMap<>();

    /** Adds or renews the pending bit; a newer connection replaces a stale one. False only at the hard cap. */
    boolean add(UUID player, FirstEventKind kind, int connection, long now) {
        Key key = new Key(Objects.requireNonNull(player, "player"), Objects.requireNonNull(kind, "kind"));
        Entry existing = entries.get(key);
        if (existing != null && existing.connection == connection) {
            return true;
        }
        if (existing != null) {
            order.remove(existing);
        } else if (entries.size() >= MAX_ENTRIES) {
            return false;
        }
        Entry entry = new Entry(key, connection, now);
        entries.put(key, entry);
        order.addFirst(entry);
        return true;
    }

    /** A disconnect removes that connection's entries only; a newer connection keeps its own. */
    void removeConnection(UUID player, int connection) {
        for (FirstEventKind kind : FirstEventKind.values()) {
            Entry entry = entries.get(new Key(player, kind));
            if (entry != null && entry.connection == connection) {
                entries.remove(entry.key);
                order.remove(entry);
            }
        }
    }

    /** Readiness of {@code kind} makes its entries due now, without looping over a receipt. */
    void wake(FirstEventKind kind, long now) {
        List<Entry> woken = new ArrayList<>();
        for (Iterator<Entry> iterator = order.iterator(); iterator.hasNext(); ) {
            Entry entry = iterator.next();
            if (entry.key.kind() == kind) {
                iterator.remove();
                entry.due = now;
                woken.add(entry);
            }
        }
        for (int index = woken.size() - 1; index >= 0; index--) {
            order.addFirst(woken.get(index));
        }
    }

    /** Examines at most {@link #PER_TICK} due entries in FIFO order. */
    void tick(long now, Handler handler) {
        for (int examined = 0; examined < PER_TICK && !order.isEmpty() && order.peekFirst().due <= now; examined++) {
            Entry entry = order.pollFirst();
            if (handler.examine(entry.key.player(), entry.key.kind(), entry.connection) == Outcome.SETTLED) {
                entries.remove(entry.key);
            } else {
                entry.due = now + RECHECK_TICKS;
                order.addLast(entry);
            }
        }
    }

    boolean contains(UUID player, FirstEventKind kind) {
        return entries.containsKey(new Key(player, kind));
    }

    int size() {
        return entries.size();
    }

    int size(FirstEventKind kind) {
        return (int) entries.keySet().stream().filter(key -> key.kind() == kind).count();
    }

    void clear() {
        entries.clear();
        order.clear();
    }
}
