package io.github.sunthemoon.advancedrocketrycommunity.client.exoplanet;

import java.lang.ref.WeakReference;
import java.util.Objects;

/** One client world's display cooldown, without retaining an unloaded world. */
final class FlashCooldown {
    static final int INTERVAL_TICKS = 100;
    private WeakReference<Object> world = new WeakReference<>(null);
    private boolean flashed;
    private long lastFlash;

    boolean tryAcquire(Object currentWorld, long now) {
        Objects.requireNonNull(currentWorld, "currentWorld");
        if (world.get() != currentWorld) {
            world = new WeakReference<>(currentWorld);
            flashed = false;
        }
        long elapsed = now - lastFlash;
        // A clock rewind starts a new timeline. Overflow means more than Long.MAX_VALUE ticks elapsed.
        if (flashed && now >= lastFlash && elapsed >= 0 && elapsed < INTERVAL_TICKS) {
            return false;
        }
        lastFlash = now;
        flashed = true;
        return true;
    }
}
