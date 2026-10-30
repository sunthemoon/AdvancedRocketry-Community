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

    @Test void completeStateWaitsForBothReferences() {
        var observation = new EscrowLoaderS6UnloadObservation();
        assertFalse(observation.awaitComplete(44, false, false, false, false));
        assertFalse(observation.awaitComplete(45, true, true, false, true));
        assertFalse(observation.awaitComplete(46, true, true, false, true));
        assertTrue(observation.awaitComplete(47, true, true, true, true));
        assertTrue(observation.awaitComplete(100, true, true, true, true));
    }

    @Test void eitherReferenceNeverRemovedFailsAtTheFixedBound() {
        for (boolean loaderRemoved : new boolean[] {false, true}) {
            var observation = new EscrowLoaderS6UnloadObservation();
            for (long tick = 45; tick < 65; tick++) {
                assertFalse(observation.awaitComplete(tick, true, true, loaderRemoved, !loaderRemoved));
            }
            assertThrows(IllegalStateException.class,
                    () -> observation.awaitComplete(65, true, true, loaderRemoved, !loaderRemoved));
        }
    }

    @Test void losingSpatialUnloadCannotResetTheRemovalDeadline() {
        var observation = new EscrowLoaderS6UnloadObservation();
        assertFalse(observation.awaitComplete(45, true, true, false, true));
        assertFalse(observation.awaitComplete(64, false, false, false, true));
        assertThrows(IllegalStateException.class,
                () -> observation.awaitComplete(65, false, false, true, true));
    }

    @Test void lateCompleteStateOrRegainedLiveReferenceCannotPass() {
        var late = new EscrowLoaderS6UnloadObservation();
        assertFalse(late.awaitComplete(45, true, true, false, true));
        assertThrows(IllegalStateException.class, () -> late.awaitComplete(66, true, true, true, true));
        var lost = new EscrowLoaderS6UnloadObservation();
        assertTrue(lost.awaitComplete(45, true, true, true, true));
        assertThrows(IllegalStateException.class, () -> lost.awaitComplete(46, true, true, false, true));
    }
}
