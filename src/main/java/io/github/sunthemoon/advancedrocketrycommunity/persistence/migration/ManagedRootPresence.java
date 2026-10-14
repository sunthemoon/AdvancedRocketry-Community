package io.github.sunthemoon.advancedrocketrycommunity.persistence.migration;

/**
 * W44 section 3.6: how a managed root was first obtained in this server session. Only PROVEN_ABSENT counts as an
 * absent root; UNREADABLE and BLOCKED roots are not operational and are never written.
 */
public enum ManagedRootPresence {
    /** A file was read and decoded as a valid current root. */
    LOADED,
    /** A no-follow stat reported no file; the first write re-checks it. */
    PROVEN_ABSENT,
    /** A file exists, or its stat failed, but vanilla could not read it; held until a plain restart. */
    UNREADABLE,
    /** A file was read but did not decode; its bytes are preserved. */
    BLOCKED;

    public boolean operational() {
        return this == LOADED || this == PROVEN_ABSENT;
    }
}
