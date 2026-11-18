package io.github.sunthemoon.advancedrocketrycommunity.gametest;

import static org.junit.jupiter.api.Assertions.*;

import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketTransferRecoveryReport.Status;
import org.junit.jupiter.api.Test;

/** W381: the recovery fixture's waits keep their bounds; the observation is read only for a failure's text. */
class RocketTransferRecoveryWaitTest {
    @Test void theBoundsAreTheFixturesOriginalOnes() {
        assertEquals(20, RocketTransferRecoveryWait.SOURCE_ONLY_ATTEMPTS);
        assertEquals(40, RocketTransferRecoveryWait.BOTH_ATTEMPTS);
    }

    @Test void aWaitRetriesOnlyWhileRecoveryAsksAndTheBoundAllows() {
        var wait = new RocketTransferRecoveryWait(100, 0);
        for (int attempt = 1; attempt < 40; attempt++) {
            assertTrue(wait.retry(Status.RETRY_LATER, attempt, 40, 100 + attempt, 0), "attempt " + attempt);
        }
        assertFalse(wait.retry(Status.RETRY_LATER, 40, 40, 140, 0));
        assertFalse(wait.retry(Status.RETRY_LATER, 41, 40, 141, 0));
        for (Status status : Status.values()) {
            if (status != Status.RETRY_LATER) { assertFalse(wait.retry(status, 1, 40, 101, 0), status.name()); }
        }
    }

    @Test void theFailureTextCarriesTheWaitAndReadsTheObservationOnce() {
        var wait = new RocketTransferRecoveryWait(100, 1_000_000_000L);
        int[] reads = {0};
        for (int attempt = 1; attempt <= 40; attempt++) {
            wait.retry(Status.RETRY_LATER, attempt, 40, 99 + attempt, 1_000_000_000L + attempt * 50_000_000L);
        }
        assertEquals(0, reads[0]);
        assertEquals("attempts=40/40 ticks=39 wall_ms=2000 retirement{pending=true attempts=1 reason=null entries=1}",
                wait.describe(40, () -> { reads[0]++; return "retirement{pending=true attempts=1 reason=null entries=1}"; }));
        assertEquals(1, reads[0]);
    }
}
