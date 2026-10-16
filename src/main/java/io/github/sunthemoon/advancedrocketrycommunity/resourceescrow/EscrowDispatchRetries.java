package io.github.sunthemoon.advancedrocketrycommunity.resourceescrow;

import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFlightLimits;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketTransferRecord;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.Supplier;

/** Failed successor classification is scheduled by logical identity, never by proposed T or position. */
final class EscrowDispatchRetries {
    private static final class Wait {
        final EscrowSuccessorRetry retry;
        EscrowGovernedDispatch.Decision decision;
        Wait(EscrowSuccessorRetry retry) { this.retry = retry; }
    }
    private final Map<UUID, Wait> waits = new HashMap<>();
    private final Supplier<EscrowSuccessorRetry> factory;

    EscrowDispatchRetries() { factory = EscrowSuccessorRetry::new; }
    EscrowDispatchRetries(Consumer<EscrowSuccessorRetry.Hold> logger) {
        factory = () -> new EscrowSuccessorRetry(logger);
    }

    EscrowGovernedDispatch.Decision poll(RocketTransferRecord record, long tick,
                                        Supplier<EscrowGovernedDispatch.Decision> observation) {
        if (record.nativeTransfer().isEmpty()) { return observation.get(); }
        UUID logical = record.logicalRocketId();
        var wait = waits.get(logical);
        if (wait != null && !wait.retry.ready(tick)) { return wait.decision; }
        var decision = observation.get();
        if (decision.route() != EscrowGovernedDispatch.Route.HOLD) { waits.remove(logical); return decision; }
        // R10/R11/R14: ordinary acknowledgements and shutdown are silent, not failed attempts.
        if (silent(decision.reason())) { return decision; }
        if (wait == null) {
            // Pending journal removals cannot evict another held flight or throw through a server tick.
            if (waits.size() >= RocketFlightLimits.MAX_ACTIVE_TRANSFERS) { return decision; }
            wait = new Wait(factory.get()); waits.put(logical, wait);
        }
        wait.decision = decision;
        var arm = record.nativeTransfer().orElseThrow().armed();
        wait.retry.failed(arm.ledger(), logical, record.transferId(), arm.serial(),
                record.phase().name(), decision.reason(), tick);
        return decision;
    }

    private static boolean silent(String reason) {
        return reason.equals("GOVERNED_JOURNAL_UNACKNOWLEDGED") || reason.equals("GOVERNED_LEDGER_PENDING")
                || reason.equals("GOVERNED_RUNTIME_STOPPED");
    }

    void retain(Predicate<UUID> present) { waits.keySet().removeIf(logical -> !present.test(logical)); }
    void clear() { waits.clear(); }
}
