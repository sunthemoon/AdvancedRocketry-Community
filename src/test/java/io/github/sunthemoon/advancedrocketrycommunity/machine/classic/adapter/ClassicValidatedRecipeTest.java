package io.github.sunthemoon.advancedrocketrycommunity.machine.classic.adapter;

import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

class ClassicValidatedRecipeTest {
    private static final ResourceLocation ID = new ResourceLocation("test:recipe");

    @Test void strictCompleteRecipeOwnsCanonicalImmutableRowsWithoutRegistryBinding() {
        var recipe = ClassicRecipeCodec.decode(ID, json("{\"item\":\"stone\"}", 64, 72_000, 10_000));
        assertEquals(ID, recipe.id()); assertEquals(64, recipe.itemInputs().get(0).count());
        assertEquals(72_000, recipe.processingTicks()); assertEquals(10_000, recipe.energyPerTick());
        assertEquals(64, recipe.itemOutputs().get(0).count());
        assertEquals(16_000, recipe.fluidOutputs().get(0).amount());
        assertThrows(UnsupportedOperationException.class, () -> recipe.itemInputs().clear());
        assertThrows(IllegalStateException.class, recipe::logicalDefinition);
        assertEquals(recipe.jsonSignature(), ClassicRecipeCodec.decode(ID, recipe.canonicalJson()).jsonSignature());
    }

    @Test void tagOnlyUnknownRowsRemainDeferredAndDoNotBecomeTrustedLogicalDefinitions() {
        var recipe = ClassicRecipeCodec.decode(ID, json("{\"tag\":\"unknown:tag\"}", 1, 1, 1));
        assertEquals("{\"tag\":\"unknown:tag\"}", recipe.itemInputs().get(0).canonicalIngredientJson());
        assertThrows(IllegalStateException.class, recipe::logicalDefinition);
        assertThrows(NullPointerException.class, () -> recipe.resolvedItemAlternatives(0, null));
    }

    @Test void internalConstructionCannotPairDifferentCanonicalJsonAndRows() {
        var recipe = ClassicRecipeCodec.decode(ID, json("{\"item\":\"stone\"}", 1, 1, 1));
        assertThrows(IllegalArgumentException.class, () -> new ClassicValidatedRecipe(ID, recipe.canonicalJson(),
                List.of(new ClassicItemInput("{\"item\":\"dirt\"}", 1)), recipe.itemOutputs(),
                recipe.fluidInputs(), recipe.fluidOutputs(), 1, 1));
        assertThrows(IllegalArgumentException.class, () -> new ClassicValidatedRecipe(ID, recipe.canonicalJson(),
                recipe.itemInputs(), recipe.itemOutputs(), recipe.fluidInputs(), recipe.fluidOutputs(), 2, 1));
        assertThrows(IllegalArgumentException.class, () -> new ClassicValidatedRecipe(ID, " ".repeat(65_537),
                recipe.itemInputs(), recipe.itemOutputs(), recipe.fluidInputs(), recipe.fluidOutputs(), 1, 1));
        assertThrows(IllegalArgumentException.class, () -> new ClassicValidatedRecipe(ID, recipe.canonicalJson(),
                java.util.Collections.nCopies(5, recipe.itemInputs().get(0)), recipe.itemOutputs(),
                recipe.fluidInputs(), recipe.fluidOutputs(), 1, 1));
    }

    @Test void missingUnknownDuplicateAndCoercedFieldsAreRefused() {
        String valid = json("{\"item\":\"stone\"}", 1, 1, 1);
        for (String bad : List.of(valid.replace("\"schema_version\":1", "\"schema_version\":true"),
                valid.replace("\"schema_version\":1", "\"schema_version\":\"1\""),
                valid.replace("\"schema_version\":1", "\"schema_version\":2"),
                valid.replace("\"schema_version\":1", "\"schema_version\":1,\"schema_version\":1"),
                valid.replace("\"schema_version\":1,", ""), valid.replace("\"schema_version\":1", "\"schema_version\":1,\"extra\":0"),
                valid + "{}", valid.replace("\"energy_per_tick\":1", "\"energy_per_tick\":null"))) {
            assertThrows(IllegalArgumentException.class, () -> ClassicRecipeCodec.decode(ID, bad));
        }
    }

    @Test void allNumericAndCollectionBoundsAreEnforcedBeforePublication() {
        for (int count : new int[] {0, 65}) { assertThrows(IllegalArgumentException.class, () -> ClassicRecipeCodec.decode(ID, json("{\"item\":\"stone\"}", count, 1, 1))); }
        for (int ticks : new int[] {0, 72_001}) { assertThrows(IllegalArgumentException.class, () -> ClassicRecipeCodec.decode(ID, json("{\"item\":\"stone\"}", 1, ticks, 1))); }
        for (int energy : new int[] {0, 10_001}) { assertThrows(IllegalArgumentException.class, () -> ClassicRecipeCodec.decode(ID, json("{\"item\":\"stone\"}", 1, 1, energy))); }
        String row = "{\"ingredient\":{\"item\":\"stone\"},\"count\":1}";
        String text = json("{\"item\":\"stone\"}", 1, 1, 1);
        assertThrows(IllegalArgumentException.class, () -> ClassicRecipeCodec.decode(ID,
                text.replace("[" + row + "]", "[" + String.join(",", java.util.Collections.nCopies(5, row)) + "]")));
        assertThrows(IllegalArgumentException.class, () -> ClassicRecipeCodec.decode(ID, "[".repeat(2_000) + "0" + "]".repeat(2_000)));
        assertThrows(IllegalArgumentException.class, () -> ClassicRecipeCodec.decode(ID, " ".repeat(65_537)));
    }

    static String json(String ingredient, int count, int ticks, int energy) {
        return "{\"type\":\"advancedrocketrycommunity:lathe\",\"schema_version\":1,\"item_inputs\":[{\"ingredient\":"
                + ingredient + ",\"count\":" + count + "}],\"item_outputs\":[{\"item\":\"unknown:output\",\"count\":64}],"
                + "\"fluid_inputs\":[],\"fluid_outputs\":[{\"fluid\":\"unknown:fluid\",\"amount\":16000}],"
                + "\"processing_time\":" + ticks + ",\"energy_per_tick\":" + energy + "}";
    }
}
