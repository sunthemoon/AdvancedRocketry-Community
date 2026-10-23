package io.github.sunthemoon.advancedrocketrycommunity.resourceescrow;

import io.github.sunthemoon.advancedrocketrycommunity.rocket.transaction.RocketOperationLedger;
import java.util.UUID;
import java.util.function.IntFunction;

/** Test-only player request policy; readiness remains the product's authority and retry checks. */
final class W151NativePlayerRequests {
    private final IntFunction<UUID> ids;
    private UUID current;
    private int attempts;
    W151NativePlayerRequests(IntFunction<UUID> ids) { this.ids = ids; }
    UUID current() { return current; }
    int attempts() { return attempts; }
    UUID next(boolean ready, RocketOperationLedger.Outcome previous) {
        if (!ready || current != null && previous != RocketOperationLedger.Outcome.FAILED) { return null; }
        current = ids.apply(attempts++); return current;
    }
}
