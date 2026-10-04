package io.github.sunthemoon.advancedrocketrycommunity.persistence;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/** Transient refusal identities, never inventories; retained until the Level ends. */
public final class ChunkSaveDenials {
    public static final int MAX_CHUNKS = 256;
    public static final int MAX_REASON_CHARS = 256;
    private static final String SATURATED = "Refusing chunk save after denial capacity exhausted; back up and repair first";
    private static final String CLOSED = "Refusing chunk save after guard lifecycle ended";
    private final Map<Long, String> reasons = new HashMap<>();
    private boolean saturated;
    private boolean closed;

    public synchronized void deny(long chunk, String reason) {
        if (reason == null || reason.isBlank() || reason.length() > MAX_REASON_CHARS) {
            throw new IllegalArgumentException("Invalid bounded chunk-save refusal reason");
        }
        if (closed || reasons.containsKey(chunk)) { return; }
        if (reasons.size() == MAX_CHUNKS) { saturated = true; return; }
        reasons.put(chunk, reason);
    }

    public synchronized Optional<String> reason(long chunk) {
        if (closed) { return Optional.of(CLOSED); }
        String reason = reasons.get(chunk);
        return reason != null ? Optional.of(reason) : saturated ? Optional.of(SATURATED) : Optional.empty();
    }

    public synchronized int size() { return reasons.size(); }
    public synchronized void close() { closed = true; reasons.clear(); }

    // Only the Forge bridge's actual GameTestServer cleanup uses this, never ordinary save/unload.
    synchronized void restoreFixture(long chunk) {
        if (closed || saturated) { throw new IllegalStateException("Cannot reset closed or saturated save guard"); }
        reasons.remove(chunk);
    }
}
