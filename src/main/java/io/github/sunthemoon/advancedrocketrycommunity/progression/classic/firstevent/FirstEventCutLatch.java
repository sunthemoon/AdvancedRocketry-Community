package io.github.sunthemoon.advancedrocketrycommunity.progression.classic.firstevent;

import java.io.IOException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/** One release-test writer delay; no world references, and neither a tick nor a wall-clock wait is unbounded. */
final class FirstEventCutLatch {
    static final int MAX_TICKS = 1_200;
    private final CountDownLatch released = new CountDownLatch(1);
    private final int ticks;
    private final long millis;
    private volatile boolean waiting;
    private volatile boolean expired;
    private long deadline = -1;

    FirstEventCutLatch() { this(MAX_TICKS, 60_000); }

    FirstEventCutLatch(int ticks, long millis) {
        if (ticks < 1 || ticks > MAX_TICKS || millis < 1 || millis > 60_000) {
            throw new IllegalArgumentException("First-event latch exceeds its fixed budget");
        }
        this.ticks = ticks;
        this.millis = millis;
    }

    /** Called only by the journal writer, after the empty-server gate was checked on S. */
    void awaitMove() throws IOException {
        waiting = true;
        try {
            if (!released.await(millis, TimeUnit.MILLISECONDS) || expired) {
                throw new IOException("Release-test first-event latch expired before the move");
            }
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IOException("Release-test first-event latch interrupted", interrupted);
        }
    }

    /** Called only on S; expiry refuses the write rather than silently releasing an unobserved move. */
    void tick(long now) {
        if (!waiting || released.getCount() == 0) { return; }
        if (deadline < 0) { deadline = now + ticks; }
        if (now >= deadline) { expired = true; released.countDown(); }
    }

    void release() { released.countDown(); }
}
