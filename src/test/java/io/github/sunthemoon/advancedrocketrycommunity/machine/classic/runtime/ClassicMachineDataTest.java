package io.github.sunthemoon.advancedrocketrycommunity.machine.classic.runtime;

import static org.junit.jupiter.api.Assertions.*;
import io.github.sunthemoon.advancedrocketrycommunity.datagen.V180ClassicMachineArt;
import io.github.sunthemoon.advancedrocketrycommunity.datagen.V180ClassicMachineData;
import io.github.sunthemoon.advancedrocketrycommunity.machine.classic.adapter.ClassicRecipeCodec;
import io.github.sunthemoon.advancedrocketrycommunity.machine.multiblock.pattern.*;
import java.nio.charset.StandardCharsets;
import java.util.EnumSet;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

class ClassicMachineDataTest {
    @Test void patternHasTheLegacyRowsAndAllRotationsWithoutMirrors() {
        var pattern = MultiblockPatternJsonCodec.decode(V180ClassicMachineData.lathePattern().toString().getBytes(StandardCharsets.UTF_8));
        assertEquals(new PatternSize(4, 2, 1), pattern.size()); assertEquals(new PatternPosition(0, 1, 0), pattern.controllerAnchor());
        assertEquals(EnumSet.allOf(PatternRotation.class), pattern.allowedRotations()); assertFalse(pattern.allowMirrorLocalX());
        assertInstanceOf(PatternMatcher.Controller.class, pattern.cells().get(new PatternPosition(0, 1, 0)));
        assertEquals(new PatternMatcher.BlockTag("advancedrocketrycommunity:motors"), pattern.cells().get(new PatternPosition(1, 1, 0)));
        assertInstanceOf(PatternMatcher.Air.class, pattern.cells().get(new PatternPosition(2, 1, 0)));
        assertEquals(new PatternMatcher.Port("item_input"), pattern.cells().get(new PatternPosition(3, 1, 0)));
        assertEquals(new PatternMatcher.Port("energy_input"), pattern.cells().get(new PatternPosition(0, 0, 0)));
        assertEquals(new PatternMatcher.Port("item_output"), pattern.cells().get(new PatternPosition(3, 0, 0)));
    }
    @Test void sevenAuthoredRodRecipesKeepLegacyAccountingAndTagRows() {
        var recipes = V180ClassicMachineData.serverFiles().entrySet().stream().filter(entry -> entry.getKey().contains("recipes/lathe_")).toList();
        assertEquals(7, recipes.size());
        for (var entry : recipes) {
            var rows = ClassicRecipeCodec.decode(ResourceLocation.tryParse("test:lathe"), entry.getValue().toString());
            assertEquals(300, rows.processingTicks()); assertEquals(20, rows.energyPerTick());
            assertEquals(1, rows.itemInputs().get(0).count()); assertTrue(rows.itemInputs().get(0).canonicalIngredientJson().contains("forge:ingots/"));
            assertEquals(2, rows.itemOutputs().get(0).count());
        }
    }
    @Test void codecRejectsUnknownFieldsAndOutOfRangeNumbersWithoutResolvingTags() {
        var recipe = V180ClassicMachineData.rodRecipe("iron"); var id = ResourceLocation.tryParse("test:lathe");
        recipe.addProperty("unexpected", 1); assertThrows(IllegalArgumentException.class, () -> ClassicRecipeCodec.decode(id, recipe.toString()));
        recipe.remove("unexpected"); recipe.addProperty("processing_time", 72_001);
        assertThrows(IllegalArgumentException.class, () -> ClassicRecipeCodec.decode(id, recipe.toString()));
        recipe.addProperty("processing_time", 72_000); recipe.addProperty("energy_per_tick", 10_001);
        assertThrows(IllegalArgumentException.class, () -> ClassicRecipeCodec.decode(id, recipe.toString()));
        recipe.addProperty("energy_per_tick", 10_000); assertNotNull(ClassicRecipeCodec.decode(id, recipe.toString()));
    }
    @Test void originalArtAndLanguageAreCompleteAndDeterministic() {
        assertEquals(V180ClassicMachineData.language(false).keySet(), V180ClassicMachineData.language(true).keySet());
        for (String block : V180ClassicMachineData.BLOCKS) {
            assertEquals(256, V180ClassicMachineArt.pixels(block).length);
            assertArrayEquals(V180ClassicMachineArt.png(block), V180ClassicMachineArt.png(block));
            assertTrue(V180ClassicMachineData.clientFiles().containsKey("assets/advancedrocketrycommunity/blockstates/" + block + ".json"));
            assertTrue(V180ClassicMachineData.serverFiles().containsKey("data/advancedrocketrycommunity/recipes/" + block + ".json"));
            assertTrue(V180ClassicMachineData.language(true).containsKey("block.advancedrocketrycommunity." + block));
        }
        assertNotEquals(java.util.Arrays.hashCode(V180ClassicMachineArt.pixels("item_input_hatch")),
                java.util.Arrays.hashCode(V180ClassicMachineArt.pixels("item_output_hatch")));
    }
}
