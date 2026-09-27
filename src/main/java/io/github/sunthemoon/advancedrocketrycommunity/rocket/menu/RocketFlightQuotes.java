package io.github.sunthemoon.advancedrocketrycommunity.rocket.menu;

import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketDestination;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFlightLimits;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFlightPlanner;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFlightState;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFuelState;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.stats.RocketStats;
import io.github.sunthemoon.advancedrocketrycommunity.travel.migration.LegacyTravelTargetAdapter;
import io.github.sunthemoon.advancedrocketrycommunity.travel.model.TravelTarget;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/** Bounded server-authoritative target quotes for one open flight console. */
public final class RocketFlightQuotes {
    public static final int MAX_QUOTES = 160;
    private static final UUID PREVIEW_ID = UUID.fromString("123e4567-e89b-42d3-a456-426614174720");
    private static final Quote UNAVAILABLE = new Quote(0, false);

    private final List<TargetQuote> entries;
    private final Map<TravelTarget, Quote> byTarget;

    public RocketFlightQuotes(Quote earth, Quote moon, Quote station) {
        this(List.of(
                new TargetQuote(new TravelTarget.BodySurface(ModIdentity.id("earth")), earth),
                new TargetQuote(new TravelTarget.BodySurface(ModIdentity.id("moon")), moon),
                new TargetQuote(new TravelTarget.Station(PREVIEW_ID), station)
        ));
    }

    public RocketFlightQuotes(List<TargetQuote> entries) {
        Objects.requireNonNull(entries, "entries");
        if (entries.size() > MAX_QUOTES) {
            throw new IllegalArgumentException("Flight quote list exceeds the fixed bound");
        }
        LinkedHashMap<TravelTarget, Quote> checked = new LinkedHashMap<>();
        for (TargetQuote entry : entries) {
            Objects.requireNonNull(entry, "entry");
            if (checked.putIfAbsent(entry.target(), entry.quote()) != null) {
                throw new IllegalArgumentException("Flight quote list contains a duplicate target");
            }
        }
        this.entries = List.copyOf(entries);
        this.byTarget = Map.copyOf(checked);
    }

    public static RocketFlightQuotes empty() {
        return new RocketFlightQuotes(List.of());
    }

    public List<TargetQuote> entries() {
        return entries;
    }

    public Quote forTarget(TravelTarget target) {
        return target == null ? UNAVAILABLE : byTarget.getOrDefault(target, UNAVAILABLE);
    }

    public Quote forDestination(RocketDestination destination) {
        if (destination == null) {
            return UNAVAILABLE;
        }
        if (destination == RocketDestination.SPACE_STATION) {
            return entries.stream()
                    .filter(entry -> entry.target() instanceof TravelTarget.Station)
                    .map(TargetQuote::quote)
                    .findFirst()
                    .orElse(UNAVAILABLE);
        }
        return forTarget(LegacyTravelTargetAdapter.fromLegacy(destination, null));
    }

    public Quote earth() {
        return forTarget(new TravelTarget.BodySurface(ModIdentity.id("earth")));
    }

    public Quote moon() {
        return forTarget(new TravelTarget.BodySurface(ModIdentity.id("moon")));
    }

    public Quote station() {
        return forDestination(RocketDestination.SPACE_STATION);
    }

    public static RocketFlightQuotes compute(
            RocketStats stats,
            RocketFuelState fuel,
            RocketDestination source,
            RocketFlightState state
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
                quote(stats, fuel, source, RocketDestination.SPACE_STATION, state)
        );
    }

    private static Quote quote(
            RocketStats stats,
            RocketFuelState fuel,
            RocketDestination source,
            RocketDestination destination,
            RocketFlightState state
    ) {
        var result = RocketFlightPlanner.plan(
                stats,
                fuel,
                source.profile(),
                destination.profile(),
                destination == RocketDestination.SPACE_STATION ? PREVIEW_ID : null,
                PREVIEW_ID,
                0L
        );
        boolean launchableState = state == RocketFlightState.FUELED || state == RocketFlightState.LANDED;
        return new Quote(Math.toIntExact(result.requiredFuel()),
                RocketNavigationStatus.resolve(result.code(), launchableState, true));
    }

    @Override
    public boolean equals(Object candidate) {
        return candidate instanceof RocketFlightQuotes other && entries.equals(other.entries);
    }

    @Override
    public int hashCode() {
        return entries.hashCode();
    }

    @Override
    public String toString() {
        return "RocketFlightQuotes" + entries;
    }

    public record TargetQuote(TravelTarget target, Quote quote) {
        public TargetQuote {
            Objects.requireNonNull(target, "target");
            Objects.requireNonNull(quote, "quote");
        }
    }

    public record Quote(int requiredFuel, boolean canLaunch, RocketNavigationStatus status) {
        public Quote(int requiredFuel, boolean canLaunch) {
            this(requiredFuel, canLaunch, canLaunch ? RocketNavigationStatus.READY : RocketNavigationStatus.UNAVAILABLE);
        }

        public Quote(int requiredFuel, RocketNavigationStatus status) {
            this(requiredFuel, status == RocketNavigationStatus.READY, status);
        }

        public Quote {
            Objects.requireNonNull(status, "status");
            if (requiredFuel < 0 || requiredFuel > RocketFlightLimits.MAX_TRAVEL_FUEL
                    || (canLaunch && requiredFuel == 0) || canLaunch != (status == RocketNavigationStatus.READY)) {
                throw new IllegalArgumentException("Invalid bounded rocket fuel quote");
            }
        }
    }
}
