package io.github.sunthemoon.advancedrocketrycommunity.rocket.sealed;

import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFlightLimits;
import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Objects;

/** Bounded hints are advisory: every consumed/overflowed hint remains eligible for loaded-world rediscovery. */
public final class SealedLazyWork<T> {
    public interface Port<T> {
        /** Exactly one loaded discovery cursor advance, or null. Never enumerate a whole world here. */
        T discover();
        /** The host charges any preparation/publication callbacks to this same allowance. */
        void attempt(T hint, SealedWorkBudget budget);
    }
    private final Thread owner = Thread.currentThread();
    private final Port<T> port;
    private final ArrayDeque<T> queue = new ArrayDeque<>();
    private final HashSet<T> queued = new HashSet<>();
    private boolean discoveryTurn = true;

    public SealedLazyWork(Port<T> port) { this.port = Objects.requireNonNull(port); }
    public boolean hint(T hint) {
        owner(); Objects.requireNonNull(hint);
        if (queued.contains(hint)) { return true; }
        if (queue.size() >= RocketFlightLimits.MAX_ACTIVE_TRANSFERS) { return false; }
        queued.add(hint); queue.addLast(hint); return true;
    }
    public int size() { owner(); return queue.size(); }
    public void tick(SealedWorkBudget budget) {
        owner();
        while (budget.take()) {
            boolean discover = discoveryTurn; discoveryTurn = !discoveryTurn;
            if (discover || queue.isEmpty()) {
                T found = port.discover(); if (found != null) { hint(found); }
            } else {
                T next = queue.removeFirst(); queued.remove(next);
                // Do not keep a held member in all 64 queue slots forever. Its intact C is the durable work list.
                port.attempt(next, budget);
            }
        }
    }
    public void clear() { owner(); queue.clear(); queued.clear(); discoveryTurn = true; }
    private void owner() {
        if (Thread.currentThread() != owner) { throw new IllegalStateException("Sealed discovery requires its owner thread"); }
    }
}
