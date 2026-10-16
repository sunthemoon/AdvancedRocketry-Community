package io.github.sunthemoon.advancedrocketrycommunity.resourceescrow;

import static org.junit.jupiter.api.Assertions.*;

import io.github.sunthemoon.advancedrocketrycommunity.testsupport.GovernedTransferFixture;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class EscrowDispatchRetriesTest {
    @BeforeAll static void bootstrap() { MinecraftBootstrap.initialize(); }

    @Test void failedDispatchObservationsFollow1004001200AcrossNewAttempts() {
        var lines = new ArrayList<EscrowSuccessorRetry.Hold>();
        var waits = new EscrowDispatchRetries(lines::add); var calls = new AtomicInteger();
        var held = EscrowGovernedDispatch.Decision.hold("GOVERNED_LEDGER_UNAVAILABLE");
        long tick = 0;
        for (int delay : new int[] {100, 400, 1200, 1200}) {
            var record = GovernedTransferFixture.create(tick + 1, 1, false).record();
            int previous = calls.get();
            assertEquals(held, waits.poll(record, tick, () -> { calls.incrementAndGet(); return held; }));
            for (long poll = tick + 1; poll < tick + delay; poll++) {
                assertEquals(held, waits.poll(record, poll, () -> { fail("Dispatch observed during backoff"); return null; }));
            }
            assertEquals(previous + 1, calls.get()); tick += delay;
        }
        assertEquals(4, lines.size()); assertEquals(1200, lines.get(3).backoff());
        var active = new EscrowGovernedDispatch.Decision(EscrowGovernedDispatch.Route.ACTIVE, null);
        var record = GovernedTransferFixture.create().record();
        assertEquals(active, waits.poll(record, tick, () -> active));
        assertEquals(held, waits.poll(record, tick + 1, () -> held));
        assertEquals(100, lines.get(4).backoff(), "Only successful classification clears its failure history");
    }

    @Test void healthyAcknowledgementsAndLifecycleWaitsDoNotLogOrDelayProgress() {
        var waits = new EscrowDispatchRetries(hold -> fail("Healthy flight logged a successor hold"));
        var record = GovernedTransferFixture.create().record();
        long tick = 0;
        for (String reason : new String[] {"GOVERNED_JOURNAL_UNACKNOWLEDGED", "GOVERNED_LEDGER_PENDING", "GOVERNED_RUNTIME_STOPPED"}) {
            var pending = EscrowGovernedDispatch.Decision.hold(reason);
            for (int i = 0; i < 1000; i++) { assertEquals(pending, waits.poll(record, tick++, () -> pending)); }
        }
        var cleanup = new EscrowGovernedDispatch.Decision(EscrowGovernedDispatch.Route.CLEANUP, null);
        assertEquals(cleanup, waits.poll(record, tick, () -> cleanup));
    }

    @Test void repeatedHardConflictsStayHeldAndSameBackoffTransitionLogsOnlyOnce() {
        var lines = new ArrayList<EscrowSuccessorRetry.Hold>(); var waits = new EscrowDispatchRetries(lines::add);
        var record = GovernedTransferFixture.create().record();
        var conflict = EscrowGovernedDispatch.Decision.hold("GOVERNED_EXACT_TRANSFER_FLOOR");
        for (long tick : new long[] {0, 100, 500, 1700, 2900}) {
            assertEquals(conflict, waits.poll(record, tick, () -> conflict));
        }
        assertEquals(3, lines.size());
        waits.retain(record.logicalRocketId()::equals);
        assertEquals(conflict, waits.poll(record, 2901, () -> { fail("Retained history was reset"); return null; }));
        waits.clear();
        assertEquals(conflict, waits.poll(record, 2901, () -> conflict)); assertEquals(4, lines.size());
    }
}
