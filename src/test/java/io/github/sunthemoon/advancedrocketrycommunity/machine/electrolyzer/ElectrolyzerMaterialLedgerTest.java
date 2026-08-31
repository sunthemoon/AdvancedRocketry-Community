package io.github.sunthemoon.advancedrocketrycommunity.machine.electrolyzer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class ElectrolyzerMaterialLedgerTest {
    @Test
    void fiftyCyclesConserveCanistersAndConsumeExactWaterAndEnergy() {
        ElectrolyzerRecipeSpec recipe = ElectrolyzerRecipeSpec.fixedRecipe();
        ElectrolyzerMaterialLedger ledger = new ElectrolyzerMaterialLedger(
                100,
                50_000,
                100_000,
                0,
                0
        );
        long initialCanisters = ledger.totalCanisters();

        for (int cycle = 0; cycle < 50; cycle++) {
            ledger = ledger.process(recipe);
        }

        assertEquals(0, ledger.emptyCanisters());
        assertEquals(0, ledger.waterAmount());
        assertEquals(0, ledger.storedEnergy());
        assertEquals(50, ledger.hydrogenCanisters());
        assertEquals(50, ledger.oxygenCanisters());
        assertEquals(initialCanisters, ledger.totalCanisters());
    }

    @Test
    void insufficientResourceCannotPartiallyCommit() {
        ElectrolyzerRecipeSpec recipe = ElectrolyzerRecipeSpec.fixedRecipe();

        assertThrows(
                IllegalStateException.class,
                () -> new ElectrolyzerMaterialLedger(1, 1_000, 2_000, 0, 0).process(recipe)
        );
        assertThrows(
                IllegalStateException.class,
                () -> new ElectrolyzerMaterialLedger(2, 999, 2_000, 0, 0).process(recipe)
        );
        assertThrows(
                IllegalStateException.class,
                () -> new ElectrolyzerMaterialLedger(2, 1_000, 1_999, 0, 0).process(recipe)
        );
    }
}
