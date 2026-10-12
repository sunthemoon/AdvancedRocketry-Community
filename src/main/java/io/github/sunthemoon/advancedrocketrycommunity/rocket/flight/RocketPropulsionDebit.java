package io.github.sunthemoon.advancedrocketrycommunity.rocket.flight;

/** Immutable complete debit captured by the accepted plan. No catalog access during transfer. */
public record RocketPropulsionDebit(long primary, long oxidizer, long revision,
        String resourceSignature, String catalogSignature) {
    public RocketPropulsionDebit {
        if (primary < 0 || primary > RocketFlightLimits.MAX_TRAVEL_FUEL
                || oxidizer < 0 || oxidizer > RocketFlightLimits.MAX_TRAVEL_FUEL || revision < 0
                || !resourceSignature.matches("[0-9a-f]{64}") || !catalogSignature.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("Invalid propulsion debit vector");
        }
    }
}
