package io.github.sunthemoon.advancedrocketrycommunity.resourceescrow;

import static org.junit.jupiter.api.Assertions.*;

import io.github.sunthemoon.advancedrocketrycommunity.testsupport.GovernedTransferFixture;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import java.util.function.BooleanSupplier;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class EscrowGovernedWaitAdaptersTest {
    @BeforeAll static void bootstrap() { MinecraftBootstrap.initialize(); }
    private static final BooleanSupplier NO_OBSERVATION = () -> { fail("A pending/backoff wait queried capacity"); return true; };

    @Test void destinationPendingAndBackoffSkipCapacityAndFreshObservation() {
        var record = GovernedTransferFixture.create().record();
        var history = new EscrowTerminalRetries();
        var work = new EscrowGovernedDestination.Work(record, null, null, history.history(record.logicalRocketId()));
        work.pending = true;
        for (int tick = 0; tick < 2_000; tick++) { assertFalse(work.ready(tick, NO_OBSERVATION)); }
        work.pending = false;
        long tick = 2_000;
        for (int delay : new int[] {100, 400, 1_200, 1_200}) {
            work.retry(tick, "BARRIER_IO");
            assertEquals(tick + delay, work.retry.nextTick());
            assertFalse(work.ready(tick + delay - 1, NO_OBSERVATION));
            tick += delay;
            assertFalse(work.ready(tick, () -> false));
            assertTrue(work.ready(tick, () -> true));
            work.release();
            work = new EscrowGovernedDestination.Work(record, null, null, history.history(record.logicalRocketId()));
        }
    }

    @Test void returnAbsenceAndReturnedF6WaitWithoutResettingTheirHistory() {
        var record = GovernedTransferFixture.create().record();
        var work = new EscrowGovernedReturn.Work(record, null, null, false, () -> fail("Polling called completion"));
        work.pending = true;
        assertFalse(work.ready(0, NO_OBSERVATION)); work.pending = false;
        work.retry(0, "BARRIER_FORCE");
        assertFalse(work.ready(99, NO_OBSERVATION)); assertTrue(work.ready(100, () -> true));
        work.returned = true;
        work.retry(100, "SUPERSEDING_SAVE");
        assertEquals(500, work.retry.nextTick());
        assertFalse(work.ready(499, NO_OBSERVATION));
        work.retry(500, "BARRIER_IO");
        assertEquals(1_700, work.retry.nextTick());
        assertFalse(work.ready(1_700, () -> false)); assertTrue(work.ready(1_700, () -> true));
        work.terminal = true; assertFalse(work.ready(2_000, NO_OBSERVATION));
    }

    @Test void f1CapacityHintIsReadOnlyAndLifecycleClosureNeverAuthorizesCapture() {
        try (var barrier = new EscrowBarrier()) {
            for (int tick = 0; tick < 2_000; tick++) { assertTrue(barrier.available("minecraft:overworld", true, tick)); }
            barrier.quiesce();
            assertFalse(barrier.available("minecraft:overworld", true, 2_000));
        }
    }

    @Test void orphanPreF2ProofAndWriteFailuresKeepHistoryAcrossReleasedCaptures() {
        var arm = GovernedTransferFixture.create().record().nativeTransfer().orElseThrow().armed();
        var work = new EscrowPreF2Cancellation.Work();
        work.failed(arm, "F1_CANCEL_PROOF", "BARRIER_IO", 0);
        work.release();
        assertFalse(work.retry.ready(99)); assertTrue(work.retry.ready(100));
        work.failed(arm, "F1_CANCEL_WRITE", "LEDGER_WRITE_FAILED", 100);
        work.release();
        assertFalse(work.retry.ready(499)); assertTrue(work.retry.ready(500));
        work.failed(arm, "F1_CANCEL_WRITE", "LEDGER_WRITE_FAILED", 500);
        assertEquals(1_700, work.retry.nextTick()); assertEquals(3, work.retry.failures());
    }
}
