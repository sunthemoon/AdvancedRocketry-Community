package io.github.sunthemoon.advancedrocketrycommunity.machine.classic.runtime;

import static org.junit.jupiter.api.Assertions.*;
import com.google.gson.JsonObject;
import io.github.sunthemoon.advancedrocketrycommunity.datagen.V180C16cMachineArt;
import io.github.sunthemoon.advancedrocketrycommunity.datagen.V180C16cMachineData;
import io.github.sunthemoon.advancedrocketrycommunity.machine.classic.adapter.*;
import io.github.sunthemoon.advancedrocketrycommunity.machine.multiblock.pattern.*;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class C16cMachineDataTest {
    @BeforeAll static void bootstrap() { MinecraftBootstrap.initialize(); }
    static JsonObject json(String id) { return V180C16cMachineData.serverFiles().get("data/advancedrocketrycommunity/recipes/" + id + ".json"); }
    static ClassicValidatedRecipe rows(String id) { return ClassicRecipeCodec.decode(ResourceLocation.tryParse("advancedrocketrycommunity:" + id), json(id).toString()); }
    @Test void crystallizerKeepsDustGemAndIngotNuggetBouleNumbers() {
        var dust = rows("crystallizing_dilithium"); var boule = rows("crystallizing_silicon_boule");
        for (var recipe : List.of(dust, boule)) { assertEquals(300, recipe.processingTicks()); assertEquals(20, recipe.energyPerTick()); assertEquals(1, recipe.itemOutputs().get(0).count()); }
        assertEquals("{\"tag\":\"forge:dusts/dilithium\"}", dust.itemInputs().get(0).canonicalIngredientJson());
        assertEquals("advancedrocketrycommunity:dilithium_crystal", dust.itemOutputs().get(0).itemId().toString());
        assertEquals(List.of("{\"tag\":\"forge:ingots/silicon\"}", "{\"tag\":\"forge:nuggets/silicon\"}"),
                boule.itemInputs().stream().map(ClassicItemInput::canonicalIngredientJson).toList());
        assertEquals("advancedrocketrycommunity:silicon_boule", boule.itemOutputs().get(0).itemId().toString());
        assertEquals(List.of(1, 1), boule.itemInputs().stream().map(ClassicItemInput::count).toList());
    }
    @Test void chemicalReactorHasOnlyFuelAndNitrogenBoneMealRoutes() {
        var fuel = rows("chemical_reacting_rocket_fuel"); var bone = rows("chemical_reacting_bone_meal");
        assertEquals(100, fuel.processingTicks()); assertEquals(10, fuel.energyPerTick()); assertTrue(fuel.itemInputs().isEmpty());
        assertEquals(List.of(new ClassicFluidRow(ResourceLocation.tryParse("advancedrocketrycommunity:oxygen"), 10),
                new ClassicFluidRow(ResourceLocation.tryParse("advancedrocketrycommunity:hydrogen"), 10)), fuel.fluidInputs());
        assertEquals(List.of(new ClassicFluidRow(ResourceLocation.tryParse("advancedrocketrycommunity:rocket_fuel"), 20)), fuel.fluidOutputs());
        assertEquals(100, bone.processingTicks()); assertEquals(1, bone.energyPerTick());
        assertEquals(1, bone.itemInputs().get(0).count()); assertEquals("{\"item\":\"minecraft:bone\"}", bone.itemInputs().get(0).canonicalIngredientJson());
        assertEquals(new ClassicFluidRow(ResourceLocation.tryParse("advancedrocketrycommunity:nitrogen"), 10), bone.fluidInputs().get(0));
        assertEquals(new ClassicItemOutput(ResourceLocation.tryParse("minecraft:bone_meal"), 8), bone.itemOutputs().get(0));
        assertEquals(2, V180C16cMachineData.serverFiles().keySet().stream().filter(key -> key.contains("/recipes/chemical_reacting_")).count());
    }
    @Test void etcherRecipesKeepFourInputsAndLegacyNumbers() {
        for (String tier : List.of("basic", "advanced")) {
            var recipe = rows("laser_etching_" + tier + "_circuit_plate");
            assertEquals(1_200, recipe.processingTicks()); assertEquals(tier.equals("basic") ? 400 : 600, recipe.energyPerTick());
            assertEquals(List.of(1, 1, 4, 1), recipe.itemInputs().stream().map(ClassicItemInput::count).toList());
            assertEquals("{\"tag\":\"forge:plates/gold\"}", recipe.itemInputs().get(0).canonicalIngredientJson());
            assertEquals("{\"item\":\"advancedrocketrycommunity:laser_lens\"}", recipe.itemInputs().get(3).canonicalIngredientJson());
            assertEquals(2, recipe.itemOutputs().get(0).count());
        }
        assertEquals("{\"tag\":\"forge:dusts/redstone\"}", rows("laser_etching_basic_circuit_plate").itemInputs().get(1).canonicalIngredientJson());
        assertEquals("{\"item\":\"minecraft:redstone_block\"}", rows("laser_etching_advanced_circuit_plate").itemInputs().get(1).canonicalIngredientJson());
    }
    @Test void centrifugeHasSeparateFiveOrderedCandidatesAndExactFluidEnergy() {
        var recipe = rows("centrifuging_enriched_lava");
        assertEquals(200, recipe.processingTicks()); assertEquals(10, recipe.energyPerTick()); assertTrue(recipe.itemOutputs().isEmpty());
        assertEquals(new ClassicFluidRow(ResourceLocation.tryParse("advancedrocketrycommunity:enriched_lava"), 1_000), recipe.fluidInputs().get(0));
        assertEquals(new ClassicFluidRow(ResourceLocation.tryParse("minecraft:lava"), 1_000), recipe.fluidOutputs().get(0));
        assertEquals(List.of("advancedrocketrycommunity:copper_nugget", "minecraft:iron_nugget", "advancedrocketrycommunity:tin_nugget",
                "minecraft:gold_nugget", "advancedrocketrycommunity:iridium_nugget"), recipe.chanceOutputs().stream().map(row -> row.itemId().toString()).toList());
        assertEquals(List.of(100, 100, 100, 75, 1), recipe.chanceOutputs().stream().map(ClassicChanceOutput::weight).toList());
        assertTrue(recipe.chanceOutputs().stream().allMatch(row -> row.count() == 1));
    }
    @Test void patternsHaveExactLegacyVolumesCruciblesSlabsAndClearance() {
        var volumes = Map.of("crystallizer", 12, "chemical_reactor", 12, "precision_laser_etcher", 27, "centrifuge", 36);
        volumes.forEach((id, volume) -> {
            var pattern = MultiblockPatternJsonCodec.decode(V180C16cMachineData.pattern(id).toString().getBytes(StandardCharsets.UTF_8));
            assertEquals(volume.intValue(), pattern.cells().size()); assertEquals(4, pattern.allowedRotations().size()); assertFalse(pattern.allowMirrorLocalX());
        });
        assertEquals(6, matcherCount("crystallizer", "quartz_crucible"));
        assertEquals(7, matcherCount("precision_laser_etcher", "minecraft:slabs"));
        assertEquals(1, matcherCount("precision_laser_etcher", "endgame_casing"));
        assertEquals(12, matcherCount("centrifuge", "endgame_casing"));
        assertEquals(2, matcherCount("chemical_reactor", "machine_clearance"));
        assertEquals(1, matcherCount("centrifuge", "machine_clearance"));
    }
    private long matcherCount(String id, String value) {
        return V180C16cMachineData.pattern(id).getAsJsonArray("cells").asList().stream()
                .filter(cell -> cell.getAsJsonObject().get("matcher").toString().contains(value)).count();
    }
    @Test void everyBlockAndShellHasOriginalDeterministicAssetsNamesAndCrafting() {
        var blocks = new java.util.ArrayList<>(V180C16cMachineData.BLOCKS); blocks.add("quartz_crucible_shell");
        for (String id : blocks) {
            assertArrayEquals(V180C16cMachineArt.png(id), V180C16cMachineArt.png(id));
            assertEquals(256, V180C16cMachineArt.pixels(id).length);
            assertNotNull(json(id)); assertNotNull(V180C16cMachineData.clientFiles().get("assets/advancedrocketrycommunity/models/item/" + id + ".json"));
            String key = (id.endsWith("shell") ? "item." : "block.") + "advancedrocketrycommunity." + id;
            assertNotNull(V180C16cMachineData.language(false).get(key)); assertNotNull(V180C16cMachineData.language(true).get(key));
        }
        assertEquals("advancedrocketrycommunity:quartz_crucible_shell", json("quartz_crucible").getAsJsonArray("ingredients").get(0).getAsJsonObject().get("item").getAsString());
        assertEquals(V180C16cMachineData.serverFiles(), V180C16cMachineData.serverFiles());
    }
}
