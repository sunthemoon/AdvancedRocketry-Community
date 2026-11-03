package io.github.sunthemoon.advancedrocketrycommunity.gametest;

import java.util.Objects;
import java.util.UUID;

/** Test-only two-stage deadline; only the requested transfer can start the unchanged landing budget. */
final class RocketFlightLegBudget {
    private final UUID requested;
    private final long preparationDeadline;
    private final int landingTicks;
    private long preparedAt = -1;

    RocketFlightLegBudget(UUID requested, long now, int preparationTicks, int landingTicks) {
        this.requested = Objects.requireNonNull(requested);
        if (now < 0 || preparationTicks <= 0 || landingTicks <= 0) {
            throw new IllegalArgumentException("Flight fixture budgets must be positive");
        }
        preparationDeadline = now + preparationTicks;
        this.landingTicks = landingTicks;
    }

    void observe(long tick, UUID preparedTransfer) {
        if (!prepared() && tick <= preparationDeadline && requested.equals(preparedTransfer)) { preparedAt = tick; }
    }

    boolean prepared() { return preparedAt >= 0; }

    boolean expired(long tick) { return tick >= (prepared() ? preparedAt + landingTicks : preparationDeadline); }

    long latestDeadline() { return preparationDeadline + landingTicks; }
}
