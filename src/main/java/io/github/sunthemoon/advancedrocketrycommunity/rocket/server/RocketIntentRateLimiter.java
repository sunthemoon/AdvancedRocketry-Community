package io.github.sunthemoon.advancedrocketrycommunity.rocket.server;

import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFlightLimits;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/** Main-thread, fixed-window limiter with one audit-worthy rejection per player window. */
final class RocketIntentRateLimiter {
    enum Decision {
        ALLOWED,
        REJECTED_AUDIT,
        REJECTED_SILENT
    }

    private final Map<UUID, Window> windows = new LinkedHashMap<>();

    Decision check(UUID playerId, long gameTime) {
        Objects.requireNonNull(playerId, "playerId");
        if (gameTime < 0L) {
            throw new IllegalArgumentException("Rate-limit game time cannot be negative");
        }
        Window window = windows.get(playerId);
        if (window == null) {
            discardExpired(gameTime);
            if (windows.size() >= RocketFlightLimits.MAX_TRACKED_INTENT_PLAYERS) {
                return Decision.REJECTED_SILENT;
            }
            windows.put(playerId, new Window(gameTime, 1, false));
            return Decision.ALLOWED;
        }
        if (gameTime < window.startedAt()
                || gameTime - window.startedAt() >= RocketFlightLimits.INTENT_WINDOW_TICKS) {
            windows.put(playerId, new Window(gameTime, 1, false));
            return Decision.ALLOWED;
        }
        if (window.accepted() < RocketFlightLimits.MAX_INTENTS_PER_WINDOW) {
            windows.put(playerId, new Window(
                    window.startedAt(),
                    window.accepted() + 1,
                    window.rejectionAudited()
            ));
            return Decision.ALLOWED;
        }
        if (!window.rejectionAudited()) {
            windows.put(playerId, new Window(window.startedAt(), window.accepted(), true));
            return Decision.REJECTED_AUDIT;
        }
        return Decision.REJECTED_SILENT;
    }

    void clear() {
        windows.clear();
    }

    int trackedPlayers() {
        return windows.size();
    }

    private void discardExpired(long gameTime) {
        Iterator<Map.Entry<UUID, Window>> iterator = windows.entrySet().iterator();
        while (iterator.hasNext()) {
            Window window = iterator.next().getValue();
            if (gameTime < window.startedAt()
                    || gameTime - window.startedAt() >= RocketFlightLimits.INTENT_WINDOW_TICKS) {
                iterator.remove();
            }
        }
    }

    private record Window(long startedAt, int accepted, boolean rejectionAudited) {
    }
}
