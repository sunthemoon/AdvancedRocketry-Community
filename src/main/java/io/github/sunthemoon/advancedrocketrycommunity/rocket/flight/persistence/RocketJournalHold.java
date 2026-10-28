package io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.persistence;

import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;

/**
 * One gated, single-use selection; only the writer waits, never the server thread. W170-R23's startup variant: with
 * {@value #STARTUP_PROPERTY} also set, the process's first replacement (the startup baseline) waits once, whatever it
 * changes, until {@link #release()} or the same bound, and logs {@code ARCE_RELEASE_JOURNAL_STARTUP_HELD} and
 * {@code ARCE_RELEASE_JOURNAL_STARTUP_RELEASED}.
 */
final class RocketJournalHold {
    static final long MAX_MILLIS = 1_200 * 50L;
    static final String BOUND_PROPERTY = "advancedrocketrycommunity.rocketJournalHoldMillis";
    static final String STARTUP_PROPERTY = "advancedrocketrycommunity.rocketJournalHoldStartup";
    private final long boundMillis;
    private final CountDownLatch startup = new CountDownLatch(1);
    private final java.util.concurrent.atomic.AtomicBoolean startupUsed;
    private volatile boolean startupWaiting;
    private volatile Selection selection;

    RocketJournalHold(long boundMillis) {
        if (boundMillis < 1 || boundMillis > MAX_MILLIS) {
            throw new IllegalArgumentException("Hold bound must be between 1 and 60000 ms");
        }
        this.boundMillis = boundMillis;
        startupUsed = new java.util.concurrent.atomic.AtomicBoolean(!Boolean.getBoolean(STARTUP_PROPERTY));
    }

    void select(UUID logical) {
        var previous = selection;
        if (previous != null && previous.release.getCount() != 0) {
            throw new IllegalStateException("Release the previous selected record first");
        }
        selection = new Selection(Objects.requireNonNull(logical));
    }

    void release() {
        var selected = selection;
        if (selected != null) { selected.release.countDown(); }
        startup.countDown();
    }

    boolean waiting() { var selected = selection; return startupWaiting || selected != null && selected.waiting; }

    void beforeReplace(CompoundTag candidate, CompoundTag previous) {
        holdStartup();
        var selected = selection;
        if (selected == null || selected.release.getCount() == 0
                || Objects.equals(record(candidate, selected.logical), record(previous, selected.logical))) { return; }
        selected.waiting = true;
        long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(boundMillis);
        try {
            long remaining;
            while ((remaining = deadline - System.nanoTime()) > 0 && selected.release.getCount() != 0) {
                try { selected.release.await(remaining, TimeUnit.NANOSECONDS); }
                catch (InterruptedException retry) {
                    // This test delay is not cancellation. Do not pass an interrupt into the
                    // following checked atomic write/force, which must still commit normally.
                }
            }
        } finally {
            // Timeout and interruption never fail a write or alter the staged bytes.
            selected.release.countDown(); selected.waiting = false;
        }
    }

    /** The startup variant: the first replacement of the process waits, once, as a selected one does. */
    private void holdStartup() {
        if (!startupUsed.compareAndSet(false, true)) { return; }
        startupWaiting = true;
        io.github.sunthemoon.advancedrocketrycommunity.AdvancedRocketryCommunity.LOGGER.info(
                "ARCE_RELEASE_JOURNAL_STARTUP_HELD bound_ms={}", boundMillis);
        long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(boundMillis);
        try {
            long remaining;
            while ((remaining = deadline - System.nanoTime()) > 0 && startup.getCount() != 0) {
                try { startup.await(remaining, TimeUnit.NANOSECONDS); }
                catch (InterruptedException retry) {
                    // As for a selection: a test delay, never a cancellation of the following checked write.
                }
            }
        } finally {
            startupWaiting = false;
            io.github.sunthemoon.advancedrocketrycommunity.AdvancedRocketryCommunity.LOGGER.info("ARCE_RELEASE_JOURNAL_STARTUP_RELEASED");
        }
    }

    private static CompoundTag record(CompoundTag root, UUID logical) {
        if (root != null) {
            for (Tag raw : root.getList("transfers", Tag.TAG_COMPOUND)) {
                var record = (CompoundTag) raw;
                if (record.hasUUID("logical_rocket_id") && logical.equals(record.getUUID("logical_rocket_id"))) { return record; }
            }
        }
        return null;
    }

    private static final class Selection {
        final UUID logical;
        final CountDownLatch release = new CountDownLatch(1);
        volatile boolean waiting;
        Selection(UUID logical) { this.logical = logical; }
    }
}
