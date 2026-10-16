package io.github.sunthemoon.advancedrocketrycommunity.resourceescrow;

import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFlightLimits;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketTransferRecord;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Predicate;

/** Logical-flight retry history outlives replaceable F6 captures, but never the owning runtime. */
final class EscrowTerminalRetries {
    private final Map<UUID, EscrowSuccessorRetry> histories = new HashMap<>();
    private final java.util.function.Supplier<EscrowSuccessorRetry> factory;

    EscrowTerminalRetries() { factory = EscrowSuccessorRetry::new; }
    EscrowTerminalRetries(Consumer<EscrowSuccessorRetry.Hold> logger) {
        factory = () -> new EscrowSuccessorRetry(logger);
    }

    boolean ready(UUID logical, long tick) {
        var history = histories.get(logical);
        return history == null || history.ready(tick);
    }

    EscrowSuccessorRetry history(UUID logical) {
        EscrowTags.require(histories.containsKey(logical) || histories.size() < RocketFlightLimits.MAX_ACTIVE_TRANSFERS,
                "Terminal retry history limit exceeded");
        return histories.computeIfAbsent(logical, key -> factory.get());
    }

    void failed(RocketTransferRecord record, String phase, String reason, long tick) {
        var arm = record.nativeTransfer().orElseThrow().armed();
        history(record.logicalRocketId()).failed(arm.ledger(), arm.logical(), arm.transfer(), arm.serial(), phase, reason, tick);
    }

    void completed(UUID logical) { histories.remove(logical); }
    void retain(Predicate<UUID> present) { histories.keySet().removeIf(logical -> !present.test(logical)); }
    void clear() { histories.clear(); }
}
