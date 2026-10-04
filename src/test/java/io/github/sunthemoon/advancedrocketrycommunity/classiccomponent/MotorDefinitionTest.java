package io.github.sunthemoon.advancedrocketrycommunity.classiccomponent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class MotorDefinitionTest {
    @Test
    void fourStableIdsAndSavedAdvancedCasingIdRemainDistinct() {
        assertEquals(List.of("motor", "advanced_motor", "enhanced_motor", "elite_motor"),
                Arrays.stream(MotorDefinition.values()).map(MotorDefinition::id).toList());
        assertEquals("advancedrocketrycommunity:motors", MotorDefinition.TAG_ID);
        assertEquals("advancedrocketrycommunity:endgame_casing", MotorDefinition.ADVANCED_CASING_ID);
        assertNull(MotorDefinition.MOTOR.previous());
    }

    @Test
    void baseMotorUsesExactlyTheAcceptedSixCraftingUnits() {
        assertEquals(Map.of("advancedrocketrycommunity:coils/copper", 1, "forge:plates/steel", 2,
                "forge:rods/iron", 2, "forge:ingots/steel", 1), counts(MotorDefinition.MOTOR));
        assertEquals(6, MotorDefinition.MOTOR.ingredients().stream().mapToInt(MotorDefinition.Ingredient::count).sum());
    }

    @Test
    void upgradesConsumeOneExactPreviousTierAndTheNextMetal() {
        for (MotorDefinition tier : List.of(MotorDefinition.ADVANCED, MotorDefinition.ENHANCED, MotorDefinition.ELITE)) {
            assertEquals(Map.of(tier.previous().fullId(), 1,
                    "advancedrocketrycommunity:coils/" + tier.metal(), 1,
                    "forge:plates/" + tier.metal(), 2), counts(tier));
            assertEquals(false, tier.ingredients().get(0).tag());
            assertEquals(4, tier.ingredients().stream().mapToInt(MotorDefinition.Ingredient::count).sum());
        }
    }

    @Test
    void invalidIngredientIdsAndCountsRefuseAtConstruction() {
        assertThrows(IllegalArgumentException.class, () -> new MotorDefinition.Ingredient(true, "forge:plates/steel", 0));
        assertThrows(IllegalArgumentException.class, () -> new MotorDefinition.Ingredient(true, "forge:plates/steel", 10));
        assertThrows(IllegalArgumentException.class, () -> new MotorDefinition.Ingredient(true, "FORGE:plates/steel", 1));
    }

    private static Map<String, Integer> counts(MotorDefinition tier) {
        return tier.ingredients().stream().collect(Collectors.toMap(MotorDefinition.Ingredient::id,
                MotorDefinition.Ingredient::count));
    }
}
