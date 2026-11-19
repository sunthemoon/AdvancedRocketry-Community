package io.github.sunthemoon.advancedrocketrycommunity.resourceescrow;

import static org.junit.jupiter.api.Assertions.*;

import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class EscrowFlightBenchmarkArrivalCostTest {
    @BeforeAll static void bootstrap() { MinecraftBootstrap.initialize(); }

    @Test void attributionRequiresItsExplicitScenarioAndIsOffForNormalAndRecoveryRuns() {
        for (var scenario : new String[] {null, "", "start", "recover", "arrival-second-call-cost-extra"}) {
            assertFalse(EscrowFlightBenchmarkArrivalCost.selected(scenario));
            assertEquals(scenario, EscrowFlightBenchmarkArrivalCost.lifecycleScenario(scenario));
        }
        assertTrue(EscrowFlightBenchmarkArrivalCost.selected("arrival-second-call-cost"));
        assertEquals("start", EscrowFlightBenchmarkArrivalCost.lifecycleScenario("arrival-second-call-cost"));
    }

    @Test void changedStateOrHoldFailsInsteadOfBecomingATimingSample() {
        for (var result : EscrowGovernedArrival.Result.values()) {
            assertThrows(IllegalArgumentException.class, () -> EscrowFlightBenchmarkArrivalCost.requireUnchanged(false, result));
        }
        assertThrows(IllegalArgumentException.class, () -> EscrowFlightBenchmarkArrivalCost.requireUnchanged(true, EscrowGovernedArrival.Result.HELD));
        assertThrows(IllegalArgumentException.class, () -> EscrowFlightBenchmarkArrivalCost.requireUnchanged(true, EscrowGovernedArrival.Result.LANDED));
        EscrowFlightBenchmarkArrivalCost.requireUnchanged(true, EscrowGovernedArrival.Result.DESCENDING);
        EscrowFlightBenchmarkArrivalCost.requireUnchanged(true, EscrowGovernedArrival.Result.READY_TO_LAND);
    }

    @Test void dispatcherResultIsInferredOnlyForItsVisitedClockAndAtTheExactDescentBoundary() {
        assertNull(EscrowFlightBenchmarkArrivalCost.expectedResult(false, 0, 100));
        assertEquals(EscrowGovernedArrival.Result.DESCENDING, EscrowFlightBenchmarkArrivalCost.expectedResult(true, 100, 100));
        assertEquals(EscrowGovernedArrival.Result.DESCENDING, EscrowFlightBenchmarkArrivalCost.expectedResult(true, 179, 100));
        assertEquals(EscrowGovernedArrival.Result.READY_TO_LAND, EscrowFlightBenchmarkArrivalCost.expectedResult(true, 180, 100));
        assertEquals(EscrowGovernedArrival.Result.READY_TO_LAND, EscrowFlightBenchmarkArrivalCost.expectedResult(true, 181, 100));
        assertThrows(IllegalArgumentException.class, () -> EscrowFlightBenchmarkArrivalCost.expectedResult(true, 99, 100));
    }
}
