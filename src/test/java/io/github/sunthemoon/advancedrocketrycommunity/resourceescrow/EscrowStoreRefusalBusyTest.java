package io.github.sunthemoon.advancedrocketrycommunity.resourceescrow;

import static org.junit.jupiter.api.Assertions.*;

import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** Final review A, I4: a refused chunk store (BARRIER_BOUNDS) cannot leave O2 or C4 busy until restart. */
class EscrowStoreRefusalBusyTest {
    @BeforeAll static void bootstrap() { MinecraftBootstrap.initialize(); }

    private static String refusedStore() {
        EscrowReconciliationView.requireStoreCapacity(false, false, 64); return "store";
    }

    @Test void settlementO2StoreRefusalLeavesNoSlotBusyAndTheWaitRetryable() {
        var op = EscrowTestFixtures.operation(1); var keys = List.of(op.source().key(), op.destination().key());
        var state = new EscrowSettlementRecovery.Work();
        var refusal = assertThrows(EscrowReconciliationView.Refusal.class, () -> EscrowSettlementRecovery.begin(state.busy, keys,
                EscrowStoreRefusalBusyTest::refusedStore, store -> fail("No joint capture without a store")));
        assertEquals(EscrowReason.BARRIER_BOUNDS, refusal.reason); assertTrue(state.busy.isEmpty());
        // A leaked busy slot would report "attempt pending" forever; the stalled wait stays retryable instead.
        state.lastReason = "BARRIER_IO"; state.retryAt = 500;
        assertEquals(EscrowOperatorRetry.Outcome.ACCEPTED, state.accelerate(10));
        // Busy covers the joint capture itself, and a refused capture releases it.
        assertFalse(EscrowSettlementRecovery.begin(state.busy, keys, () -> "store", store -> {
            assertEquals(Set.copyOf(keys), state.busy); return false;
        }));
        assertTrue(state.busy.isEmpty());
        assertTrue(EscrowSettlementRecovery.begin(state.busy, keys, () -> "store", store -> true));
        assertEquals(Set.copyOf(keys), state.busy);
    }

    @Test void reconciliationC4StoreRefusalLeavesTheCheckpointIdle() {
        var busy = new AtomicBoolean();
        var refusal = assertThrows(EscrowReconciliationView.Refusal.class, () -> EscrowReconciliationDriver.capture(busy::set,
                EscrowStoreRefusalBusyTest::refusedStore, store -> fail("No joint capture without a store")));
        assertEquals(EscrowReason.BARRIER_BOUNDS, refusal.reason); assertFalse(busy.get());
        assertFalse(EscrowReconciliationDriver.capture(busy::set, () -> "store", store -> { assertTrue(busy.get()); return false; }));
        assertFalse(busy.get());
        assertTrue(EscrowReconciliationDriver.capture(busy::set, () -> "store", store -> true)); assertTrue(busy.get());
    }
}
