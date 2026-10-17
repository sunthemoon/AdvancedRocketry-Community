package io.github.sunthemoon.advancedrocketrycommunity.resourceescrow;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class EscrowSuccessorRetryTest {
    @Test void destinationReturnAndCleanupWaitsShareTheScheduleAndTransitionLog() {
        for (String phase : java.util.List.of("F4_PROOF", "RETURN_ABSENCE_PROOF", "RETURN_F6_PROOF", "F8_PRUNE")) {
            var lines = new ArrayList<EscrowSuccessorRetry.Hold>();
            var retry = new EscrowSuccessorRetry(lines::add);
            var ledger = UUID.randomUUID(); var logical = UUID.randomUUID(); var transfer = UUID.randomUUID();
            String reason = phase.equals("F8_PRUNE") ? "LEDGER_WRITE_FAILED" : "BARRIER_IO";
            long now = 20;
            for (int delay : new int[] {100, 400, 1_200, 1_200}) {
                retry.failed(ledger, logical, transfer, 7, phase, reason, now);
                assertEquals(now + delay, retry.nextTick(), phase);
                for (long tick = now; tick < now + delay; tick++) { assertFalse(retry.ready(tick), phase); }
                now += delay; assertTrue(retry.ready(now), phase);
            }
            assertEquals(3, lines.size(), phase + " logged a repeated saturated wait");
            assertTrue(lines.stream().allMatch(line -> line.phase().equals(phase) && line.serial() == 7));
            retry.failed(ledger, logical, transfer, 7, phase, "SUPERSEDING_SAVE", now);
            assertEquals(4, lines.size(), "A changed wait reason needs its own transition");
        }
    }

    @Test void proofAndDefiniteWriteFailuresKeepTheExact1004001200Schedule() {
        var lines = new ArrayList<EscrowSuccessorRetry.Hold>();
        var retry = new EscrowSuccessorRetry(lines::add);
        var ledger = UUID.randomUUID(); var logical = UUID.randomUUID(); var transfer = UUID.randomUUID();
        assertTrue(retry.ready(0));
        long tick = 0;
        for (int delay : new int[] {100, 400, 1200, 1200}) {
            retry.failed(ledger, logical, transfer, 0, "F1_PROOF", "BARRIER_IO", tick);
            assertEquals(tick + delay, retry.nextTick());
            for (long now = tick; now < tick + delay; now++) { assertFalse(retry.ready(now)); }
            tick += delay; assertTrue(retry.ready(tick));
        }
        assertEquals(3, retry.failures()); assertEquals(3, lines.size());
        retry.failed(ledger, logical, UUID.randomUUID(), 12, "F1_WRITE", "LEDGER_WRITE_FAILED", tick);
        assertEquals(tick + 1200, retry.nextTick(), "A different proposed transfer must not reset logical-source history");
        assertEquals(4, lines.size()); assertEquals(12, lines.get(3).serial());
        assertEquals("F1_WRITE", lines.get(3).phase());
    }

    @Test void loggingTracksTheCompleteTransitionWithoutALifetimeCapOrPollingLines() {
        var lines = new ArrayList<EscrowSuccessorRetry.Hold>();
        var retry = new EscrowSuccessorRetry(lines::add); var ledger = UUID.randomUUID();
        var logical = UUID.randomUUID(); var transfer = UUID.randomUUID();
        long tick = 0;
        for (int attempt = 0; attempt < 3; attempt++) {
            retry.failed(ledger, logical, transfer, 0, "F1_PROOF", "BARRIER_FORCE", tick); tick = retry.nextTick();
        }
        for (int poll = 0; poll < 10_000; poll++) { retry.ready(tick); }
        assertEquals(3, lines.size());
        retry.failed(ledger, logical, transfer, 0, "F1_PROOF", "BARRIER_FORCE", tick); tick = retry.nextTick();
        assertEquals(3, lines.size());
        for (int change = 0; change < 100; change++) {
            String reason = change % 2 == 0 ? "SUPERSEDING_SAVE" : "BARRIER_FORCE";
            retry.failed(ledger, logical, transfer, 0, "F1_PROOF", reason, tick); tick = retry.nextTick();
        }
        assertEquals(103, lines.size());
        retry.failed(UUID.randomUUID(), logical, transfer, 0, "F1_PROOF", "BARRIER_FORCE", tick); tick = retry.nextTick();
        retry.failed(ledger, logical, UUID.randomUUID(), 0, "F1_PROOF", "BARRIER_FORCE", tick); tick = retry.nextTick();
        retry.failed(ledger, logical, transfer, 1, "F1_PROOF", "BARRIER_FORCE", tick); tick = retry.nextTick();
        retry.failed(ledger, logical, transfer, 1, "F1_WRITE", "BARRIER_FORCE", tick); tick = retry.nextTick();
        assertEquals(107, lines.size());
        retry.failed(ledger, UUID.randomUUID(), transfer, 1, "F1_WRITE", "BARRIER_FORCE", tick);
        assertEquals(108, lines.size(), "Logical identity belongs to the logged transition");
    }

    @Test void independentSourcesHaveIndependentFailureHistories() {
        var first = new EscrowSuccessorRetry(hold -> { }); var second = new EscrowSuccessorRetry(hold -> fail());
        first.failed(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), 0, "F1_PROOF", "BARRIER_IO", 20);
        assertFalse(first.ready(119)); assertTrue(second.ready(20)); assertEquals(0, second.failures());
    }
}
