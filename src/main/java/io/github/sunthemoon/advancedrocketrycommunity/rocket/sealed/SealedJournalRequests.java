package io.github.sunthemoon.advancedrocketrycommunity.rocket.sealed;

import io.github.sunthemoon.advancedrocketrycommunity.persistence.CheckedJournalCheckpoint.Outcome;
import java.util.Objects;

/** Aggregates every retained/lazy member's demand into at most one checked whole-U capture per owner tick. */
public final class SealedJournalRequests {
    public interface Port {
        long now();
        boolean operational();
        boolean current();
        /** ADR-074 item 8: asks the journal's writer for one checked capture; false when the writer refuses it. */
        boolean request();
        /** B14's WRITE_PENDING: a write is requested or in flight, so its outcome is not published yet. */
        boolean writePending();
        /** The outcome of the journal's last published write. */
        Outcome outcome();
        /** Once per accepted request, on the owner thread: the published outcome of the write that answered it. */
        void written(Outcome outcome);
    }
    private final Thread owner = Thread.currentThread();
    private final Port port;
    private long lastAttempt = Long.MIN_VALUE;
    private long notBefore = Long.MIN_VALUE;
    private boolean requested, awaiting;

    public SealedJournalRequests(Port port) { this.port = Objects.requireNonNull(port); }
    public void request() { owner(); requested = true; }

    /** Call after the owner's work pass. Callers re-query presence/absence later, never use this as permission. */
    public void flush() {
        owner();
        if (!requested || !port.operational()) { return; }
        if (port.current()) { requested = false; awaiting = false; return; }
        // WRITE_PENDING means "not proved yet, wait": it is no result, keeps the demand and starts no other capture.
        if (port.writePending()) { return; }
        long now = port.now();
        if (awaiting) {
            // The writer is sequential: its first idle answer after the request reflects a capture taken at or after it.
            awaiting = false;
            Outcome result = port.outcome();
            port.written(result);
            if (result == Outcome.COMMITTED) { notBefore = lastAttempt + 1; }
        }
        if (lastAttempt == now || now < notBefore) { return; }
        lastAttempt = now;
        // Set before entering the writer, so an exception cannot create an immediate write loop.
        notBefore = now + 200;
        awaiting = port.request();
    }
    private void owner() {
        if (Thread.currentThread() != owner) { throw new IllegalStateException("Sealed journal requests require their owner thread"); }
    }
}
