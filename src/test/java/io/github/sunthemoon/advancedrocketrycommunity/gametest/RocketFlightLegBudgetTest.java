package io.github.sunthemoon.advancedrocketrycommunity.gametest;

import static org.junit.jupiter.api.Assertions.*;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class RocketFlightLegBudgetTest {
    private final UUID requested = UUID.randomUUID();

    @Test
    void predecessorAndUnrelatedTransfersNeverStartTheLandingClock() {
        var budget = new RocketFlightLegBudget(requested, 20, 1000, 270);
        budget.observe(21, UUID.randomUUID());
        budget.observe(400, null);
        assertFalse(budget.prepared());
        assertFalse(budget.expired(1019));
        assertTrue(budget.expired(1020));
    }

    @Test
    void slowRetirementLeavesAll270LandingTicks() {
        var budget = new RocketFlightLegBudget(requested, 20, 1000, 270);
        budget.observe(280, requested);
        assertTrue(budget.prepared());
        assertFalse(budget.expired(549));
        assertTrue(budget.expired(550));
    }

    @Test
    void repeatedObservationsDoNotExtendTheLandingDeadline() {
        var budget = new RocketFlightLegBudget(requested, 20, 1000, 270);
        budget.observe(30, requested);
        budget.observe(299, requested);
        assertFalse(budget.expired(299));
        assertTrue(budget.expired(300));
    }

    @Test
    void preparationAtItsDeadlineStillGetsExactly270Ticks() {
        var budget = new RocketFlightLegBudget(requested, 20, 1000, 270);
        budget.observe(1020, requested);
        assertTrue(budget.prepared());
        assertFalse(budget.expired(1289));
        assertTrue(budget.expired(1290));
        assertEquals(1290, budget.latestDeadline());
    }

    @Test
    void preparationAfterItsDeadlineCannotReviveAnExpiredWait() {
        var budget = new RocketFlightLegBudget(requested, 20, 1000, 270);
        budget.observe(1021, requested);
        assertFalse(budget.prepared());
        assertTrue(budget.expired(1021));
    }

    @Test
    void immediatePreparationKeepsTheOriginalLandingBudget() {
        var budget = new RocketFlightLegBudget(requested, 20, 1000, 270);
        budget.observe(20, requested);
        assertFalse(budget.expired(289));
        assertTrue(budget.expired(290));
    }

    @Test
    void nonpositiveBudgetsAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> new RocketFlightLegBudget(requested, 0, 0, 270));
        assertThrows(IllegalArgumentException.class, () -> new RocketFlightLegBudget(requested, 0, 1000, 0));
    }
}
