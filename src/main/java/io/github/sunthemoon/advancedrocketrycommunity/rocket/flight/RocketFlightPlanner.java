package io.github.sunthemoon.advancedrocketrycommunity.rocket.flight;

import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.stats.RocketStats;
import java.util.Objects;
import java.util.UUID;
import net.minecraft.resources.ResourceLocation;

/** Pure server-side Earth/Moon reachability and fuel quotation. */
public final class RocketFlightPlanner {
    public static final RocketTravelProfile EARTH = new RocketTravelProfile(
            ModIdentity.id("earth"),
            requiredLocation("minecraft", "overworld"),
            1_000,
            0
    );
    public static final RocketTravelProfile MOON = new RocketTravelProfile(
            ModIdentity.id("moon"),
            ModIdentity.id("moon"),
            165,
            50
    );

    private RocketFlightPlanner() {
    }

    public static RocketFlightPlanResult plan(
            RocketStats stats,
            RocketFuelState fuel,
            RocketTravelProfile source,
            RocketTravelProfile destination,
            UUID requestId,
            long gameTime
    ) {
        Objects.requireNonNull(stats, "stats");
        Objects.requireNonNull(fuel, "fuel");
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(destination, "destination");
        Objects.requireNonNull(requestId, "requestId");
        if (source.bodyId().equals(destination.bodyId())
                || source.dimensionId().equals(destination.dimensionId())) {
            return RocketFlightPlanResult.failure(RocketFlightPlanCode.SAME_DESTINATION, 0L);
        }
        if (!supported(source, destination)) {
            return RocketFlightPlanResult.failure(RocketFlightPlanCode.UNSUPPORTED_ROUTE, 0L);
        }
        if (!stats.hasFlightComponents()) {
            return RocketFlightPlanResult.failure(
                    RocketFlightPlanCode.MISSING_FLIGHT_COMPONENTS,
                    0L
            );
        }
        if (!stats.hasSufficientThrust()) {
            return RocketFlightPlanResult.failure(RocketFlightPlanCode.INSUFFICIENT_THRUST, 0L);
        }
        if (fuel.capacity() != stats.fuelCapacity()) {
            return RocketFlightPlanResult.failure(RocketFlightPlanCode.FUEL_STATE_MISMATCH, 0L);
        }

        long requiredFuel;
        try {
            long massCost = Math.addExact(stats.mass(), 1L) / 2L;
            long gravitySum = Math.addExact(source.gravityMilli(), destination.gravityMilli());
            long gravityCost = Math.addExact(gravitySum, 9L) / 10L;
            long distanceCost = Math.abs(Math.subtractExact(
                    (long) source.routeDistanceUnits(),
                    destination.routeDistanceUnits()
            ));
            requiredFuel = Math.addExact(
                    RocketFlightLimits.BASE_TRAVEL_FUEL,
                    Math.addExact(massCost, Math.addExact(gravityCost, distanceCost))
            );
        } catch (ArithmeticException exception) {
            return RocketFlightPlanResult.failure(RocketFlightPlanCode.ARITHMETIC_OVERFLOW, 0L);
        }
        if (requiredFuel <= 0L || requiredFuel > RocketFlightLimits.MAX_TRAVEL_FUEL) {
            return RocketFlightPlanResult.failure(RocketFlightPlanCode.ARITHMETIC_OVERFLOW, 0L);
        }
        if (fuel.capacity() < requiredFuel) {
            return RocketFlightPlanResult.failure(
                    RocketFlightPlanCode.INSUFFICIENT_CAPACITY,
                    requiredFuel
            );
        }
        if (fuel.amount() < requiredFuel) {
            return RocketFlightPlanResult.failure(
                    RocketFlightPlanCode.INSUFFICIENT_FUEL,
                    requiredFuel
            );
        }
        RocketFlightPlan plan = new RocketFlightPlan(
                RocketFlightPlan.SCHEMA_VERSION,
                requestId,
                source.bodyId(),
                destination.bodyId(),
                source.dimensionId(),
                destination.dimensionId(),
                requiredFuel,
                gameTime
        );
        return new RocketFlightPlanResult(RocketFlightPlanCode.SUCCESS, requiredFuel, plan);
    }

    public static RocketTravelProfile forDimension(net.minecraft.resources.ResourceLocation dimensionId) {
        Objects.requireNonNull(dimensionId, "dimensionId");
        if (EARTH.dimensionId().equals(dimensionId)) {
            return EARTH;
        }
        if (MOON.dimensionId().equals(dimensionId)) {
            return MOON;
        }
        throw new IllegalArgumentException("Dimension is not a v0.6 rocket destination: " + dimensionId);
    }

    public static RocketTravelProfile forBody(net.minecraft.resources.ResourceLocation bodyId) {
        Objects.requireNonNull(bodyId, "bodyId");
        if (EARTH.bodyId().equals(bodyId)) {
            return EARTH;
        }
        if (MOON.bodyId().equals(bodyId)) {
            return MOON;
        }
        throw new IllegalArgumentException("Body is not a v0.6 rocket destination: " + bodyId);
    }

    private static boolean supported(RocketTravelProfile source, RocketTravelProfile destination) {
        return (source.equals(EARTH) && destination.equals(MOON))
                || (source.equals(MOON) && destination.equals(EARTH));
    }

    private static ResourceLocation requiredLocation(String namespace, String path) {
        ResourceLocation location = ResourceLocation.tryBuild(namespace, path);
        if (location == null) {
            throw new IllegalStateException("Invalid built-in rocket destination identifier");
        }
        return location;
    }
}
