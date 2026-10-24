package io.github.sunthemoon.advancedrocketrycommunity.rocket.server;

import java.util.List;
import java.util.Optional;
import java.util.TreeSet;
import java.util.UUID;
import java.util.function.Predicate;

/** One fair selection across bounded U entries and retained RAM removals, including legacy records. */
public final class SealedRecoverySelection {
    private final Thread owner = Thread.currentThread();
    private long selectedTick = Long.MIN_VALUE;
    private UUID last;

    /** Candidates come only from the existing bounded journal/pending maps, never a world enumeration. */
    public Optional<UUID> select(long tick, List<UUID> candidates, Predicate<UUID> eligible) {
        if (Thread.currentThread() != owner) { throw new IllegalStateException("Recovery selection requires its owner thread"); }
        if (tick == selectedTick) { return Optional.empty(); }
        selectedTick = tick;
        var ids = List.copyOf(new TreeSet<>(candidates));
        int start = 0;
        if (last != null) { while (start < ids.size() && ids.get(start).compareTo(last) <= 0) { start++; } }
        for (int offset = 0; offset < ids.size(); offset++) {
            var id = ids.get((start + offset) % ids.size());
            if (eligible.test(id)) { last = id; return Optional.of(id); }
        }
        return Optional.empty();
    }
}
