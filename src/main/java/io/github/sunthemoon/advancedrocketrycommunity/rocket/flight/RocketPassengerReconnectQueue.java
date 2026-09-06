package io.github.sunthemoon.advancedrocketrycommunity.rocket.flight;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** Short, bounded, lifecycle-owned retries; never retains players or worlds. */
public final class RocketPassengerReconnectQueue {
    public static final int CAPACITY = 128;
    public static final int ATTEMPTS_PER_TICK = 4;
    public static final long MAX_WAIT_TICKS = 200L;
    private final LinkedHashMap<UUID, Long> waiting = new LinkedHashMap<>();

    public boolean offer(UUID player, long tick) {
        Objects.requireNonNull(player, "player");
        if (tick < 0L) {
            throw new IllegalArgumentException("Negative reconnect time");
        }
        if (waiting.containsKey(player)) {
            return true;
        }
        if (waiting.size() >= CAPACITY) {
            return false;
        }
        waiting.put(player, tick);
        return true;
    }

    public Batch next(long tick) {
        var pending = new ArrayList<UUID>();
        var expired = new ArrayList<UUID>();
        var keys = waiting.keySet().stream().limit(ATTEMPTS_PER_TICK).toList();
        for (UUID player : keys) {
            long started = waiting.remove(player);
            if (tick < started || tick - started >= MAX_WAIT_TICKS) {
                expired.add(player);
            } else {
                waiting.put(player, started);
                pending.add(player);
            }
        }
        return new Batch(List.copyOf(pending), List.copyOf(expired));
    }

    public void complete(UUID player) {
        waiting.remove(player);
    }

    public void clear() {
        waiting.clear();
    }

    public record Batch(List<UUID> pending, List<UUID> expired) {
    }
}
