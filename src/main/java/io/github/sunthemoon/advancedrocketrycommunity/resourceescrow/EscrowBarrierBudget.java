package io.github.sunthemoon.advancedrocketrycommunity.resourceescrow;

import java.util.HashMap;
import java.util.Map;

/** Owner-thread quotas. Capture is reserved at its worst case before serialization begins. */
final class EscrowBarrierBudget {
    static final int PENDING = 32, PER_TYPE = 16, LEVELS = 64, CADENCE = 20;
    private record Batch(long start, int terrain, int entities) { }
    private final Map<String, Batch> batches = new HashMap<>();
    private int pending, bytes;
    private long lastCapture = Long.MIN_VALUE;

    boolean available(String level, boolean entity, long tick) {
        if (pending >= PENDING || lastCapture == tick
                || bytes > EscrowLimits.CAPTURE_BYTES - AnvilRecordReader.DECODED_BYTES
                || !batches.containsKey(level) && batches.size() >= LEVELS) { return false; }
        Batch batch = batches.get(level);
        if (batch == null || tick - batch.start() >= CADENCE) { batch = new Batch(tick, 0, 0); }
        if ((entity ? batch.entities() : batch.terrain()) >= PER_TYPE) { return false; }
        return true;
    }
    boolean reserve(String level, boolean entity, long tick) {
        if (!available(level, entity, tick)) { return false; }
        Batch batch = batches.get(level);
        if (batch == null || tick - batch.start() >= CADENCE) { batch = new Batch(tick, 0, 0); }
        batches.put(level, new Batch(batch.start(), batch.terrain() + (entity ? 0 : 1), batch.entities() + (entity ? 1 : 0)));
        lastCapture = tick; pending++; bytes += AnvilRecordReader.DECODED_BYTES; return true;
    }
    void captured(int actualBytes) {
        EscrowTags.require(actualBytes >= 0 && actualBytes <= AnvilRecordReader.DECODED_BYTES, "Capture byte limit exceeded");
        bytes -= AnvilRecordReader.DECODED_BYTES - actualBytes;
    }
    void release(int actualBytes) { pending--; bytes -= actualBytes; }
    int pending() { return pending; }
    int bytes() { return bytes; }
}
