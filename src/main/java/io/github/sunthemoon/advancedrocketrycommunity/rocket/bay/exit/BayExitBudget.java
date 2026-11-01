package io.github.sunthemoon.advancedrocketrycommunity.rocket.bay.exit;

import java.util.Optional;

/** One server-owned, owner-thread budget shared by exits and descent contact probes; no static state. */
public final class BayExitBudget {
    public static final int CANDIDATES_PER_EXIT = 32;
    public static final int CANDIDATES_PER_TICK = 128;
    public static final int READS_PER_CANDIDATE = 96;
    public static final int READS_PER_TICK = 4096;
    public static final int VISITS_PER_CANDIDATE = 16;
    public static final int VISITS_PER_TICK = 256;

    public record Usage(int candidates, int reads, int visits) {
        public Usage minus(Usage earlier) {
            return new Usage(candidates - earlier.candidates, reads - earlier.reads, visits - earlier.visits);
        }
    }

    private final Thread owner = Thread.currentThread();
    private long tick;
    private int candidates;
    private int reads;
    private int visits;
    private int open;

    public BayExitBudget(long tick) { this.tick = tick; }

    void checkThread() {
        if (Thread.currentThread() != owner) { throw new IllegalStateException("Exit budget is owner-thread only"); }
    }

    /** The adapter advances only after the old tick's END drain. Same-tick calls never replenish work. */
    public void advanceTick(long next) {
        checkThread();
        if (next < tick || open != 0) { throw new IllegalStateException("Cannot reset active or older exit budget"); }
        if (next > tick) { tick = next; candidates = 0; reads = 0; visits = 0; }
    }

    public Usage usage() { checkThread(); return new Usage(candidates, reads, visits); }

    /** Also used for contact probes, so they cannot acquire a separate world-read allowance. */
    public Optional<Probe> openProbe() {
        checkThread();
        if (candidates == CANDIDATES_PER_TICK) { return Optional.empty(); }
        candidates++;
        open++;
        return Optional.of(new Probe());
    }

    public final class Probe implements AutoCloseable {
        private int localReads;
        private int localVisits;
        private boolean incomplete;
        private boolean closed;

        private Probe() { }

        void checkOpen() {
            checkThread();
            if (closed) { throw new IllegalStateException("Exit probe is closed"); }
        }

        /** Reserve the whole known window before the first read; failure consumes no read units. */
        public boolean reserveReads(int count) {
            checkOpen();
            if (count < 0 || count > READS_PER_CANDIDATE - localReads || count > READS_PER_TICK - reads) {
                incomplete = true;
                return false;
            }
            localReads += count;
            reads += count;
            return true;
        }

        /** Charge before visiting each entity or reservation, including non-colliding ones. */
        public boolean visit() {
            checkOpen();
            if (localVisits == VISITS_PER_CANDIDATE || visits == VISITS_PER_TICK) {
                incomplete = true;
                return false;
            }
            localVisits++;
            visits++;
            return true;
        }

        public boolean incomplete() { checkThread(); return incomplete; }

        @Override public void close() {
            checkThread();
            if (!closed) { closed = true; open--; }
        }
    }
}
