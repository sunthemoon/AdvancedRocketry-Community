package io.github.sunthemoon.advancedrocketrycommunity.progression.classic.firstevent;

import java.util.Objects;
import java.util.Optional;

/**
 * W44 terms: the tagged durable view of a source outbox. KNOWN carries the outbox of loaded or confirmed authority;
 * SOURCE_UNCONFIRMED is a temporary wait for writer confirmation, never EMPTY; SOURCE_UNAVAILABLE is a quarantined,
 * unreadable or blocked root. Unconfirmed RAM is never a KNOWN view.
 */
public record FirstEventSourceView(Status status, FirstEventOutbox outbox, Reason reason) {
    public enum Status { KNOWN, SOURCE_UNCONFIRMED, SOURCE_UNAVAILABLE }

    /** Why a view is not KNOWN; the status command and the held-kind WARN print it. */
    public enum Reason { NONE, UNCONFIRMED, QUARANTINED, ROOT_UNREADABLE, ROOT_BLOCKED, COMMITTED_OVERRIDE }

    public FirstEventSourceView {
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(reason, "reason");
        if ((status == Status.KNOWN) != (outbox != null)) {
            throw new IllegalArgumentException("Only a KNOWN view carries an outbox");
        }
    }

    public static FirstEventSourceView known(FirstEventOutbox outbox) {
        return new FirstEventSourceView(Status.KNOWN, Objects.requireNonNull(outbox, "outbox"), Reason.NONE);
    }

    public static FirstEventSourceView unconfirmed() {
        return new FirstEventSourceView(Status.SOURCE_UNCONFIRMED, null, Reason.UNCONFIRMED);
    }

    public static FirstEventSourceView unavailable(Reason reason) {
        return new FirstEventSourceView(Status.SOURCE_UNAVAILABLE, null, reason);
    }

    public boolean known() { return status == Status.KNOWN; }

    /** The KNOWN outbox, if any. */
    public Optional<FirstEventOutbox> knownOutbox() { return Optional.ofNullable(outbox); }

    /** The KNOWN event, ELECTED or DELIVERED; empty for EMPTY and for every view that is not KNOWN. */
    public Optional<FirstEvent> knownEvent() { return knownOutbox().flatMap(FirstEventOutbox::eventOptional); }
}
