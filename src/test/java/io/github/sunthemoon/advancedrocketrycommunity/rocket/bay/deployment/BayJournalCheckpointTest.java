package io.github.sunthemoon.advancedrocketrycommunity.rocket.bay.deployment;

import static io.github.sunthemoon.advancedrocketrycommunity.rocket.bay.deployment.BayJournalCheckpoint.Action.*;
import static io.github.sunthemoon.advancedrocketrycommunity.rocket.bay.deployment.BayJournalCheckpoint.Answer.*;
import static io.github.sunthemoon.advancedrocketrycommunity.rocket.bay.deployment.BayJournalCheckpoint.decide;
import static org.junit.jupiter.api.Assertions.*;

import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * W27d1's resumption under ADR-074 item 11 (TASK W128, extra tests): D0's B14 step holds on WRITE_PENDING and runs
 * nothing, continues on PROVED after a wait, rejects every bay on UNPROVED after the request, requests again on
 * WRITE_REQUIRED, and a stop while it waits runs nothing.
 */
final class BayJournalCheckpointTest {
    @Test void writePendingHoldsAndRunsNothingWithNoTimeLimit() {
        for (int poll = 0; poll < 10_000; poll++) {
            assertEquals(WAIT, decide(WRITE_PENDING, true), "a pending capture masks every stale outcome");
        }
        assertEquals(WAIT, decide(WRITE_PENDING, false));
    }

    @Test void provedAfterAPendingWaitContinuesAndAJournalRecordOfTheRocketRejectsEveryBay() {
        assertEquals(REQUEST, decide(WRITE_REQUIRED, false));
        assertEquals(WAIT, decide(WRITE_PENDING, true));
        assertEquals(CONTINUE, decide(PROVED, true));
        assertEquals(CONTINUE, decide(PROVED, false), "an already current acknowledgement needs no write");
        assertEquals(REJECT_PENDING, decide(ASSEMBLY_JOURNAL_PENDING, false));
        assertEquals(REJECT_PENDING, decide(ASSEMBLY_JOURNAL_PENDING, true), "writing a stale record is no absence proof");
    }

    @Test void theFirstUnprovedRequestsAndUnprovedAfterTheRequestRejectsEveryBay() {
        assertEquals(REQUEST, decide(ASSEMBLY_JOURNAL_UNPROVED, false), "a stale failed outcome from before is not the answer");
        assertEquals(REJECT_UNPROVED, decide(ASSEMBLY_JOURNAL_UNPROVED, true));
    }

    @Test void writeRequiredAfterTheRequestRequestsAgain() {
        assertEquals(REQUEST, decide(WRITE_REQUIRED, true), "mutated since: request again, no count limit");
    }

    @Test void theAnswerNamesAreTheJournalsAbsenceStatusNames() {
        for (var status : io.github.sunthemoon.advancedrocketrycommunity.rocket.persistence.RocketTransactionSavedData.AbsenceStatus.values()) {
            assertNotNull(BayJournalCheckpoint.Answer.valueOf(status.name()));
        }
        assertEquals(io.github.sunthemoon.advancedrocketrycommunity.rocket.persistence.RocketTransactionSavedData.AbsenceStatus.values().length,
                BayJournalCheckpoint.Answer.values().length);
    }

    @Test void aStopWhileTheGroupWaitsRunsNothing() {
        var queue = new BayStepQueue();
        UUID waiting = new UUID(14, 1);
        queue.offer(waiting);
        assertEquals(1, queue.run(key -> BayStepQueue.Result.NEXT_TICK), "the waiting group polls once per tick");
        queue.close();
        assertTrue(queue.closed());
        assertEquals(0, queue.run(key -> { throw new AssertionError("a step ran after stopping"); }));
        queue.offer(waiting);
        assertFalse(queue.queued(waiting), "a late completion queues nothing");
    }
}
