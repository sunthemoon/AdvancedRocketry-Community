package io.github.sunthemoon.advancedrocketrycommunity.machine.electrolyzer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class ElectrolyzerRecipeSpecTest {
    @Test
    void fixedRecipeHasDocumentedValues() {
        ElectrolyzerRecipeSpec recipe = ElectrolyzerRecipeSpec.fixedRecipe();

        assertEquals(1, recipe.schemaVersion());
        assertEquals(2, recipe.inputCount());
        assertEquals(1_000, recipe.waterAmount());
        assertEquals(100, recipe.processingTicks());
        assertEquals(20, recipe.energyPerTick());
        assertEquals(2_000, recipe.totalEnergy());
        assertEquals(1, recipe.hydrogenOutputCount());
        assertEquals(1, recipe.oxygenOutputCount());
    }

    @Test
    void allInclusiveBoundariesAreAccepted() {
        assertEquals(
                ElectrolyzerRecipeSpec.MAX_ENERGY_PER_RECIPE,
                new ElectrolyzerRecipeSpec(1, 64, 4_000, 20, 1_000, 64, 64).totalEnergy()
        );
    }

    @Test
    void invalidSchemaAndEveryZeroValueAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> recipe(2, 1, 1, 1, 1, 1, 1));
        assertThrows(IllegalArgumentException.class, () -> recipe(1, 0, 1, 1, 1, 1, 1));
        assertThrows(IllegalArgumentException.class, () -> recipe(1, 1, 0, 1, 1, 1, 1));
        assertThrows(IllegalArgumentException.class, () -> recipe(1, 1, 1, 0, 1, 1, 1));
        assertThrows(IllegalArgumentException.class, () -> recipe(1, 1, 1, 1, 0, 1, 1));
        assertThrows(IllegalArgumentException.class, () -> recipe(1, 1, 1, 1, 1, 0, 1));
        assertThrows(IllegalArgumentException.class, () -> recipe(1, 1, 1, 1, 1, 1, 0));
    }

    @Test
    void valuesAboveEveryHardLimitAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> recipe(1, 65, 1, 1, 1, 1, 1));
        assertThrows(IllegalArgumentException.class, () -> recipe(1, 1, 4_001, 1, 1, 1, 1));
        assertThrows(IllegalArgumentException.class, () -> recipe(1, 1, 1, 1_201, 1, 1, 1));
        assertThrows(IllegalArgumentException.class, () -> recipe(1, 1, 1, 1, 1_001, 1, 1));
        assertThrows(IllegalArgumentException.class, () -> recipe(1, 1, 1, 1, 1, 65, 1));
        assertThrows(IllegalArgumentException.class, () -> recipe(1, 1, 1, 1, 1, 1, 65));
    }

    @Test
    void totalEnergyCannotExceedMachineBuffer() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new ElectrolyzerRecipeSpec(1, 1, 1, 21, 1_000, 1, 1)
        );
    }

    private static ElectrolyzerRecipeSpec recipe(
            int schema,
            int input,
            int water,
            int ticks,
            int energy,
            int hydrogen,
            int oxygen
    ) {
        return new ElectrolyzerRecipeSpec(schema, input, water, ticks, energy, hydrogen, oxygen);
    }
}
