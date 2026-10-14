package io.github.sunthemoon.advancedrocketrycommunity.endgame.elevator;

import io.github.sunthemoon.advancedrocketrycommunity.endgame.elevator.ElevatorCapsuleCodec.Entry;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Pure client projection clock. Each endpoint owns one; no world/entity references or server authority. */
public final class ElevatorCapsuleCountdown {
    public static final int LEASE_TICKS = 300;
    private final Map<Integer, Timed> entries = new LinkedHashMap<>();

    private record Timed(Entry entry, long received, long firstSeen) {
        int remaining(long now) {
            if (now < received || now < firstSeen || now - firstSeen >= LEASE_TICKS) {
                return 0;
            }
            return (int) Math.max(0, entry.remainingTicks() - (now - received));
        }
    }

    /** Membership replacement clears omissions; updates of the same rider do not extend its lease. */
    public void update(List<Entry> projection, long now) {
        if (projection.size() > ElevatorCapsuleCodec.MAX_RIDES) {
            clear();
            return;
        }
        Map<Integer, Timed> replacement = new LinkedHashMap<>();
        for (Entry entry : projection) {
            Timed old = entries.get(entry.entityId());
            long firstSeen = old == null ? now : old.firstSeen();
            Timed timed = new Timed(entry, now, firstSeen);
            if (timed.remaining(now) > 0) {
                replacement.put(entry.entityId(), timed);
            }
        }
        entries.clear();
        entries.putAll(replacement);
    }

    public List<Entry> live(long now) {
        expire(now);
        return entries.values().stream().map(timed -> new Entry(timed.entry().entityId(), timed.remaining(now)))
                .toList();
    }

    public void expire(long now) {
        entries.values().removeIf(timed -> timed.remaining(now) == 0);
    }

    public void discard(int entityId) {
        entries.remove(entityId);
    }

    public void clear() {
        entries.clear();
    }
}
