package io.github.sunthemoon.advancedrocketrycommunity.rocket.flight;

import java.util.Objects;
import java.util.Optional;

public record RocketFlightPlanResult(
        RocketFlightPlanCode code,
        long requiredFuel,
        RocketFlightPlan plan,
        RocketRequiredCosts requiredCosts
) {
    public RocketFlightPlanResult(RocketFlightPlanCode code, long requiredFuel, RocketFlightPlan plan) {
        this(code, requiredFuel, plan, plan != null ? RocketRequiredCosts.from(plan)
                : requiredFuel > 0 && (code == RocketFlightPlanCode.INSUFFICIENT_FUEL || code == RocketFlightPlanCode.INSUFFICIENT_CAPACITY)
                ? new RocketRequiredCosts(requiredFuel, RocketRequiredCosts.UNAVAILABLE) : RocketRequiredCosts.UNKNOWN);
    }
    public RocketFlightPlanResult {
        Objects.requireNonNull(code, "code");
        Objects.requireNonNull(requiredCosts, "requiredCosts");
        if (requiredFuel < 0L || requiredFuel > RocketFlightLimits.MAX_TRAVEL_FUEL) {
            throw new IllegalArgumentException("Required fuel result is outside the fixed limit");
        }
        if ((code == RocketFlightPlanCode.SUCCESS) != (plan != null)) {
            throw new IllegalArgumentException("Only a successful plan result may contain a plan");
        }
    }

    public Optional<RocketFlightPlan> optionalPlan() {
        return Optional.ofNullable(plan);
    }

    public boolean success() {
        return code == RocketFlightPlanCode.SUCCESS;
    }

    public RocketFlightPlanResult withRequiredCosts(RocketRequiredCosts costs) {
        return new RocketFlightPlanResult(code, requiredFuel, plan, costs);
    }

    public static RocketFlightPlanResult failure(RocketFlightPlanCode code, long requiredFuel) {
        if (code == RocketFlightPlanCode.SUCCESS) {
            throw new IllegalArgumentException("Failure result cannot use SUCCESS");
        }
        return new RocketFlightPlanResult(code, requiredFuel, null);
    }
}
