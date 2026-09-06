package io.github.sunthemoon.advancedrocketrycommunity.rocket.server;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFlightState;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketTransferPhase;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class RocketFlightReleaseCheckpointTest {
    @ParameterizedTest
    @CsvSource({
            "COUNTDOWN, PREPARED, COUNTDOWN",
            "ASCENT, PREPARED, ASCENT",
            "TRANSIT_PREPARED, PREPARED, TRANSIT",
            "DESTINATION_SPAWNED, DESTINATION_SPAWNED, DESCENT",
            "PASSENGERS_TRANSFERRED, PASSENGERS_TRANSFERRED, DESCENT",
            "SOURCE_REMOVED, SOURCE_REMOVED, DESCENT",
            "DESCENT, COMMITTED, DESCENT",
            "LANDED, COMMITTED, LANDED"
    })
    void observesExactDurablePhase(
            RocketFlightReleaseCheckpoint checkpoint,
            RocketTransferPhase phase,
            RocketFlightState state
    ) {
        assertTrue(checkpoint.reached(phase, state));
        for (RocketTransferPhase other : RocketTransferPhase.values()) {
            if (other != phase) {
                assertFalse(checkpoint.reached(other, state), () -> checkpoint + " accepted " + other);
            }
        }
    }

    @ParameterizedTest
    @CsvSource({
            "COUNTDOWN, PREPARED, ASCENT",
            "ASCENT, PREPARED, TRANSIT",
            "TRANSIT_PREPARED, PREPARED, COUNTDOWN",
            "DESCENT, COMMITTED, LANDED",
            "LANDED, COMMITTED, DESCENT"
    })
    void sharedJournalPhaseDoesNotConflateFlightStates(
            RocketFlightReleaseCheckpoint checkpoint,
            RocketTransferPhase phase,
            RocketFlightState state
    ) {
        assertFalse(checkpoint.reached(phase, state));
    }
}
