package io.github.sunthemoon.advancedrocketrycommunity.rocket.sealed;

import io.github.sunthemoon.advancedrocketrycommunity.persistence.CheckedJournalCheckpoint.Outcome;
import java.util.Objects;

/** Aggregates every retained/lazy member's demand into at most one checked whole-U capture per owner tick. */
public final class SealedJournalRequests {
    public interface Port {
        long now();
        boolean operational();
        boolean current();
        Outcome checkpoint();
    }
    private final Thread owner = Thread.currentThread();
    private final Port port;
    private long lastAttempt = Long.MIN_VALUE;
    private long notBefore = Long.MIN_VALUE;
    private boolean requested;

    public SealedJournalRequests(Port port) { this.port = Objects.requireNonNull(port); }
    public void request() { owner(); requested = true; }

    /** Call after the owner's work pass. Callers re-query presence/absence later, never use this as permission. */
    public void flush() {
        owner();
        if (!requested || !port.operational()) { return; }
        if (port.current()) { requested = false; return; }
        long now = port.now();
        if (lastAttempt == now || now < notBefore) { return; }
        lastAttempt = now;
        // Set before entering the writer, so an exception cannot create an immediate write loop.
        notBefore = now + 200;
        Outcome result = port.checkpoint();
        if (result == Outcome.COMMITTED) {
            requested = !port.current();
            notBefore = now + 1;
        }
    }
    private void owner() {
        if (Thread.currentThread() != owner) { throw new IllegalStateException("Sealed journal requests require their owner thread"); }
    }
}
