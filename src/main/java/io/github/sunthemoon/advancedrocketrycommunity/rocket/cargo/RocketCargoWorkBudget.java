package io.github.sunthemoon.advancedrocketrycommunity.rocket.cargo;

/** Owner-thread accounting for ADR-065 4.1.1; must be owned once per runtime, never once per request. */
public final class RocketCargoWorkBudget {
    public static final long MAX_BYTES = 16L * 1_048_576;
    public static final int MAX_VISITS = 16_384;
    private final Thread owner = Thread.currentThread();
    private long tick = Long.MIN_VALUE;
    private long bytes;
    private int visits;
    private int reconstructions;

    /** Reserve a conservative whole step before doing any copy, encode, hash or construction. */
    public boolean charge(long serverTick, long byteWork, int blockVisits, boolean reconstruction) {
        if (Thread.currentThread() != owner) { throw new IllegalStateException("Cargo budget requires its owner thread"); }
        if (byteWork < 0 || blockVisits < 0 || serverTick < tick) { throw new IllegalArgumentException("Invalid cargo work charge"); }
        long usedBytes = serverTick == tick ? bytes : 0;
        int usedVisits = serverTick == tick ? visits : 0;
        int usedReconstructions = serverTick == tick ? reconstructions : 0;
        if (byteWork > MAX_BYTES - usedBytes || blockVisits > MAX_VISITS - usedVisits
                || reconstruction && usedReconstructions != 0) { return false; }
        tick = serverTick; bytes = usedBytes + byteWork; visits = usedVisits + blockVisits;
        reconstructions = usedReconstructions + (reconstruction ? 1 : 0);
        return true;
    }
    public long chargedBytes() { return bytes; }
    public int chargedVisits() { return visits; }
    public int reconstructions() { return reconstructions; }
}
