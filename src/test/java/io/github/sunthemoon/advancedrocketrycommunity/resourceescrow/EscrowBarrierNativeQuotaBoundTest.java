package io.github.sunthemoon.advancedrocketrycommunity.resourceescrow;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/** R112's availability bound observes the real Level window, not a replacement test quota. */
class EscrowBarrierNativeQuotaBoundTest {
    @Test void twentyCapturesFinishAt84ButTheNextNativeOpportunityIs100() {
        var budget = new EscrowBarrierBudget();
        var captures = new ArrayList<Long>();
        for (long tick = 0; tick < 100; tick++) {
            if (captures.size() < 20 && budget.reserveOrdinary("minecraft:overworld", true, tick)) {
                captures.add(tick);
                budget.captured(0);
                budget.releaseOrdinary(0);
            }
        }
        assertEquals(List.of(0L, 1L, 2L, 3L, 20L, 21L, 22L, 23L, 40L, 41L,
                42L, 43L, 60L, 61L, 62L, 63L, 80L, 81L, 82L, 83L), captures);
        long completed = captures.get(captures.size() - 1) + 1;
        assertEquals(84, completed);
        for (long tick = completed; tick < 100; tick++) {
            assertFalse(budget.availableOrdinary("minecraft:overworld", true, tick), "Premature capture at " + tick);
        }
        assertTrue(budget.availableOrdinary("minecraft:overworld", true, 100));
        assertTrue(budget.reserveOrdinary("minecraft:overworld", true, 100));
        budget.captured(0); budget.releaseOrdinary(0);
        assertEquals(0, budget.pending());
        assertEquals(0, budget.ordinaryPending());
        assertEquals(0, budget.bytes());
    }
}
