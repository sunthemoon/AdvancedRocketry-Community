package io.github.sunthemoon.advancedrocketrycommunity.endgame.intent;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * ADR-054 section 4 per-player intent spacing: state-changing intents and selection intents each have their own
 * interval. Only an accepted intent starts a new interval. Entries are forgotten at logout and server stop, so the
 * map is bounded by the players online.
 */
public final class EndgameRateLimiter {
    private final Map<UUID, long[]> last = new HashMap<>();

    public boolean allow(UUID player, IntentKind kind, long now, int intervalTicks) {
        Objects.requireNonNull(kind, "kind");
        long[] times = last.computeIfAbsent(Objects.requireNonNull(player, "player"), ignored -> new long[]{
                Long.MIN_VALUE, Long.MIN_VALUE});
        long previous = times[kind.ordinal()];
        if (previous != Long.MIN_VALUE && now - previous < intervalTicks) {
            return false;
        }
        times[kind.ordinal()] = now;
        return true;
    }

    public void forget(UUID player) {
        last.remove(player);
    }

    public int size() {
        return last.size();
    }

    public void clear() {
        last.clear();
    }
}
