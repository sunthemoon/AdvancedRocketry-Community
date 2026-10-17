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
    private final Map<Object, Long> next = new HashMap<>();
    Result request(EscrowLedger root, UUID operation, long tick, Supplier<Outcome> accelerate) {
        var live = new java.util.HashSet<>(); Object key = null;
        for (var op : root.records()) {
            var unit = unit(op); live.add(unit);
            if (op.id().equals(operation)) { key = unit; }
        }
        next.keySet().retainAll(live);
        if (key == null) { return new Result(Outcome.REFUSED, -1); }
        long eligible = next.getOrDefault(key, 0L);
        if (tick < eligible) { return new Result(Outcome.COOLDOWN, eligible); }
        var outcome = accelerate.get();
        if (outcome == Outcome.ACCEPTED) { next.put(key, tick + COOLDOWN_TICKS); }
        return new Result(outcome, next.getOrDefault(key, -1L));
    }
    /** The retried unit: a settled member's frozen intent (all members share one cooldown), otherwise the operation. */
    static Object unit(EscrowOperation op) {
        var settlement = op.settlement();
        return settlement.isEmpty() ? op.id() : EscrowTags.string(settlement, "digest");
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
