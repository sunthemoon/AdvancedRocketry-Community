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
    private long claimedTick = Long.MIN_VALUE;
    private Object claimed;

    /** A step that did more work than it reserved; a caller with a typed status reports it rather than propagating it. */
    public static final class Exceeded extends IllegalStateException {
        Exceeded() { super("Cargo work exceeded its reserved step bound"); }
    }

    /** A continuation also owns this tick's reconstruction slot; a different cursor must defer. */
    public boolean claim(long serverTick, Object cursor) {
        java.util.Objects.requireNonNull(cursor);
        if (Thread.currentThread() != owner) { throw new IllegalStateException("Cargo budget requires its owner thread"); }
        if (serverTick == claimedTick) { return claimed == cursor; }
        if (!charge(serverTick, 0, 0, true)) { return false; }
        claimedTick = serverTick; claimed = cursor; return true;
    }

    /** Reserve before executing; unused byte/visit capacity is returned only after the bounded step finishes. */
    public boolean perform(long serverTick, long byteBound, int visitBound, boolean reconstruction,
            java.util.function.Supplier<io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketSnapshotPreparation.Work> work) {
        if (!charge(serverTick, byteBound, visitBound, reconstruction)) { return false; }
        var actual = work.get();
        if (actual.bytes() < 0 || actual.bytes() > byteBound || actual.visits() < 0 || actual.visits() > visitBound) {
            throw new Exceeded();
        }
        bytes -= byteBound - actual.bytes(); visits -= visitBound - actual.visits();
        return true;
    }

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
