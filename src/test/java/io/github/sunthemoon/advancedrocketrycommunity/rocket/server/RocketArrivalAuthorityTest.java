package io.github.sunthemoon.advancedrocketrycommunity.rocket.server;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFlightState;
import java.util.Set;
import org.junit.jupiter.api.Test;

class RocketArrivalAuthorityTest {
    @Test
    void emptyAndFueledArrivalsShareLandedReservationSettlement() {
        var stationary = Set.of(RocketFlightState.ASSEMBLED, RocketFlightState.FUELED, RocketFlightState.LANDED);
        for (var state : RocketFlightState.values()) {
            assertEquals(stationary.contains(state), RocketTransferEntities.isStationaryArrival(state), state.name());
        }
    }
}
