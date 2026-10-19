package io.github.sunthemoon.advancedrocketrycommunity.rocket.cargo;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class RocketCargoWorkBudgetTest {
    @Test void exactLimitsAreSharedAndRefusalsChargeNothing() {
        var budget = new RocketCargoWorkBudget();
        assertTrue(budget.charge(100, 16L * 1_048_576, 16_384, true));
        assertFalse(budget.charge(100, 1, 0, false));
        assertFalse(budget.charge(100, 0, 1, false));
        assertFalse(budget.charge(100, 0, 0, true));
        assertEquals(16L * 1_048_576, budget.chargedBytes()); assertEquals(16_384, budget.chargedVisits());
        assertEquals(1, budget.reconstructions());
        assertTrue(budget.charge(101, 1, 1, true));
        assertEquals(1, budget.chargedBytes()); assertEquals(1, budget.chargedVisits());
    }
    @Test void rejectionOfAnOversizedAtomicStepNeverSpendsFutureTickCredit() {
        var budget = new RocketCargoWorkBudget();
        assertTrue(budget.charge(1, 10, 2, false));
        assertFalse(budget.charge(2, Long.MAX_VALUE, 0, false));
        assertEquals(10, budget.chargedBytes());
        assertFalse(budget.charge(2, 0, Integer.MAX_VALUE, false));
        assertEquals(2, budget.chargedVisits());
        assertTrue(budget.charge(2, 0, 0, true));
        assertThrows(IllegalArgumentException.class, () -> budget.charge(1, 0, 0, false));
        assertThrows(IllegalArgumentException.class, () -> budget.charge(2, -1, 0, false));
    }
    @Test void anotherThreadCannotSpendTheOwnerBudget() throws Exception {
        var budget = new RocketCargoWorkBudget();
        var error = new java.util.concurrent.atomic.AtomicReference<Throwable>();
        var thread = new Thread(() -> { try { budget.charge(1, 1, 1, true); } catch (Throwable thrown) { error.set(thrown); } });
        thread.start(); thread.join(5000);
        assertFalse(thread.isAlive()); assertInstanceOf(IllegalStateException.class, error.get());
        assertEquals(0, budget.chargedBytes());
    }
}
