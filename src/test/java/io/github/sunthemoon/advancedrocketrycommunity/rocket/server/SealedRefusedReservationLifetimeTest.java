package io.github.sunthemoon.advancedrocketrycommunity.rocket.server;

import static org.junit.jupiter.api.Assertions.*;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class SealedRefusedReservationLifetimeTest {
    @Test void refusalExpiresAfterTheAttemptBoundEvenWithoutACompletionCallback() {
        var turns = new SealedProofTurns(); turns.bind(() -> true);
        turns.reservationRefused();
        for (int tick = 0; tick < RocketOrdinaryRetirement.ATTEMPT_TICKS + 2; tick++) { turns.tick(); }
        turns.sealedEnded();
        assertTrue(turns.sealedRoom(), "An abandoned refusal outlived its bound");
    }

    @Test void sealedCompletionAllowsTwoServiceTicksForTheProducerToRetry() {
        var turns = new SealedProofTurns(); turns.bind(() -> true);
        turns.reservationRefused(); turns.sealedEnded();
        assertFalse(turns.sealedRoom());
        turns.tick(); assertFalse(turns.sealedRoom());
        assertTrue(turns.ordinaryReservationRoom(UUID.randomUUID()));
        turns.tick(); assertTrue(turns.sealedRoom());
    }

    @Test void manyRefusedCallersDoNotAccumulateHandoffWindows() {
        var turns = new SealedProofTurns(); turns.bind(() -> true);
        for (int caller = 0; caller < 1_000; caller++) { turns.reservationRefused(); }
        turns.sealedEnded(); turns.tick(); turns.tick();
        assertTrue(turns.sealedRoom());
    }

    @Test void anonymousRefusalDoesNotPassAnOlderDueReturn() {
        var turns = new SealedProofTurns(); turns.bind(() -> true);
        var older = UUID.randomUUID();
        turns.ordinaryRoom(older); turns.reservationRefused(); turns.sealedEnded();
        assertFalse(turns.ordinaryReservationRoom(UUID.randomUUID()));
        assertTrue(turns.ordinaryReservationRoom(older));
        turns.ordinaryStarted(older); assertTrue(turns.sealedRoom());
    }

    @Test void admissionAndStopClearTheAnonymousSignal() {
        var turns = new SealedProofTurns(); turns.bind(() -> true);
        turns.reservationRefused(); turns.sealedEnded(); turns.reservationAdmitted();
        assertTrue(turns.sealedRoom());
        turns.reservationRefused(); turns.clear(); turns.sealedEnded();
        assertTrue(turns.sealedRoom());
    }
}
