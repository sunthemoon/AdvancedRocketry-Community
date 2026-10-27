package io.github.sunthemoon.advancedrocketrycommunity.rocket.server;

import static org.junit.jupiter.api.Assertions.*;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;

/** W152K: the refused caller's window on the server tick (W152J-R5). */
class SealedRefusedWindowTest {
    private final AtomicLong now = new AtomicLong();
    private final SealedProofTurns turns = new SealedProofTurns();
    { turns.bind(() -> true); turns.bindServerTick(now::get); }

    /** RR4: only sealed-side checks run, as while the transfer journal is unreadable; ordinary service never ticks. */
    @Test void windowClosesOnTheServerTickWithoutOrdinaryService() {
        turns.reservationRefused(); turns.sealedEnded();
        long room = -1;
        for (long tick = 0; tick < 10_000 && room < 0; tick++) { now.set(tick); if (turns.sealedRoom()) { room = tick; } }
        assertEquals(2, room, "Without ordinary service ticks the window must still close two server ticks after completion");
    }

    @Test void abandonedRefusalExpiresOnTheAttemptBoundWithoutOrdinaryService() {
        now.set(5); turns.reservationRefused();
        now.set(106); turns.sealedEnded();
        assertFalse(turns.sealedRoom(), "Completion must not lengthen the refusal's own bound");
        now.set(5 + RocketOrdinaryRetirement.ATTEMPT_TICKS + 2); assertTrue(turns.sealedRoom());
    }

    @Test void completionShortensTheWindowToTwoServerTicks() {
        now.set(5); turns.reservationRefused();
        now.set(50); turns.sealedEnded();
        now.set(51); assertFalse(turns.sealedRoom()); assertTrue(turns.ordinaryReservationRoom(UUID.randomUUID()));
        now.set(52); assertTrue(turns.sealedRoom());
    }
}
