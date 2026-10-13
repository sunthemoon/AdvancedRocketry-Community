package io.github.sunthemoon.advancedrocketrycommunity.resourceescrow;

import java.util.ArrayDeque;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Supplier;

/** Bounded rotating queue. Unloaded/manual/backoff work cannot monopolize a tick. */
public final class EscrowReconciliationScheduler {
    public static final int VISITS_PER_TICK = 8;
    public record Result(EscrowReason reason, boolean proved) {
        public Result { EscrowTags.require(!proved || reason == EscrowReason.NONE, "A failure cannot prove an endpoint"); }
        public static Result deferred() { return new Result(EscrowReason.NONE, false); }
    }
    @FunctionalInterface public interface Driver {
        /** Owner-thread callback only; a deferred result never clears a persisted stall. */
        void attempt(EscrowOperation operation, List<EscrowOperation> records, Consumer<Result> completion);
    }
    private static final class Retry {
        int failures;
        long next;
        boolean active;
        EscrowReason manual = EscrowReason.NONE;
        Retry(EscrowOperation op) { failures = op.phase() == EscrowOperation.Phase.STALLED ? 3 : 0; }
    }
    private static final org.slf4j.Logger LOGGER = com.mojang.logging.LogUtils.getLogger();
    private final Thread owner = Thread.currentThread();
    private final Supplier<EscrowLedger> ledger;
    private final BiConsumer<UUID, EscrowReason> transition;
    private final Driver driver;
    private final ArrayDeque<UUID> queue = new ArrayDeque<>();
    private final Map<UUID, Retry> retries = new HashMap<>();
    private long tick;

    public EscrowReconciliationScheduler(Supplier<EscrowLedger> ledger, BiConsumer<UUID, EscrowReason> transition, Driver driver) {
        this.ledger = ledger; this.transition = transition; this.driver = driver;
    }
    public void tick(long now) {
        owner(); tick = now;
        List<EscrowOperation> records = ledger.get().records();
        Map<UUID, EscrowOperation> current = new HashMap<>();
        records.forEach(op -> current.put(op.id(), op));
        queue.removeIf(id -> !current.containsKey(id)); retries.keySet().retainAll(current.keySet());
        var queued = new HashSet<>(queue);
        records.stream().sorted(Comparator.comparingLong(EscrowOperation::sequence)).filter(op -> queued.add(op.id())).forEach(op -> {
            queue.addLast(op.id()); retries.put(op.id(), new Retry(op));
        });
        int visits = Math.min(VISITS_PER_TICK, queue.size());
        while (visits-- > 0) {
            UUID id = queue.removeFirst(); queue.addLast(id);
            EscrowOperation op = current.get(id); Retry retry = retries.get(id);
            if (retry.active || now < retry.next || retry.manual.manual() || op.reason().manual()
                    || op.phase() == EscrowOperation.Phase.SETTLING_ACCEPT || op.phase() == EscrowOperation.Phase.SETTLING_ABANDON) { continue; }
            retry.active = true;
            driver.attempt(op, records, result -> completed(id, retry, result));
        }
    }
    private void completed(UUID id, Retry retry, Result result) {
        owner(); if (retries.get(id) != retry) { return; }
        EscrowTags.require(retry.active, "Duplicate reconciliation callback"); retry.active = false;
        // Design section 5: one bounded line per state change and backoff transition. A continuing stall is silent.
        if (result.reason().manual()) {
            retry.manual = result.reason(); transition.accept(id, result.reason());
            LOGGER.warn("Escrow record {} needs audited settlement or offline repair: {}", id, result.reason());
        } else if (result.reason().retryableProofFailure()) {
            int previous = retry.failures;
            retry.failures = Math.min(3, retry.failures + 1);
            retry.next = tick + EscrowReason.backoff(retry.failures);
            if (retry.failures >= 3) { transition.accept(id, result.reason()); }
            if (retry.failures != previous && retry.failures >= 3) {
                LOGGER.warn("Escrow record {} is STALLED after {} proof failures ({}); retrying every {} ticks",
                        id, retry.failures, result.reason(), EscrowReason.backoff(retry.failures));
            } else if (retry.failures != previous) {
                LOGGER.info("Escrow record {} proof failed ({}); backing off {} ticks", id, result.reason(), EscrowReason.backoff(retry.failures));
            }
        } else if (result.proved()) {
            if (retry.failures > 0) { LOGGER.info("Escrow record {} proof succeeded after {} failures", id, retry.failures); }
            retry.failures = 0; retry.next = tick + 1; transition.accept(id, EscrowReason.NONE);
        } else {
            retry.next = tick + (result.reason() == EscrowReason.WAITING_UNLOADED ? 20 : 1);
        }
    }
    private void owner() { EscrowTags.require(Thread.currentThread() == owner, "Reconciliation requires its owner thread"); }
}
