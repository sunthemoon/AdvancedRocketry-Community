package io.github.sunthemoon.advancedrocketrycommunity.celestial.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

final class GravityFlightBudgetTest {
    @Test void allowancesAreProportionalAndAlwaysBounded() {
        assertEquals(485, GravityFlightBudget.allowance(0.165D));
        assertEquals(160, GravityFlightBudget.allowance(0.5D));
        for (double factor : new double[]{0, 0.1D, 0.001D, Double.MIN_VALUE}) {
            assertEquals(800, GravityFlightBudget.allowance(factor));
        }
        for (double factor : new double[]{1, 2, 4, -1, Double.NaN, Double.POSITIVE_INFINITY}) {
            assertEquals(80, GravityFlightBudget.allowance(factor));
        }
    }

    @Test void vanillaDisconnectsOnTheTickAfterEachAllowance() {
        for (double factor : new double[]{0, 0.001D, 0.1D, 0.165D, 0.5D, 0.99D, 1, 2}) {
            GravityFlightBudget budget = new GravityFlightBudget();
            int counter = 0;
            int tick = 0;
            while (counter <= 80 && tick <= 800) {
                int adjusted = budget.adjust(counter, factor);
                assertTrue(adjusted >= counter - 1 && adjusted <= counter);
                if (counter > 0) { assertTrue(adjusted > 0, "A gravity tick must never reset the counter"); }
                counter = adjusted + 1; // The unchanged vanilla ++counter > 80 check.
                tick++;
            }
            assertEquals(GravityFlightBudget.allowance(factor) + 1, tick, "factor " + factor);
            assertEquals(81, counter);
        }
    }

    @Test void aNativeResetRestoresTheBudgetButAChangedFactorDoesNot() {
        GravityFlightBudget budget = new GravityFlightBudget();
        for (int i = 0; i < 80; i++) { assertEquals(79, budget.adjust(80, 0.5D)); }
        assertEquals(80, budget.adjust(80, 0.5D));
        assertEquals(80, budget.adjust(80, 1.0D));
        assertEquals(79, budget.adjust(80, 0.165D));
        for (int i = 81; i < 405; i++) { assertEquals(79, budget.adjust(80, 0.165D)); }
        assertEquals(80, budget.adjust(80, 0.165D));
        assertEquals(0, budget.adjust(0, 0.165D));
        assertEquals(79, budget.adjust(80, 0.165D));
    }
}
