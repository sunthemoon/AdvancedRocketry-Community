package io.github.sunthemoon.advancedrocketrycommunity.rocket.server;

import static org.junit.jupiter.api.Assertions.*;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;

/** W152K: the refused caller's window on the server tick (W152J-R5) and what ineligible holds may do in it (W152J-R3). */
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

    /** W152J-R3: an ineligible hold leaves the window to a new caller, never extends it and never serves a pending one. */
    @Test void ineligibleHoldInTheWindowKeepsTheTurnOnlyForANewCallerUntilExpiry() {
        now.set(5); turns.reservationRefused();
        now.set(50); turns.sealedEnded();
        now.set(51); turns.ordinaryReservationHeld();
        assertTrue(turns.ordinaryReservationRoom(UUID.randomUUID(), false), "An ineligible hold spent the refused caller's window");
        assertFalse(turns.ordinaryReservationRoom(UUID.randomUUID(), true), "A pending logical renewed its token through the window");
        assertFalse(turns.sealedRoom());
        turns.ordinaryReservationHeld();
        now.set(52);
        assertTrue(turns.sealedRoom(), "An ineligible hold extended the window");
        assertFalse(turns.ordinaryReservationRoom(UUID.randomUUID(), false), "An ineligible hold kept the ordinary turn open after the window");
    }

    @Test void ineligibleHoldWithoutAWindowStillYieldsTheTurnAtOnce() {
        now.set(50); turns.sealedEnded(); turns.ordinaryReservationHeld();
        assertTrue(turns.sealedRoom()); assertFalse(turns.ordinaryReservationRoom(UUID.randomUUID(), false));
        now.set(55); turns.reservationRefused();
        assertFalse(turns.ordinaryReservationRoom(UUID.randomUUID(), false), "A refusal reopened a turn that a hold had yielded");
    }

    /** A native-window BUSY that keeps its queue place uses the window up and keeps the turn past it, as R117 kept it. */
    @Test void nativeBusyClaimUsesUpTheWindowAndKeepsItsPlace() {
        var claim = UUID.randomUUID();
        now.set(5); turns.reservationRefused();
        now.set(84); turns.sealedEnded();
        now.set(85); turns.ordinaryReservationHeld();
        assertTrue(turns.ordinaryReservationRoom(claim, false));
        assertTrue(turns.ordinaryRoom(claim, () -> true));
        turns.reservationAdmitted();
        assertFalse(turns.ordinaryReservationRoom(UUID.randomUUID(), false), "The used-up window admitted another caller");
        now.set(99); assertFalse(turns.sealedRoom()); assertTrue(turns.ordinaryRoom(claim, () -> true));
        now.set(100); turns.ordinaryStarted(claim);
        assertTrue(turns.sealedRoom()); assertFalse(turns.ordinaryReservationRoom(UUID.randomUUID(), false));
    }

    @Test void firstCaptureUsesUpTheWindowAndEndsTheTurn() {
        var claim = UUID.randomUUID();
        now.set(5); turns.reservationRefused();
        now.set(50); turns.sealedEnded();
        assertTrue(turns.ordinaryRoom(claim)); turns.ordinaryStarted(claim);
        assertTrue(turns.sealedRoom()); assertFalse(turns.ordinaryReservationRoom(UUID.randomUUID(), false));
    }
}
