package io.github.sunthemoon.advancedrocketrycommunity.rocket.flight;

import io.github.sunthemoon.advancedrocketrycommunity.celestial.CelestialIds;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.model.CelestialBodyDefinition;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.service.CelestialCatalog;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.stats.RocketStats;
import io.github.sunthemoon.advancedrocketrycommunity.station.model.StationState;
import io.github.sunthemoon.advancedrocketrycommunity.travel.model.TravelTarget;
import io.github.sunthemoon.advancedrocketrycommunity.travel.route.model.RouteAnchor;
import io.github.sunthemoon.advancedrocketrycommunity.travel.route.model.RoutePlan;
import io.github.sunthemoon.advancedrocketrycommunity.travel.route.service.RouteCatalog;
import io.github.sunthemoon.advancedrocketrycommunity.travel.route.service.TravelFuelFormula;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import net.minecraft.resources.ResourceLocation;

/** Pure typed-target planner backed by immutable celestial and route snapshots. */
public final class RocketTargetFlightPlanner {
    private RocketTargetFlightPlanner() {
    }

    public static RocketFlightPlanResult plan(
            RocketStats stats,
            RocketFuelState fuel,
            TravelTarget source,
            ResourceLocation sourceDimension,
            TravelTarget destination,
            CelestialCatalog celestial,
            RouteCatalog routes,
            Function<UUID, Optional<StationState>> stations,
            UUID requestId,
            long gameTime
    ) {
        Objects.requireNonNull(stats, "stats");
        Objects.requireNonNull(fuel, "fuel");
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(sourceDimension, "sourceDimension");
        Objects.requireNonNull(destination, "destination");
        Objects.requireNonNull(celestial, "celestial");
        Objects.requireNonNull(routes, "routes");
        Objects.requireNonNull(stations, "stations");
        Objects.requireNonNull(requestId, "requestId");
        if (source.equals(destination)) {
            return RocketFlightPlanResult.failure(RocketFlightPlanCode.SAME_DESTINATION, 0L);
        }

        ResolvedTarget resolvedSource = resolve(source, celestial, stations, false).orElse(null);
        ResolvedTarget resolvedDestination = resolve(destination, celestial, stations, true).orElse(null);
        if (resolvedSource == null
                || resolvedDestination == null
                || !resolvedSource.dimensionId().equals(sourceDimension)) {
            return RocketFlightPlanResult.failure(RocketFlightPlanCode.UNSUPPORTED_ROUTE, 0L);
        }
        if (!stats.hasFlightComponents()) {
            return RocketFlightPlanResult.failure(RocketFlightPlanCode.MISSING_FLIGHT_COMPONENTS, 0L);
        }
        if (!stats.hasSufficientThrust()) {
            return RocketFlightPlanResult.failure(RocketFlightPlanCode.INSUFFICIENT_THRUST, 0L);
        }
        if (fuel.capacity() != stats.fuelCapacity()) {
            return RocketFlightPlanResult.failure(RocketFlightPlanCode.FUEL_STATE_MISMATCH, 0L);
        }

        RoutePlan route = routes.plan(resolvedSource.anchor(), resolvedDestination.anchor())
                .result()
                .orElse(null);
        if (route == null) {
            return RocketFlightPlanResult.failure(RocketFlightPlanCode.UNSUPPORTED_ROUTE, 0L);
        }
        Optional<Long> quoted = TravelFuelFormula.calculate(
                stats.mass(),
                resolvedSource.gravityMilli(),
                resolvedDestination.gravityMilli(),
                route.totalDistanceUnits(),
                RocketFlightLimits.MAX_TRAVEL_FUEL
        ).result();
        if (quoted.isEmpty()) {
            return RocketFlightPlanResult.failure(RocketFlightPlanCode.ARITHMETIC_OVERFLOW, 0L);
        }
        long requiredFuel = quoted.orElseThrow();
        if (fuel.capacity() < requiredFuel) {
            return RocketFlightPlanResult.failure(RocketFlightPlanCode.INSUFFICIENT_CAPACITY, requiredFuel);
        }
        if (fuel.amount() < requiredFuel) {
            return RocketFlightPlanResult.failure(RocketFlightPlanCode.INSUFFICIENT_FUEL, requiredFuel);
        }

        UUID destinationStation = destination instanceof TravelTarget.Station station
                ? station.instanceId()
                : null;
        RocketFlightPlan plan = new RocketFlightPlan(
                RocketFlightPlan.SCHEMA_VERSION,
                requestId,
                resolvedSource.bodyId(),
                resolvedDestination.bodyId(),
                sourceDimension,
                resolvedDestination.dimensionId(),
                destinationStation,
                requiredFuel,
                gameTime,
                destination
        );
        return new RocketFlightPlanResult(RocketFlightPlanCode.SUCCESS, requiredFuel, plan);
    }

    private static Optional<ResolvedTarget> resolve(
            TravelTarget target,
            CelestialCatalog celestial,
            Function<UUID, Optional<StationState>> stations,
            boolean arrival
    ) {
        if (target instanceof TravelTarget.BodySurface surface) {
            return celestial.get(surface.bodyId())
                    .filter(body -> !arrival || body.supportsSurfaceArrival())
                    .flatMap(body -> body.levelKey()
                            .filter(level -> !level.equals(CelestialIds.SPACE_LEVEL))
                            .map(level -> new ResolvedTarget(
                    RouteAnchor.bodySurface(body.id()),
                    body.id(),
                    level.location(),
                    gravityMilli(body)
            )));
        }
        if (target instanceof TravelTarget.Orbit orbit) {
            return celestial.get(orbit.bodyId())
                    .filter(body -> !arrival || body.capabilities().orbitable())
                    .map(body -> new ResolvedTarget(
                    RouteAnchor.orbit(body.id()),
                    body.id(),
                    CelestialIds.SPACE_LEVEL.location(),
                    0
            ));
        }
        if (target instanceof TravelTarget.Station station) {
            return stations.apply(station.instanceId())
                    .flatMap(state -> celestial.get(state.orbitBody())
                            .filter(body -> !arrival || body.capabilities().orbitable())
                            .map(body -> new ResolvedTarget(
                            RouteAnchor.orbit(body.id()),
                            body.id(),
                            CelestialIds.SPACE_LEVEL.location(),
                            0
                    )));
        }
        return Optional.empty();
    }

    private static int gravityMilli(CelestialBodyDefinition body) {
        return Math.toIntExact(Math.round(body.gravityMultiplier() * 1_000.0D));
    }

    private record ResolvedTarget(
            RouteAnchor anchor,
            ResourceLocation bodyId,
            ResourceLocation dimensionId,
            int gravityMilli
    ) {
    }
}
