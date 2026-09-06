package io.github.sunthemoon.advancedrocketrycommunity.rocket.menu;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketDestination;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFlightPlanner;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFlightState;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFuelState;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.stats.RocketStats;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class RocketFlightQuotesTest {
    private static final RocketStats STATS = new RocketStats(5, 210, 1000, 1000, 1, 1, 1, 1);
    private static final UUID PREVIEW = UUID.fromString("123e4567-e89b-42d3-a456-426614174742");
    private static final UUID REQUEST = UUID.fromString("123e4567-e89b-42d3-a456-426614174743");

    @Test
    void selectedStationDoesNotReuseTheDefaultMoonQuote() {
        var quotes = quotes(1000, RocketDestination.EARTH, RocketFlightState.FUELED);
        assertEquals(372, quotes.forDestination(RocketDestination.MOON).requiredFuel());
        assertEquals(330, quotes.forDestination(RocketDestination.SPACE_STATION).requiredFuel());
        assertFalse(quotes.earth().canLaunch());
        assertTrue(quotes.moon().canLaunch());
        assertTrue(quotes.station().canLaunch());
    }

    @Test
    void routeSpecificFuelThresholdControlsTheLaunchAffordance() {
        var quotes = quotes(330, RocketDestination.EARTH, RocketFlightState.FUELED);
        assertTrue(quotes.station().canLaunch());
        assertFalse(quotes.moon().canLaunch());
        assertEquals(372, quotes.moon().requiredFuel());
        assertFalse(quotes(329, RocketDestination.EARTH, RocketFlightState.FUELED).station().canLaunch());
    }

    @Test
    void stationDeparturesCanQuoteBothEarthAndMoonIndependently() {
        var quotes = quotes(247, RocketDestination.SPACE_STATION, RocketFlightState.LANDED);
        assertEquals(330, quotes.earth().requiredFuel());
        assertFalse(quotes.earth().canLaunch());
        assertEquals(247, quotes.moon().requiredFuel());
        assertTrue(quotes.moon().canLaunch());
        assertFalse(quotes.station().canLaunch());
    }

    @Test
    void everyExistingRouteMatchesTheLaunchPlanner() {
        for (var source : RocketDestination.values()) {
            var quotes = quotes(1000, source, RocketFlightState.FUELED);
            for (var target : RocketDestination.values()) {
                var result = RocketFlightPlanner.plan(STATS, fuel(1000), source.profile(), target.profile(),
                        target == RocketDestination.SPACE_STATION ? PREVIEW : null,
                        REQUEST, 42);
                assertEquals(result.requiredFuel(), quotes.forDestination(target).requiredFuel());
                assertEquals(result.success(), quotes.forDestination(target).canLaunch());
            }
        }
    }

    @Test
    void countdownKeepsTheCostButDisablesLaunch() {
        var quotes = quotes(1000, RocketDestination.EARTH, RocketFlightState.COUNTDOWN);
        assertEquals(330, quotes.station().requiredFuel());
        assertFalse(quotes.station().canLaunch());
        assertFalse(quotes.moon().canLaunch());
    }

    @Test
    void missingRouteOrComponentsCannotCreateAnAvailableQuote() {
        assertEquals(RocketFlightQuotes.empty(), quotes(1000, null, RocketFlightState.FUELED));
        assertEquals(new RocketFlightQuotes.Quote(0, false), RocketFlightQuotes.empty().forDestination(null));
        var invalid = new RocketStats(5, 210, 1000, 1000, 0, 1, 1, 1);
        var invalidQuotes = RocketFlightQuotes.compute(invalid, fuel(1000),
                RocketDestination.EARTH, RocketFlightState.FUELED);
        assertFalse(invalidQuotes.earth().canLaunch());
        assertFalse(invalidQuotes.moon().canLaunch());
        assertFalse(invalidQuotes.station().canLaunch());
    }

    @Test
    void quoteBoundsAndZeroFuelSuccessAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> new RocketFlightQuotes.Quote(-1, false));
        assertThrows(IllegalArgumentException.class, () -> new RocketFlightQuotes.Quote(0, true));
        assertThrows(IllegalArgumentException.class, () -> new RocketFlightQuotes.Quote(2_048_001, false));
    }

    private static RocketFlightQuotes quotes(long amount, RocketDestination source, RocketFlightState state) {
        return RocketFlightQuotes.compute(STATS, fuel(amount), source, state);
    }

    private static RocketFuelState fuel(long amount) {
        return RocketFuelState.empty(1000).fill(amount).state();
    }
}
