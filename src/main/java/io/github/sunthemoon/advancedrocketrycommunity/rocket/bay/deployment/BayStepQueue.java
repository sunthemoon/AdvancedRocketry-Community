package io.github.sunthemoon.advancedrocketrycommunity.rocket.bay.deployment;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;

/**
 * The coordinator's one round-robin step queue, keyed by logical rocket L (W27A-DESIGN section 4: at most 32 steps per
 * tick, shared by checkpoint and restart work). Each key runs one step per turn; a key that asks again goes to the back,
 * so every other queued key has its turn first. Pure Java, owner-thread only.
 */
public final class BayStepQueue {
    public static final int STEPS_PER_TICK = 32;
    /** AGAIN: more work now; NEXT_TICK: poll again next tick; IDLE: only an event offers the key again. */
    public enum Result { AGAIN, NEXT_TICK, IDLE }

    private final ArrayDeque<UUID> order = new ArrayDeque<>();
    private final Set<UUID> queued = new HashSet<>();
    private boolean closed;

    /** Queues the key once; a key already queued keeps its place. A closed queue ignores it. */
    public void offer(UUID key) {
        Objects.requireNonNull(key, "key");
        if (!closed && queued.add(key)) { order.addLast(key); }
    }

    public boolean queued(UUID key) { return queued.contains(key); }
    public int size() { return order.size(); }

    /** Runs at most {@link #STEPS_PER_TICK} steps; keys that asked for the next tick are queued after the pass. */
    public int run(Function<UUID, Result> step) {
        return run(STEPS_PER_TICK, step);
    }

    int run(int budget, Function<UUID, Result> step) {
        if (closed) { return 0; }
        List<UUID> nextTick = new ArrayList<>();
        int steps = 0;
        while (steps < budget && !order.isEmpty()) {
            UUID key = order.pollFirst();
            queued.remove(key);
            steps++;
            Result result = Objects.requireNonNull(step.apply(key), "result");
            if (result == Result.AGAIN) { offer(key); }
            else if (result == Result.NEXT_TICK) { nextTick.add(key); }
        }
        nextTick.forEach(this::offer);
        return steps;
    }

    public void remove(UUID key) {
        if (queued.remove(key)) { order.remove(key); }
    }

    public void clear() { order.clear(); queued.clear(); }

    /** Server stopping (W129-DESIGN section 4.6): every waiting step is discarded and nothing runs again. */
    public void close() { closed = true; clear(); }
    public boolean closed() { return closed; }
}
