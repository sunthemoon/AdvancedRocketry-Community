package io.github.sunthemoon.advancedrocketrycommunity.gametest;

import static org.junit.jupiter.api.Assertions.*;

import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketTransferRecoveryReport.Status;
import org.junit.jupiter.api.Test;

/**
 * W381: the recovery fixture waits for a source-side proof while the product's return is on its first clean attempt,
 * not for a fixed 20 or 40 ticks that only a Level with an open force window and an instant disk can meet.
 */
class RocketTransferRecoveryWaitTest {
    private static final int ATTEMPT_TICKS = 100;

    @Test void aProofStillInProgressOutlivesTheFormerBounds() {
        // W381 run repro1: a Level's first force batch comes 20 ticks after the capture and its proof 2 ticks later, so
        // the source-only case needed call 22; the two-slot case with one first batch needed call 40 with an instant disk.
        var wait = new RocketTransferRecoveryWait(ATTEMPT_TICKS, 0, 0);
        for (int attempt = 1; attempt <= ATTEMPT_TICKS; attempt++) {
            assertTrue(wait.retry(Status.RETRY_LATER, attempt, true, attempt, 0), "attempt " + attempt);
        }
        assertTrue(RocketTransferRecoveryWait.FORMER_SOURCE_ONLY_ATTEMPTS < 22 && RocketTransferRecoveryWait.FORMER_BOTH_ATTEMPTS <= 40);
    }

    @Test void theWaitEndsWithTheProductsAllowanceForOneAttempt() {
        var wait = new RocketTransferRecoveryWait(ATTEMPT_TICKS, 0, 0);
        assertFalse(wait.retry(Status.RETRY_LATER, ATTEMPT_TICKS + 1, true, ATTEMPT_TICKS + 1, 0));
    }

    @Test void theWaitEndsAtOnceWhenTheReturnIsNoLongerOnItsFirstCleanAttempt() {
        var wait = new RocketTransferRecoveryWait(ATTEMPT_TICKS, 0, 0);
        assertFalse(wait.retry(Status.RETRY_LATER, 1, false, 1, 0));
        // The manager dropped the return, started a second attempt, or recorded a wait or a failure.
        assertFalse(RocketTransferRecoveryWait.firstCleanAttempt(false, -1, null));
        assertFalse(RocketTransferRecoveryWait.firstCleanAttempt(true, 2, null));
        for (String reason : new String[] {"DEADLINE", "BARRIER_IO", "BARRIER_UNSTABLE", "SUPERSEDING_SAVE", "SURVIVOR_CHANGED",
                "SLOT_UNLOADED", "SAVE_OFF", "CREW_WAITING", "CENSUS_LIMIT", "UNAVAILABLE IllegalStateException"}) {
            assertFalse(RocketTransferRecoveryWait.firstCleanAttempt(true, 1, reason), reason);
        }
        assertTrue(RocketTransferRecoveryWait.firstCleanAttempt(true, 1, null));
        assertTrue(RocketTransferRecoveryWait.firstCleanAttempt(true, 0, "BUSY"));
    }

    @Test void onlyRetryLaterIsWaitedFor() {
        var wait = new RocketTransferRecoveryWait(ATTEMPT_TICKS, 0, 0);
        for (Status status : Status.values()) {
            assertEquals(status == Status.RETRY_LATER, wait.retry(status, 1, true, 1, 0), status.name());
        }
    }

    @Test void theFailureTextCarriesTheWaitAndReadsTheObservationOnce() {
        var wait = new RocketTransferRecoveryWait(ATTEMPT_TICKS, 100, 1_000_000_000L);
        int[] reads = {0};
        for (int attempt = 1; attempt <= 40; attempt++) {
            wait.retry(Status.RETRY_LATER, attempt, true, 99 + attempt, 1_000_000_000L + attempt * 50_000_000L);
        }
        assertEquals(0, reads[0]);
        assertEquals("attempts=40/100 ticks=39 wall_ms=2000 retirement{pending=true attempts=1 reason=null entries=1}",
                wait.describe(() -> { reads[0]++; return "retirement{pending=true attempts=1 reason=null entries=1}"; }));
        assertEquals(1, reads[0]);
    }
}
