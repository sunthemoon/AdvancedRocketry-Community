package io.github.sunthemoon.advancedrocketrycommunity.rocket.flight;

import java.util.Objects;
import java.util.Optional;

public record RocketFlightPlanResult(
        RocketFlightPlanCode code,
        long requiredFuel,
        RocketFlightPlan plan
) {
    public RocketFlightPlanResult {
        Objects.requireNonNull(code, "code");
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

    public static RocketFlightPlanResult failure(RocketFlightPlanCode code, long requiredFuel) {
        if (code == RocketFlightPlanCode.SUCCESS) {
            throw new IllegalArgumentException("Failure result cannot use SUCCESS");
        }
        return new RocketFlightPlanResult(code, requiredFuel, null);
    }
}
