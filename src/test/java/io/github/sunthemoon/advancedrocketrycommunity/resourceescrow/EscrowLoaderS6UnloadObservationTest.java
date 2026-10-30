package io.github.sunthemoon.advancedrocketrycommunity.resourceescrow;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class EscrowLoaderS6UnloadObservationTest {
    @Test void recordsEachFirstObservationWithoutConflatingTheConditions() {
        var observation = new EscrowLoaderS6UnloadObservation();
        observation.observe(10, false, false, false, false);
        assertEquals(-1, observation.chunkAbsentAt());
        observation.observe(11, false, true, false, true);
        observation.observe(12, true, true, false, true);
        observation.observe(15, true, true, true, true);
        observation.observe(16, false, false, false, false);
        assertEquals(12, observation.chunkAbsentAt());
        assertEquals(11, observation.entitiesUnloadedAt());
        assertEquals(15, observation.loaderRemovedAt());
        assertEquals(11, observation.rocketRemovedAt());
    }

    @Test void neverRemovedReferenceStaysExplicitAtTheDiagnosticCeiling() {
        var observation = new EscrowLoaderS6UnloadObservation();
        for (long tick = 1; tick <= 31; tick++) {
            observation.observe(tick, tick >= 10, tick >= 11, false, tick >= 11);
            assertEquals(-1, observation.loaderRemovedAt());
            assertEquals(tick == 31, observation.windowComplete(tick));
        }
        assertEquals(11, observation.rocketRemovedAt());
    }

    @Test void noUnloadedPairMeansNoDiagnosticCompletion() {
        var observation = new EscrowLoaderS6UnloadObservation();
        observation.observe(1, false, true, true, true);
        assertFalse(observation.windowComplete(2_400));
        observation.observe(2_400, true, true, true, true);
        assertFalse(observation.windowComplete(2_419));
        assertTrue(observation.windowComplete(2_420));
    }
}
