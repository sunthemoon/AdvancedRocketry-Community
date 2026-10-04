package io.github.sunthemoon.advancedrocketrycommunity.machine.pump;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class PumpBudgetTest {
    @Test void acceptedSourceCostsExactly100AndWaitsFiveTicks() {
        var plan = PumpBudget.drain(new PumpBudget.State(10_000, 0, 0), true);
        assertEquals(PumpCode.DRAINED, plan.code());
        assertEquals(new PumpBudget.State(9_900, 1_000, 5), plan.state());
        var state = plan.state();
        for (int tick = 1; tick < 5; tick++) {
            state = state.tick();
            assertEquals(PumpCode.COOLDOWN, PumpBudget.drain(state, true).code());
        }
        state = state.tick();
        assertEquals(new PumpBudget.State(9_800, 2_000, 5), PumpBudget.drain(state, true).state());
    }
    @Test void everyResourceRefusalRetainsExactSnapshot() {
        for (var state : new PumpBudget.State[]{new PumpBudget.State(99, 0, 0),
                new PumpBudget.State(100, 15_001, 0), new PumpBudget.State(100, 0, 5)}) {
            assertEquals(state, PumpBudget.drain(state, true).state());
        }
        var state = new PumpBudget.State(100, 15_000, 0);
        assertEquals(PumpCode.TANK_INCOMPATIBLE, PumpBudget.drain(state, false).code());
        assertEquals(state, PumpBudget.drain(state, false).state());
        assertEquals(new PumpBudget.State(0, 16_000, 5), PumpBudget.drain(state, true).state());
    }
    @Test void signedAndOverLimitStateCannotEnterPlanner() {
        for (int bad : new int[]{-1, Integer.MIN_VALUE, Integer.MAX_VALUE, 10_001}) {
            assertThrows(IllegalArgumentException.class, () -> new PumpBudget.State(bad, 0, 0));
        }
        assertThrows(IllegalArgumentException.class, () -> new PumpBudget.State(0, 16_001, 0));
        assertThrows(IllegalArgumentException.class, () -> new PumpBudget.State(0, 0, 6));
    }
}
