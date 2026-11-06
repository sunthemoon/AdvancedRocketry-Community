package io.github.sunthemoon.advancedrocketrycommunity.client;

import java.util.Optional;

/** One transient notice tied to one screen instance; no history survives closing or switching screens. */
final class ScreenMessageState<T> {
    static final long VISIBLE_MILLIS = 6000;
    private Object owner;
    private T message;
    private long receivedAt;
    /** W311: a refusal its owner keeps beyond the lifetime until it releases it; a newer timed notice shows first. */
    private T pinned;

    void accept(Object screen, boolean modScreen, boolean overlay, T value, long now) {
        accept(screen, modScreen, overlay, value, now, false);
    }

    void accept(Object screen, boolean modScreen, boolean overlay, T value, long now, boolean pin) {
        if (screen == null || !modScreen || !overlay) { return; }
        if (screen != owner) { clear(); }
        owner = screen;
        if (pin) {
            pinned = value;
            message = null;
            receivedAt = 0;
        } else {
            message = value;
            receivedAt = now;
        }
    }

    Optional<T> current(Object screen, long now) {
        if (screen != owner) {
            clear();
        } else if (message != null && (now < receivedAt || now - receivedAt >= VISIBLE_MILLIS)) {
            if (pinned == null) { clear(); } else { message = null; receivedAt = 0; }
        }
        return Optional.ofNullable(message != null ? message : pinned);
    }

    /** W311: the owner's next action or plan change ends its refusal; a timed notice keeps its own lifetime. */
    void release(Object screen) {
        if (screen != null && screen == owner) {
            pinned = null;
            if (message == null) { clear(); }
        }
    }

    void clear() {
        owner = null;
        message = null;
        receivedAt = 0;
        pinned = null;
    }
}
