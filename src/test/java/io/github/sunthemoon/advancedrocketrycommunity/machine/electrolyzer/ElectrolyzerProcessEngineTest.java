package io.github.sunthemoon.advancedrocketrycommunity.machine.electrolyzer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ElectrolyzerProcessEngineTest {
    @Test
    void validTickConsumesExactEnergyAndAdvances() {
        ElectrolyzerTickResult result = tick(41, true, true, true, true, 20);

        assertEquals(42, result.progress());
        assertEquals(20, result.energyConsumed());
        assertFalse(result.completed());
        assertEquals(ElectrolyzerStatus.RUNNING, result.status());
    }

    @Test
    void completionResetsProgressExactlyOnce() {
        ElectrolyzerTickResult result = tick(99, true, true, true, true, 20);

        assertEquals(0, result.progress());
        assertEquals(20, result.energyConsumed());
        assertTrue(result.completed());
    }

    @Test
    void everyPauseReasonPreservesProgressAndConsumesNothing() {
        assertPaused(tick(40, true, false, true, true, 20), ElectrolyzerStatus.REDSTONE_DISABLED);
        assertPaused(tick(40, true, true, false, true, 20), ElectrolyzerStatus.NEEDS_WATER);
        assertPaused(tick(40, true, true, true, false, 20), ElectrolyzerStatus.OUTPUT_BLOCKED);
        assertPaused(tick(40, true, true, true, true, 19), ElectrolyzerStatus.NEEDS_ENERGY);
    }

    @Test
    void missingActiveRecipeResetsProgressWithoutConsumption() {
        ElectrolyzerTickResult result = tick(40, false, true, true, true, 20);

        assertEquals(0, result.progress());
        assertEquals(0, result.energyConsumed());
        assertEquals(ElectrolyzerStatus.INVALID_RECIPE, result.status());
    }

    @Test
    void invalidProgressIsRejected() {
        ElectrolyzerTickInput input = new ElectrolyzerTickInput(true, true, true, true, 20, 20);
        assertThrows(IllegalArgumentException.class, () -> ElectrolyzerProcessEngine.tick(-1, 100, input));
        assertThrows(IllegalArgumentException.class, () -> ElectrolyzerProcessEngine.tick(100, 100, input));
    }

    private static ElectrolyzerTickResult tick(
            int progress,
            boolean recipe,
            boolean enabled,
            boolean water,
            boolean output,
            int storedEnergy
    ) {
        return ElectrolyzerProcessEngine.tick(
                progress,
                100,
                new ElectrolyzerTickInput(recipe, enabled, water, output, storedEnergy, 20)
        );
    }

    private static void assertPaused(ElectrolyzerTickResult result, ElectrolyzerStatus status) {
        assertEquals(40, result.progress());
        assertEquals(0, result.energyConsumed());
        assertFalse(result.completed());
        assertEquals(status, result.status());
    }
}
