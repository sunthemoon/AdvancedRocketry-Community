package io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.persistence;

import io.github.sunthemoon.advancedrocketrycommunity.progression.classic.firstevent.FirstEventOutbox;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketTransferRecord;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.BooleanSupplier;

/** Owner-thread reservations and FIFO effects. Acknowledgement authority belongs only to RocketJournalLeases. */
final class RocketJournalSchedule {
    static final int LIMIT = 128;
    static final int RUNS_PER_TICK = 64;
    private final RocketTransferSavedData journal;
    private final int limit;
    private final Thread owner = Thread.currentThread();
    private final Map<UUID, RocketTransferSavedData.Reservation> reservations = new HashMap<>();
    private final LinkedHashMap<UUID, Effect> effects = new LinkedHashMap<>();
    private final ArrayDeque<Effect> queue = new ArrayDeque<>();
    private volatile boolean outstanding;
    private long tick = Long.MIN_VALUE;
    private int runs;
    private boolean visiting;

    RocketJournalSchedule(RocketTransferSavedData journal, int limit) {
        if (limit < 1 || limit > LIMIT) { throw new IllegalArgumentException("Invalid journal reservation bound"); }
        this.journal = journal; this.limit = limit;
    }

    Optional<RocketTransferSavedData.Reservation> reserve(UUID logical) {
        checkOwner(); Objects.requireNonNull(logical);
        if (!journal.schedulingAvailable() || busy(logical) || size() >= limit) { return Optional.empty(); }
        var token = new RocketTransferSavedData.Reservation(this, logical);
        reservations.put(logical, token); publish(); return Optional.of(token);
    }

    void close(RocketTransferSavedData.Reservation token) {
        checkOwner(); reservations.remove(token.logical(), token); publish();
    }

    boolean owns(RocketTransferSavedData.Reservation token) {
        return token != null && reservations.get(token.logical()) == token;
    }

    boolean busy(UUID logical) { checkOwner(); return effects.containsKey(logical) || reservations.containsKey(logical); }
    boolean busy(UUID logical, RocketTransferSavedData.Reservation token) {
        checkOwner();
        return effects.containsKey(logical) || reservations.containsKey(logical)
                && !(owns(token) && token.logical().equals(logical));
    }
    boolean outstanding() { return outstanding; }
    int size() { return effects.size() + reservations.size(); }
    long tick() { return tick; }

    boolean submit(RocketTransferSavedData.Reservation token, boolean outbox, boolean duringFailure,
                   BooleanSupplier action, Runnable discarded, long now) {
        checkOwner();
        Objects.requireNonNull(action); Objects.requireNonNull(discarded);
        if (!owns(token) || !journal.schedulingAvailable()) { return false; }
        var effect = new Effect(token.logical(), journal.findByLogicalRocket(token.logical()).orElse(null),
                journal.mutationSequence(), outbox ? journal.firstMoonLandingRam() : null,
                duringFailure, action, discarded);
        reservations.remove(token.logical(), token); // Consume even on the immediate path, before the body.
        resetBudget(now);
        if (!visiting && acknowledged(effect) && (!journal.effectWriteFailed() || duringFailure)
                && runs < RUNS_PER_TICK) {
            publish(); runs++; visiting = true;
            try { effect.run(); } finally { visiting = false; }
        } else { effects.put(effect.logical, effect); queue.addLast(effect); publish(); }
        return true;
    }

    void visit(long now) {
        checkOwner(); resetBudget(now);
        if (visiting || !journal.schedulingAvailable()) { return; }
        visiting = true;
        var skipped = new ArrayList<Effect>();
        try {
            // Keep skipped entries busy, restoring their FIFO positions after this bounded visit.
            while (runs < RUNS_PER_TICK && journal.schedulingAvailable()) {
                var effect = queue.peekFirst();
                if (effect == null) { break; }
                boolean changed = !unchanged(effect);
                if (!changed && !acknowledged(effect)) { break; }
                if (!changed && journal.effectWriteFailed() && !effect.duringFailure) {
                    skipped.add(queue.removeFirst()); continue;
                }
                queue.removeFirst();
                effects.remove(effect.logical, effect); publish(); runs++;
                if (changed) { effect.discarded.run(); continue; }
                try { effect.run(); }
                catch (RuntimeException failure) {
                    com.mojang.logging.LogUtils.getLogger().error(
                            "ARCE_TRANSFER_EFFECT_FAILED logical={} generation={}; retain journal authority for recovery",
                            effect.logical, effect.sequence, failure);
                }
            }
        } finally {
            for (int index = skipped.size() - 1; index >= 0; index--) {
                var effect = skipped.get(index);
                if (effects.get(effect.logical) == effect) { queue.addFirst(effect); }
            }
            visiting = false;
        }
    }

    void discard() {
        checkOwner(); var pending = List.copyOf(effects.values()); effects.clear(); queue.clear(); reservations.clear(); publish();
        pending.forEach(effect -> effect.discarded.run());
    }

    private boolean unchanged(Effect effect) {
        return journal.logicalUnchangedSince(effect.logical, effect.sequence)
                && journal.findByLogicalRocket(effect.logical).orElse(null) == effect.expected
                && (effect.outbox == null || effect.outbox.equals(journal.firstMoonLandingRam()));
    }

    private boolean acknowledged(Effect effect) {
        return unchanged(effect) && journal.slotAcknowledged(effect.logical)
                && (effect.outbox == null || journal.outboxAcknowledged(effect.outbox));
    }

    private void resetBudget(long now) { if (tick != now) { tick = now; runs = 0; } }
    private void publish() { outstanding = size() != 0; }
    private void checkOwner() {
        if (Thread.currentThread() != owner) { throw new IllegalStateException("Journal scheduling requires the owner thread"); }
    }

    private record Effect(UUID logical, RocketTransferRecord expected, long sequence, FirstEventOutbox outbox,
                          boolean duringFailure, BooleanSupplier action, Runnable discarded) {
        void run() {
            boolean accepted = false;
            try { accepted = action.getAsBoolean(); }
            finally { if (!accepted) { discarded.run(); } }
        }
    }
}
