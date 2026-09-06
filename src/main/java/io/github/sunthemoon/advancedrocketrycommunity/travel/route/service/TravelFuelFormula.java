package io.github.sunthemoon.advancedrocketrycommunity.travel.route.service;

import com.mojang.serialization.DataResult;
import io.github.sunthemoon.advancedrocketrycommunity.travel.route.model.RouteLimits;

/** Pure shared fuel formula for legacy profiles and data-driven route plans. */
public final class TravelFuelFormula {
    public static final long BASE_TRAVEL_FUEL = 100L;
    public static final long MAX_ROUTE_PLAN_DISTANCE =
            (long) RouteLimits.MAX_ROUTES * RouteLimits.MAX_DISTANCE_UNITS;

    private TravelFuelFormula() {
    }

    public static DataResult<Long> calculate(
            long mass,
            int sourceGravityMilli,
            int destinationGravityMilli,
            long routeDistanceUnits,
            long maximumFuel
    ) {
        if (mass < 0L) {
            return DataResult.error(() -> "Rocket mass cannot be negative");
        }
        if (sourceGravityMilli < 0 || sourceGravityMilli > 10_000
                || destinationGravityMilli < 0 || destinationGravityMilli > 10_000) {
            return DataResult.error(() -> "Travel gravity is outside 0..10000 milli-g");
        }
        if (routeDistanceUnits < 0L || routeDistanceUnits > MAX_ROUTE_PLAN_DISTANCE) {
            return DataResult.error(() -> "Route-plan distance is outside its fixed bound");
        }
        if (maximumFuel <= 0L) {
            return DataResult.error(() -> "Maximum fuel must be positive");
        }

        try {
            long massCost = Math.addExact(mass, 1L) / 2L;
            long gravitySum = Math.addExact(sourceGravityMilli, destinationGravityMilli);
            long gravityCost = Math.addExact(gravitySum, 9L) / 10L;
            long requiredFuel = Math.addExact(
                    BASE_TRAVEL_FUEL,
                    Math.addExact(massCost, Math.addExact(gravityCost, routeDistanceUnits))
            );
            if (requiredFuel <= 0L || requiredFuel > maximumFuel) {
                return DataResult.error(() -> "Required fuel is outside the configured capacity bound");
            }
            return DataResult.success(requiredFuel);
        } catch (ArithmeticException exception) {
            return DataResult.error(() -> "Travel fuel calculation overflow");
        }
    }
}
