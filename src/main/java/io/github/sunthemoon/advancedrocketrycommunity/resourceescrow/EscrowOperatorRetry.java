package io.github.sunthemoon.advancedrocketrycommunity.resourceescrow;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;

/** Command scheduling only. No result is evidence of an endpoint or writer acknowledgement. */
final class EscrowOperatorRetry {
    static final int COOLDOWN_TICKS = 200;
    enum Outcome { ACCEPTED, PENDING, COOLDOWN, REFUSED }
    record Result(Outcome outcome, long nextTick) {
        String message() {
            return switch (outcome) {
                case ACCEPTED -> "Retry acceleration queued; no proof or release claimed. next_command_tick=" + nextTick;
                case PENDING -> "attempt pending";
                case COOLDOWN -> "Retry cooldown; next_eligible_tick=" + nextTick;
                case REFUSED -> "Retry refused: not a retryable proof/write wait; no state changed.";
            };
        }
    }
    private final Map<UUID, Long> next = new HashMap<>();
    Result request(EscrowLedger root, UUID operation, long tick, Supplier<Outcome> accelerate) {
        var live = root.records().stream().map(EscrowOperation::id).collect(java.util.stream.Collectors.toSet());
        next.keySet().retainAll(live);
        if (!live.contains(operation)) { return new Result(Outcome.REFUSED, -1); }
        long eligible = next.getOrDefault(operation, 0L);
        if (tick < eligible) { return new Result(Outcome.COOLDOWN, eligible); }
        var outcome = accelerate.get();
        if (outcome == Outcome.ACCEPTED) { next.put(operation, tick + COOLDOWN_TICKS); }
        return new Result(outcome, next.getOrDefault(operation, -1L));
    }
    static boolean retryable(EscrowReason reason) {
        return switch (reason) {
            case BARRIER_IO, BARRIER_FORCE, BARRIER_UNSTABLE, SUPERSEDING_SAVE,
                    LEDGER_WRITE_FAILED, TRANSFER_JOURNAL_UNPROVED, WAITING_UNLOADED -> true;
            default -> false;
        };
    }
    void clear() { next.clear(); }
}
