package io.github.sunthemoon.advancedrocketrycommunity.machine.tank;

import java.util.LinkedHashMap;
import java.util.Objects;
import java.util.function.Consumer;

/** Bounded FIFO plus oldest-dirty admission: overflow remains in each caller, not an unbounded second queue. */
public final class TankTransferQueue<K> {
    public static final int MAX_QUEUED = 1_024;
    public static final int PER_TICK = 64;
    public static final int TRANSFER_MB = 1_000;
    public enum Admission { QUEUED, WAITING }

    private final LinkedHashMap<K, Long> pending = new LinkedHashMap<>();
    private long admissionAge = Long.MAX_VALUE;
    private long observedAge = Long.MAX_VALUE;
    private long lastTick = Long.MIN_VALUE;
    private boolean processing;

    /** Called by already-loaded tank tickers; constant work, no world/column traversal. */
    public Admission offer(K key, long tick, long dirtySince) {
        Objects.requireNonNull(key, "key");
        if (tick < 0 || dirtySince < 0 || dirtySince > tick) {
            throw new IllegalArgumentException("Invalid transient tank scheduling time");
        }
        if (pending.containsKey(key)) {
            return Admission.QUEUED;
        }
        observedAge = Math.min(observedAge, dirtySince);
        if (dirtySince > admissionAge || pending.size() >= MAX_QUEUED) {
            return Admission.WAITING;
        }
        pending.put(key, tick);
        return Admission.QUEUED;
    }

    /** No request is run on its requesting tick; duplicate/reentrant drains cannot enlarge a tick budget. */
    public int tick(long tick, Consumer<K> action) {
        Objects.requireNonNull(action, "action");
        if (processing || tick <= lastTick) {
            return 0;
        }
        processing = true;
        lastTick = tick;
        int attempts = 0;
        try {
            while (attempts < PER_TICK && !pending.isEmpty()) {
                var head = pending.entrySet().iterator().next();
                if (head.getValue() >= tick) {
                    break;
                }
                K key = head.getKey();
                pending.remove(key);
                attempts++;
                action.accept(key);
            }
            return attempts;
        } finally {
            // An overflowed old dirty tank precedes a recently serviced tank even in fixed BE ticker order.
            admissionAge = observedAge;
            observedAge = Long.MAX_VALUE;
            processing = false;
        }
    }

    public void remove(K key) { pending.remove(key); }
    public int size() { return pending.size(); }
    public boolean contains(K key) { return pending.containsKey(key); }
    public void clear() {
        pending.clear();
        admissionAge = Long.MAX_VALUE;
        observedAge = Long.MAX_VALUE;
    }
}
