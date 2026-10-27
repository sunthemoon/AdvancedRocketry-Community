package io.github.sunthemoon.advancedrocketrycommunity.satellite.mission;

/**
 * ADR-074 item 5 and design section 4.1. E is the durable epoch, h W119's reserved epoch (no capture carries an epoch
 * at or below it) and w the epoch that the capture in flight carries, from the capture until its completion is
 * published. Every stamp reads the snapshot epoch, w while a capture carrying w > E is in flight and E otherwise; every
 * durability check reads E. Only E is persisted, by the file that carries it. Owner thread only, under the registry's
 * lock; the IO thread never reads this.
 */
public final class RegistryEpochs {
    private static final long IDLE = Long.MIN_VALUE;
    private long durable, reserved, flight = IDLE, stateChanges;
    private boolean changed;

    RegistryEpochs(long loaded) {
        if (loaded < 1L) { throw new IllegalArgumentException("Save epoch starts at 1"); }
        durable = loaded;
        reserved = loaded;
    }

    public long durable() { return durable; }
    public long reserved() { return reserved; }
    /** A state change that no published COMMITTED capture covers. */
    public boolean changed() { return changed; }
    /** Counts state changes (clock observations excluded), so publication knows whether one happened since a capture. */
    public long stateChanges() { return stateChanges; }
    public boolean inFlight() { return flight != IDLE; }

    void markChanged() {
        changed = true;
        stateChanges++;
    }

    /** The snapshot epoch: every stamp reads it (rule 2). */
    public long stamp() { return flight > durable ? flight : durable; }

    /** The reservation ceiling r = max(E, h, w), counting a completed but unpublished capture as in flight. */
    public long ceiling() { return Math.max(Math.max(durable, reserved), flight); }

    /**
     * Design section 4.3: a change is admissible only when a successor u = r + 1 is representable. Every stamp t is at
     * most r, so u > t follows. At r = MAX nothing that needs a new epoch is admitted; a clock-only write keeps E.
     */
    public boolean admitsChange() { return ceiling() < Long.MAX_VALUE; }

    /** Rule 1: the epoch a capture taken now carries; ArithmeticException when no successor is representable. */
    public long toCarry() { return changed ? Math.addExact(Math.max(durable, reserved), 1L) : durable; }

    /** A capture carrying {@code carried} is in flight until its completion is published. */
    public void captured(long carried) {
        if (flight != IDLE) { throw new IllegalStateException("A registry capture is already in flight"); }
        flight = carried;
    }

    /**
     * Rule 3, COMMITTED: E and h become at least w, once. The changed flag clears only when no state change happened
     * since the capture; otherwise the next capture carries a new epoch. Returns whether E advanced.
     */
    boolean committed(long stateChangesAtCapture) {
        long carried = land();
        boolean advanced = carried > durable;
        durable = Math.max(durable, carried);
        reserved = Math.max(reserved, carried);
        if (stateChanges == stateChangesAtCapture) { changed = false; }
        return advanced;
    }

    /** Rule 4, FAILED: w is reserved only when the registry mutated during the flight; otherwise it may be carried again. */
    public void failed(boolean mutatedInFlight) {
        long carried = land();
        if (mutatedInFlight) { reserved = Math.max(reserved, carried); }
    }

    /** Rule 5, UNKNOWN: w is reserved; the caller clears the acknowledgement. */
    public void unknown() { reserved = Math.max(reserved, land()); }

    /** Domain fixtures without a writer: a write carrying {@link #toCarry()} completed with nothing in flight. */
    boolean persistedNow() {
        long carried = toCarry();
        captured(carried);
        return committed(stateChanges);
    }

    private long land() {
        if (flight == IDLE) { throw new IllegalStateException("No registry capture is in flight"); }
        long carried = flight;
        flight = IDLE;
        return carried;
    }
}
