package io.github.sunthemoon.advancedrocketrycommunity.rocket.flight;

import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.stats.RocketStats;
import java.util.Objects;
import java.util.UUID;
import net.minecraft.resources.ResourceLocation;

/** Pure server-side Earth/Moon/Space-station reachability and fuel quotation. */
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
    public static final RocketTravelProfile SPACE_STATION = new RocketTravelProfile(
            ModIdentity.id("space"),
            ModIdentity.id("space"),
            0,
            25
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
        return plan(stats, fuel, source, destination, null, requestId, gameTime);
    }

    public static RocketFlightPlanResult plan(
            RocketStats stats,
            RocketFuelState fuel,
            RocketTravelProfile source,
            RocketTravelProfile destination,
            UUID destinationStationId,
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
                destinationStationId,
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
        if (SPACE_STATION.dimensionId().equals(dimensionId)) {
            return SPACE_STATION;
        }
        throw new IllegalArgumentException("Dimension is not a rocket destination: " + dimensionId);
    }

    public static RocketTravelProfile forBody(net.minecraft.resources.ResourceLocation bodyId) {
        Objects.requireNonNull(bodyId, "bodyId");
        if (EARTH.bodyId().equals(bodyId)) {
            return EARTH;
        }
        if (MOON.bodyId().equals(bodyId)) {
            return MOON;
        }
        if (SPACE_STATION.bodyId().equals(bodyId)) {
            return SPACE_STATION;
        }
        throw new IllegalArgumentException("Body is not a rocket destination: " + bodyId);
    }

    private static boolean supported(RocketTravelProfile source, RocketTravelProfile destination) {
        return !source.equals(destination)
                && (source.equals(EARTH) || source.equals(MOON) || source.equals(SPACE_STATION))
                && (destination.equals(EARTH)
                || destination.equals(MOON)
                || destination.equals(SPACE_STATION));
    }

    private static ResourceLocation requiredLocation(String namespace, String path) {
        ResourceLocation location = ResourceLocation.tryBuild(namespace, path);
        if (location == null) {
            throw new IllegalStateException("Invalid built-in rocket destination identifier");
        }
        return location;
    }
}
