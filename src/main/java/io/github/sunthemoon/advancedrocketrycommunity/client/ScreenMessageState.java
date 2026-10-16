package io.github.sunthemoon.advancedrocketrycommunity.client;

import java.util.Optional;

/** One transient notice tied to one screen instance; no history survives closing or switching screens. */
final class ScreenMessageState<T> {
    static final long VISIBLE_MILLIS = 6000;
    private Object owner;
    private T message;
    private long receivedAt;

    void accept(Object screen, boolean modScreen, boolean overlay, T value, long now) {
        if (screen == null || !modScreen || !overlay) { return; }
        owner = screen;
        message = value;
        receivedAt = now;
    }

    Optional<T> current(Object screen, long now) {
        if (screen != owner || now < receivedAt || now - receivedAt >= VISIBLE_MILLIS) { clear(); }
        return Optional.ofNullable(message);
    }

    void clear() {
        owner = null;
        message = null;
        receivedAt = 0;
    }
}
