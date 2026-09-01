package io.github.sunthemoon.advancedrocketrycommunity.rocket.flight;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class RocketFlightStateMachineTest {
    @Test
    void completeEarthMoonStateSequenceIsExplicit() {
        RocketFlightState state = RocketFlightState.ASSEMBLED;
        state = applied(state, RocketFlightEvent.FUEL_AVAILABLE);
        state = applied(state, RocketFlightEvent.START_COUNTDOWN);
        state = applied(state, RocketFlightEvent.COUNTDOWN_COMPLETE);
        state = applied(state, RocketFlightEvent.ASCENT_COMPLETE);
        state = applied(state, RocketFlightEvent.DESTINATION_AUTHORITY_ACQUIRED);
        state = applied(state, RocketFlightEvent.LANDING_COMPLETE);

        assertEquals(RocketFlightState.LANDED, state);
        assertTrue(state.acceptsFuel());
        assertFalse(state.inMotion());
    }

    @Test
    void countdownCancellationAndRecoverableFailureHaveNarrowExits() {
        assertEquals(
                RocketFlightState.FUELED,
                applied(RocketFlightState.COUNTDOWN, RocketFlightEvent.CANCEL_COUNTDOWN)
        );
        assertEquals(
                RocketFlightState.FAILED_RECOVERABLE,
                applied(RocketFlightState.TRANSIT, RocketFlightEvent.MARK_FAILED)
        );
        assertEquals(
                RocketFlightState.ASSEMBLED,
                applied(RocketFlightState.FAILED_RECOVERABLE, RocketFlightEvent.RECOVER_ASSEMBLED)
        );
        assertEquals(
                RocketFlightState.FUELED,
                applied(RocketFlightState.FAILED_RECOVERABLE, RocketFlightEvent.RECOVER_FUELED)
        );
    }

    @Test
    void everyUnlistedTransitionIsRejectedWithoutChangingState() {
        int applied = 0;
        for (RocketFlightState state : RocketFlightState.values()) {
            for (RocketFlightEvent event : RocketFlightEvent.values()) {
                RocketFlightTransition transition = RocketFlightStateMachine.apply(state, event);
                if (transition.applied()) {
                    applied++;
                } else {
                    assertEquals(state, transition.next());
                }
            }
        }
        assertEquals(21, applied);
        assertFalse(RocketFlightStateMachine.isLegal(
                RocketFlightState.ASCENT,
                RocketFlightEvent.CANCEL_COUNTDOWN
        ));
        assertFalse(RocketFlightStateMachine.isLegal(
                RocketFlightState.DISASSEMBLED,
                RocketFlightEvent.MARK_FAILED
        ));
    }

    @Test
    void onlyStationarySafeStatesCanDisassemble() {
        assertEquals(
                RocketFlightState.DISASSEMBLED,
                applied(RocketFlightState.ASSEMBLED, RocketFlightEvent.DISASSEMBLE)
        );
        assertTrue(RocketFlightStateMachine.isLegal(
                RocketFlightState.FUELED,
                RocketFlightEvent.DISASSEMBLE
        ));
        assertTrue(RocketFlightStateMachine.isLegal(
                RocketFlightState.LANDED,
                RocketFlightEvent.DISASSEMBLE
        ));
        assertFalse(RocketFlightStateMachine.isLegal(
                RocketFlightState.COUNTDOWN,
                RocketFlightEvent.DISASSEMBLE
        ));
    }

    @Test
    void stableNetworkIdsRoundTripAndUnknownIdsFail() {
        for (RocketFlightState state : RocketFlightState.values()) {
            assertEquals(state, RocketFlightState.fromNetworkId(state.networkId()));
        }
        assertThrows(IllegalArgumentException.class, () -> RocketFlightState.fromNetworkId(-1));
        assertThrows(IllegalArgumentException.class, () -> RocketFlightState.fromNetworkId(99));
    }

    private static RocketFlightState applied(RocketFlightState state, RocketFlightEvent event) {
        RocketFlightTransition transition = RocketFlightStateMachine.apply(state, event);
        assertTrue(transition.applied(), () -> state + " should accept " + event);
        return transition.next();
    }
}
