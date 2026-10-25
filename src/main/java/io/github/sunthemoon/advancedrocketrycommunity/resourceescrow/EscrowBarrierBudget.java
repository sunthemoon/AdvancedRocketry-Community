package io.github.sunthemoon.advancedrocketrycommunity.resourceescrow;

import java.util.HashMap;
import java.util.Map;

/** Owner-thread quotas. Capture is reserved at its worst case before serialization begins. */
final class EscrowBarrierBudget {
    static final int PENDING = 32, PER_TYPE = 16, LEVELS = 64, CADENCE = 20;
    /** W98 (W96-R5): ordinary source-retirement proofs count in every quota above and never exceed this headroom. */
    static final int ORDINARY_PENDING = 8, ORDINARY_PER_LEVEL = 4;
    private record Batch(long start, int terrain, int entities, int ordinary) { }
    private final Map<String, Batch> batches = new HashMap<>();
    private int pending, bytes, ordinaryPending;
    private long lastCapture = Long.MIN_VALUE;

    boolean available(String level, boolean entity, long tick) {
        if (pending >= PENDING || lastCapture == tick
                || bytes > EscrowLimits.CAPTURE_BYTES - AnvilRecordReader.DECODED_BYTES
                || !batches.containsKey(level) && batches.size() >= LEVELS) { return false; }
        Batch batch = batches.get(level);
        if (batch == null || tick - batch.start() >= CADENCE) { batch = new Batch(tick, 0, 0, 0); }
        if ((entity ? batch.entities() : batch.terrain()) >= PER_TYPE) { return false; }
        return true;
    }
    /** Existing entity caller, with the same shared ordinary sub-quota as terrain. */
    boolean availableOrdinary(String level, long tick) {
        return availableOrdinary(level, true, tick);
    }
    boolean availableOrdinary(String level, boolean entity, long tick) {
        if (ordinaryPending >= ORDINARY_PENDING || !available(level, entity, tick)) { return false; }
        Batch batch = batches.get(level);
        return batch == null || tick - batch.start() >= CADENCE || batch.ordinary() < ORDINARY_PER_LEVEL;
    }
    boolean reserve(String level, boolean entity, long tick) {
        if (!available(level, entity, tick)) { return false; }
        Batch batch = batches.get(level);
        if (batch == null || tick - batch.start() >= CADENCE) { batch = new Batch(tick, 0, 0, 0); }
        batches.put(level, new Batch(batch.start(), batch.terrain() + (entity ? 0 : 1), batch.entities() + (entity ? 1 : 0),
                batch.ordinary()));
        lastCapture = tick; pending++; bytes += AnvilRecordReader.DECODED_BYTES; return true;
    }
    boolean reserveOrdinary(String level, long tick) {
        return reserveOrdinary(level, true, tick);
    }
    boolean reserveOrdinary(String level, boolean entity, long tick) {
        if (!availableOrdinary(level, entity, tick) || !reserve(level, entity, tick)) { return false; }
        Batch batch = batches.get(level);
        batches.put(level, new Batch(batch.start(), batch.terrain(), batch.entities(), batch.ordinary() + 1));
        ordinaryPending++; return true;
    }
    void captured(int actualBytes) {
        EscrowTags.require(actualBytes >= 0 && actualBytes <= AnvilRecordReader.DECODED_BYTES, "Capture byte limit exceeded");
        bytes -= AnvilRecordReader.DECODED_BYTES - actualBytes;
    }
    void release(int actualBytes) { pending--; bytes -= actualBytes; }
    void releaseOrdinary(int actualBytes) { release(actualBytes); ordinaryPending--; }
    int pending() { return pending; }
    int ordinaryPending() { return ordinaryPending; }
    int bytes() { return bytes; }
}
