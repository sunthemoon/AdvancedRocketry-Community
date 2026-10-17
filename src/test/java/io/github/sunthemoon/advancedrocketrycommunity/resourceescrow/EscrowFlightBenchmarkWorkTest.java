package io.github.sunthemoon.advancedrocketrycommunity.resourceescrow;

import static org.junit.jupiter.api.Assertions.*;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class EscrowFlightBenchmarkWorkTest {
    @BeforeAll static void bootstrap() { MinecraftBootstrap.initialize(); }

    @Test void releaseWaitsForBothProductPruningSteps() {
        assertFalse(EscrowFlightBenchmarkWork.settlementPruned(false, 1000, 4000, 1, 1));
        assertFalse(EscrowFlightBenchmarkWork.settlementPruned(false, 1000, 4000, 0, 1));
        assertFalse(EscrowFlightBenchmarkWork.settlementPruned(false, 1000, 4000, 1, 0));
        assertTrue(EscrowFlightBenchmarkWork.settlementPruned(false, 1000, 4000, 0, 0));
    }

    @Test void pruningWaitCannotConcealChangedResourcesOrMissingRetirement() {
        for (int markers : new int[] {0, 1}) {
            assertThrows(IllegalArgumentException.class,
                    () -> EscrowFlightBenchmarkWork.settlementPruned(true, 1000, 4000, markers, markers));
            assertThrows(IllegalArgumentException.class,
                    () -> EscrowFlightBenchmarkWork.settlementPruned(false, 999, 4000, markers, markers));
            assertThrows(IllegalArgumentException.class,
                    () -> EscrowFlightBenchmarkWork.settlementPruned(false, 1001, 4000, markers, markers));
            assertThrows(IllegalArgumentException.class,
                    () -> EscrowFlightBenchmarkWork.settlementPruned(false, 1000, 3999, markers, markers));
            assertThrows(IllegalArgumentException.class,
                    () -> EscrowFlightBenchmarkWork.settlementPruned(false, 1000, 4001, markers, markers));
        }
    }
}
