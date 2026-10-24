package io.github.sunthemoon.advancedrocketrycommunity.rocket.flight;

/** Bounded, RAM-only display requirements. Not a debit, launch permit or persisted plan. */
public record RocketRequiredCosts(long primary, long oxidizer) {
    public static final long UNAVAILABLE = -1;
    public static final RocketRequiredCosts UNKNOWN = new RocketRequiredCosts(UNAVAILABLE, UNAVAILABLE);

    public RocketRequiredCosts {
        if (primary < UNAVAILABLE || primary > RocketFlightLimits.MAX_TRAVEL_FUEL
                || oxidizer < UNAVAILABLE || oxidizer > RocketFlightLimits.MAX_TRAVEL_FUEL) {
            throw new IllegalArgumentException("Required costs exceed the display bounds");
        }
    }

    static RocketRequiredCosts from(RocketFlightPlan plan) {
        if (plan == null) { return UNKNOWN; }
        var debit = plan.propulsionDebit();
        return debit == null ? new RocketRequiredCosts(plan.requiredFuel(), 0)
                : new RocketRequiredCosts(debit.primary(), debit.oxidizer());
    }
}
