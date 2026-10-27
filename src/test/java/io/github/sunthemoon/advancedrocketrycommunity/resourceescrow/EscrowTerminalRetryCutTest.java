package io.github.sunthemoon.advancedrocketrycommunity.resourceescrow;

import static org.junit.jupiter.api.Assertions.*;

import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import java.util.ArrayList;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class EscrowTerminalRetryCutTest {
    private static final UUID ID = new UUID(175, 1);

    @BeforeAll static void bootstrap() { MinecraftBootstrap.initialize(); }

    @Test void delayedObservationUsesCallbackTickAndPreservesLogging() {
        var lines = new ArrayList<EscrowSuccessorRetry.Hold>();
        var history = new EscrowSuccessorRetry(lines::add);
        var clock = new AtomicLong(152);
        var reads = new AtomicLong();
        var failure = new EscrowTerminalRetryCut.WriteFailure(history, () -> { reads.incrementAndGet(); return clock.get(); });
        try (failure) {
            assertFalse(failure.matches(history));
            fail(history, clock.get(), "F7_WRITE", "LEDGER_WRITE_FAILED");
            assertTrue(failure.matches(history));
            assertEquals(252, history.nextTick());
            clock.incrementAndGet();
            assertTrue(failure.matches(history));
            assertNotEquals(clock.get() + 100, history.nextTick());
            assertEquals(1, lines.size());
            assertFalse(failure.matches(new EscrowSuccessorRetry(lines::add)));
        }
        failure.close();
        // Closing restores the original consumer; subsequent failures never read the fixture clock.
        fail(history, 300, "F7_WRITE", "LEDGER_WRITE_FAILED");
        assertEquals(1, reads.get());
        assertFalse(failure.matches(history));
        assertEquals(2, lines.size());
    }

    @Test void rejectsDeadlineBasedOnObservationInsteadOfCallbackTick() {
        var history = new EscrowSuccessorRetry(hold -> { });
        try (var failure = new EscrowTerminalRetryCut.WriteFailure(history, () -> 152)) {
            fail(history, 153, "F7_WRITE", "LEDGER_WRITE_FAILED");
            assertFalse(failure.matches(history));
        }
    }

    @Test void rejectsProofFailureAndQueueRefusalEvenWithTheSameDeadline() {
        for (String[] cause : new String[][] {{"F6_PROOF", "SUPERSEDING_SAVE"}, {"F7_WRITE", "LEDGER_QUEUE_WAIT"}}) {
            var history = new EscrowSuccessorRetry(hold -> { });
            try (var failure = new EscrowTerminalRetryCut.WriteFailure(history, () -> 152)) {
                fail(history, 152, cause[0], cause[1]);
                assertEquals(252, history.nextTick());
                assertFalse(failure.matches(history));
            }
        }
    }

    private static void fail(EscrowSuccessorRetry history, long tick, String phase, String reason) {
        history.failed(ID, ID, ID, 1, phase, reason, tick);
    }
}
