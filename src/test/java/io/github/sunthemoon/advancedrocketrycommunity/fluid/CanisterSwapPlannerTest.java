package io.github.sunthemoon.advancedrocketrycommunity.fluid;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import org.junit.jupiter.api.Test;

class CanisterSwapPlannerTest {
    @Test
    void aSingleUnitReplacesTheHeldItemWithoutInventorySpace() {
        var decision = CanisterSwapPlanner.plan(1, 0);
        assertTrue(decision.accepted());
        assertTrue(decision.replaceHeld());
        assertEquals(1, decision.heldCountAfter());
        assertEquals(0, decision.stowCount());
    }

    @Test
    void stackedUnitsNeedSpaceAndConserveAllSixteenContainers() {
        for (int count = 2; count <= 16; count++) {
            var blocked = CanisterSwapPlanner.plan(count, 0);
            assertFalse(blocked.accepted());
            assertEquals(count, blocked.heldCountAfter());
            var accepted = CanisterSwapPlanner.plan(count, 1);
            assertTrue(accepted.accepted());
            assertFalse(accepted.replaceHeld());
            assertEquals(count, accepted.heldCountAfter() + accepted.stowCount());
        }
    }

    @Test
    void impossibleCountsAndNegativeCapacityAreRejected() {
        for (int count : new int[] {Integer.MIN_VALUE, -1, 0, 17, 64, Integer.MAX_VALUE}) {
            assertFalse(CanisterSwapPlanner.plan(count, 64).accepted());
        }
        assertFalse(CanisterSwapPlanner.plan(1, -1).accepted());
    }

    @Test
    void fluidIdsAndPhysicalFactsAreStableAndUnique() {
        var ids = new HashSet<String>();
        int gases = 0;
        for (var definition : ClassicFluidDefinition.values()) {
            assertTrue(ids.add(definition.id()));
            assertTrue(definition.temperature() > 0);
            assertTrue(definition.viscosity() > 0);
            assertTrue(definition.light() >= 0 && definition.light() <= 15);
            if (definition.gas()) {
                gases++;
                assertEquals(-1_000, definition.density());
                assertEquals(1_000, definition.viscosity());
                assertEquals(300, definition.temperature());
                assertEquals(0, definition.light());
            }
        }
        assertEquals(5, ids.size());
        assertEquals(3, gases);
        assertEquals(0xFF6CE2FF, ClassicFluidDefinition.OXYGEN.tint());
        assertEquals(0xFFDBC1C1, ClassicFluidDefinition.HYDROGEN.tint());
        assertEquals(0xFFDFE5FE, ClassicFluidDefinition.NITROGEN.tint());
        assertEquals(0xFFE5D884, ClassicFluidDefinition.ROCKET_FUEL.tint());
        assertEquals(800, ClassicFluidDefinition.ROCKET_FUEL.density());
        assertEquals(1_500, ClassicFluidDefinition.ROCKET_FUEL.viscosity());
        assertEquals(2, ClassicFluidDefinition.ROCKET_FUEL.light());
        assertEquals(3_000, ClassicFluidDefinition.ENRICHED_LAVA.density());
        assertEquals(6_000, ClassicFluidDefinition.ENRICHED_LAVA.viscosity());
        assertEquals(1_300, ClassicFluidDefinition.ENRICHED_LAVA.temperature());
        assertEquals(15, ClassicFluidDefinition.ENRICHED_LAVA.light());
    }
}
