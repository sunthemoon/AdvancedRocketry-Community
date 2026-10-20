package io.github.sunthemoon.advancedrocketrycommunity.rocket.cargo;

/** Tick-only RAM lifetime; polling and failed budget reservations do not extend a ticket. */
final class RocketCargoTicketLifetime {
    private static final int LIMIT = 40;
    private long lastProgress;
    private Long readyAt;

    RocketCargoTicketLifetime(long now) { lastProgress = now; }

    void progress(long now, boolean progressed, boolean ready) {
        if (readyAt != null) { return; }
        if (progressed) { lastProgress = now; }
        if (ready) { readyAt = now; }
    }

    String expired(long now) {
        if (readyAt != null) { return now - readyAt >= LIMIT ? "ready-unpublished-40-ticks" : null; }
        return now - lastProgress >= LIMIT ? "no-progress-40-ticks" : null;
    }
}
