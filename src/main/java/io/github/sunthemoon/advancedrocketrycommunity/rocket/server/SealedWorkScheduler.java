package io.github.sunthemoon.advancedrocketrycommunity.rocket.server;

import io.github.sunthemoon.advancedrocketrycommunity.rocket.sealed.SealedWorkBudget;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

/** Active native attempts precede discovery; only due, unstarted work joins the fair rotation. */
final class SealedWorkScheduler {
    interface Entry {
        UUID id();
        boolean recovery();
        boolean activeAttempt();
        boolean eligible(long now);
        void tickDeadline();
        void tickProof();
        void work();
    }
    private int cursor;
    private boolean lazyFirst;

    void tick(long now, List<? extends Entry> entries, UUID selected, SealedWorkBudget budget,
            Runnable lazy, Consumer<UUID> serviced) {
        entries.forEach(Entry::tickDeadline);
        var active = entries.stream().filter(Entry::activeAttempt).toList();
        // Capture/poll every active plan before any machine can spend the remaining inspection allowance.
        active.forEach(Entry::tickProof);
        for (var entry : active) {
            if (entry.eligible(now) && budget.remaining() > 0) { entry.work(); }
        }
        lazyFirst = !lazyFirst;
        if (lazyFirst) { lazy.run(); }
        int next = cursor;
        boolean advanced = false;
        for (int offset = 0; offset < entries.size(); offset++) {
            int index = (cursor + offset) % entries.size();
            var entry = entries.get(index);
            if (active.contains(entry) || entry.recovery() && !entry.id().equals(selected)
                    || !entry.eligible(now) || budget.remaining() == 0) { continue; }
            if (!advanced) { next = (index + 1) % entries.size(); advanced = true; }
            if (entry.recovery()) { serviced.accept(entry.id()); }
            entry.tickProof();
            entry.work();
        }
        cursor = next;
        if (!lazyFirst) { lazy.run(); }
    }
}
