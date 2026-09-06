package io.github.sunthemoon.advancedrocketrycommunity.rocket.menu;

import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketDestination;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFlightLimits;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFlightPlanner;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFlightState;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFuelState;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.stats.RocketStats;
import java.util.Objects;
import java.util.UUID;

/** Three bounded server quotes, not station permissions or landing reservations. */
public record RocketFlightQuotes(Quote earth, Quote moon, Quote station) {
    private static final UUID PREVIEW_ID = new UUID(0L, 0L);
    private static final Quote UNAVAILABLE = new Quote(0, false);

    public RocketFlightQuotes {
        Objects.requireNonNull(earth, "earth");
        Objects.requireNonNull(moon, "moon");
        Objects.requireNonNull(station, "station");
    }

    public static RocketFlightQuotes empty() {
        return new RocketFlightQuotes(UNAVAILABLE, UNAVAILABLE, UNAVAILABLE);
    }

    public Quote forDestination(RocketDestination destination) {
        if (destination == null) {
            return UNAVAILABLE;
        }
        return switch (destination) {
            case EARTH -> earth;
            case MOON -> moon;
            case SPACE_STATION -> station;
        };
    }

    public static RocketFlightQuotes compute(
            RocketStats stats, RocketFuelState fuel, RocketDestination source, RocketFlightState state
    ) {
        Objects.requireNonNull(stats, "stats");
        Objects.requireNonNull(fuel, "fuel");
        Objects.requireNonNull(state, "state");
        if (source == null) {
            return empty();
        }
        return new RocketFlightQuotes(
                quote(stats, fuel, source, RocketDestination.EARTH, state),
                quote(stats, fuel, source, RocketDestination.MOON, state),
                quote(stats, fuel, source, RocketDestination.SPACE_STATION, state));
    }

    private static Quote quote(RocketStats stats, RocketFuelState fuel, RocketDestination source,
                               RocketDestination destination, RocketFlightState state) {
        var result = RocketFlightPlanner.plan(stats, fuel, source.profile(), destination.profile(),
                destination == RocketDestination.SPACE_STATION ? PREVIEW_ID : null, PREVIEW_ID, 0L);
        boolean launchableState = state == RocketFlightState.FUELED || state == RocketFlightState.LANDED;
        return new Quote(Math.toIntExact(result.requiredFuel()), launchableState && result.success());
    }

    public record Quote(int requiredFuel, boolean canLaunch) {
        public Quote {
            if (requiredFuel < 0 || requiredFuel > RocketFlightLimits.MAX_TRAVEL_FUEL
                    || (canLaunch && requiredFuel == 0)) {
                throw new IllegalArgumentException("Invalid bounded rocket fuel quote");
            }
        }
    }
}
