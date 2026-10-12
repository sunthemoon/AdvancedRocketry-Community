package io.github.sunthemoon.advancedrocketrycommunity.machine.classic.runtime;

import static org.junit.jupiter.api.Assertions.*;
import com.google.gson.JsonObject;
import io.github.sunthemoon.advancedrocketrycommunity.config.CommonConfig;
import io.github.sunthemoon.advancedrocketrycommunity.config.SwitchOverrides;
import io.github.sunthemoon.advancedrocketrycommunity.datagen.V180ArcCuttingArt;
import io.github.sunthemoon.advancedrocketrycommunity.datagen.V180ArcCuttingData;
import io.github.sunthemoon.advancedrocketrycommunity.machine.classic.adapter.ClassicRecipeCodec;
import io.github.sunthemoon.advancedrocketrycommunity.machine.classic.adapter.ClassicValidatedRecipe;
import io.github.sunthemoon.advancedrocketrycommunity.machine.multiblock.pattern.*;
import java.nio.charset.StandardCharsets;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

class ArcCuttingDataTest {
    private static JsonObject json(String id) { return V180ArcCuttingData.serverFiles().get("data/advancedrocketrycommunity/recipes/" + id + ".json"); }
    private static ClassicValidatedRecipe rows(String id) { return ClassicRecipeCodec.decode(ResourceLocation.tryParse("advancedrocketrycommunity:" + id), json(id).toString()); }
    @Test void allFiveArcRoutesHaveExactLegacyCountsTimesEnergyAndMaterialTags() {
        assertEquals(5, V180ArcCuttingData.serverFiles().keySet().stream().filter(key -> key.contains("recipes/arc_furnace_")).count());
        var expected = Map.of("steel", List.of(6_000, 1, 1), "silicon", List.of(12_000, 1, 1),
                "titanium", List.of(100, 10, 1), "titanium_aluminide", List.of(9_000, 20, 3), "titanium_iridium", List.of(3_000, 20, 2));
        expected.forEach((material, values) -> {
            var recipe = rows("arc_furnace_" + material);
            assertEquals(values.get(0).intValue(), recipe.processingTicks()); assertEquals(values.get(1).intValue(), recipe.energyPerTick());
            assertEquals(values.get(2).intValue(), recipe.itemOutputs().get(0).count());
            assertEquals("advancedrocketrycommunity:" + material + "_ingot", recipe.itemOutputs().get(0).itemId().toString());
            assertFalse(recipe.sawmill()); assertTrue(recipe.enabled(false));
        });
        var steel = rows("arc_furnace_steel"); assertEquals(2, steel.itemInputs().size());
        assertEquals("{\"tag\":\"forge:ingots/iron\"}", steel.itemInputs().get(0).canonicalIngredientJson());
        assertEquals("{\"item\":\"minecraft:charcoal\"}", steel.itemInputs().get(1).canonicalIngredientJson());
        assertTrue(steel.itemInputs().stream().allMatch(input -> input.count() == 1));
        assertEquals(List.of(7, 3), rows("arc_furnace_titanium_aluminide").itemInputs().stream().map(input -> input.count()).toList());
        assertEquals(List.of(1, 1), rows("arc_furnace_titanium_iridium").itemInputs().stream().map(input -> input.count()).toList());
        assertEquals("{\"item\":\"minecraft:sand\"}", rows("arc_furnace_silicon").itemInputs().get(0).canonicalIngredientJson());
        assertEquals("[{\"tag\":\"forge:ores/rutile\"},{\"tag\":\"forge:raw_materials/rutile\"}]",
                rows("arc_furnace_titanium").itemInputs().get(0).canonicalIngredientJson());
    }
    @Test void wafersAndBothCircuitTiersGiveFourAt300TicksAnd100Fe() {
        for (String product : List.of("silicon_wafer", "basic_circuit", "advanced_circuit")) {
            var recipe = rows("cutting_" + product);
            assertEquals(300, recipe.processingTicks()); assertEquals(100, recipe.energyPerTick());
            assertEquals(1, recipe.itemInputs().get(0).count()); assertEquals(4, recipe.itemOutputs().get(0).count());
            assertEquals("advancedrocketrycommunity:" + product, recipe.itemOutputs().get(0).itemId().toString());
            assertFalse(recipe.sawmill()); assertTrue(recipe.enabled(false));
        }
    }
    @Test void everyVanillaLogAndStemAndLightwoodHasOneExactSixPlankRecipe() {
        assertEquals(Set.of("minecraft:oak_log", "minecraft:spruce_log", "minecraft:birch_log", "minecraft:jungle_log",
                "minecraft:acacia_log", "minecraft:dark_oak_log", "minecraft:mangrove_log", "minecraft:cherry_log",
                "minecraft:crimson_stem", "minecraft:warped_stem", "advancedrocketrycommunity:lightwood_log"), V180ArcCuttingData.WOODS.keySet());
        long marked = V180ArcCuttingData.serverFiles().values().stream().filter(value -> value.has("sawmill")).count(); assertEquals(11, marked);
        V180ArcCuttingData.WOODS.forEach((log, planks) -> {
            var recipe = rows("cutting_" + log.substring(log.indexOf(':') + 1));
            assertEquals("{\"item\":\"" + log + "\"}", recipe.itemInputs().get(0).canonicalIngredientJson());
            assertEquals(1, recipe.itemInputs().get(0).count()); assertEquals(planks, recipe.itemOutputs().get(0).itemId().toString());
            assertEquals(6, recipe.itemOutputs().get(0).count()); assertEquals(80, recipe.processingTicks()); assertEquals(10, recipe.energyPerTick());
            assertTrue(recipe.sawmill()); assertFalse(recipe.enabled(false)); assertTrue(recipe.enabled(true));
        });
    }
    @Test void commonSawmillDefaultAndInMemoryOverrideControlOnlyMarkedRecipes() {
        assertTrue(CommonConfig.SAWMILL_VANILLA_WOOD.getDefault());
        assertEquals(List.of("machines", "sawmillVanillaWood"), CommonConfig.SAWMILL_VANILLA_WOOD.getPath());
        try {
            SwitchOverrides.set(CommonConfig.SAWMILL_VANILLA_WOOD, false);
            assertFalse(CommonConfig.sawmillVanillaWood()); assertFalse(rows("cutting_oak_log").enabled(CommonConfig.sawmillVanillaWood()));
            assertTrue(rows("cutting_basic_circuit").enabled(CommonConfig.sawmillVanillaWood()));
            SwitchOverrides.set(CommonConfig.SAWMILL_VANILLA_WOOD, true); assertTrue(CommonConfig.sawmillVanillaWood());
        } finally { SwitchOverrides.clear(CommonConfig.SAWMILL_VANILLA_WOOD); }
    }
    @Test void flagIsStrictCuttingOnlyAndPartOfTheJsonSignature() {
        var id = ResourceLocation.tryParse("test:recipe"); var recipe = json("cutting_oak_log").deepCopy();
        String signature = ClassicRecipeCodec.decode(id, recipe.toString()).jsonSignature();
        recipe.addProperty("sawmill", false); assertNotEquals(signature, ClassicRecipeCodec.decode(id, recipe.toString()).jsonSignature());
        recipe.addProperty("sawmill", "true"); assertThrows(IllegalArgumentException.class, () -> ClassicRecipeCodec.decode(id, recipe.toString()));
        recipe.addProperty("sawmill", true); recipe.addProperty("type", "advancedrocketrycommunity:arc_furnace");
        assertThrows(IllegalArgumentException.class, () -> ClassicRecipeCodec.decode(id, recipe.toString()));
        recipe.remove("sawmill"); recipe.addProperty("type", "advancedrocketrycommunity:unregistered");
        assertThrows(IllegalArgumentException.class, () -> ClassicRecipeCodec.decode(id, recipe.toString()));
        var arc = json("arc_furnace_steel").deepCopy();
        assertThrows(IllegalArgumentException.class, () -> new ClassicMachineRecipe.Serializer().fromJson(id, arc));
    }
    @Test void bothPatternsKeepLegacyGeometryAndFourRotations() {
        var arc = pattern(V180ArcCuttingData.arcPattern()); var cutting = pattern(V180ArcCuttingData.cuttingPattern());
        assertEquals(new PatternSize(5, 5, 5), arc.size()); assertEquals(new PatternPosition(2, 1, 0), arc.controllerAnchor());
        assertEquals(125, arc.cells().size());
        assertEquals(72, arc.cells().values().stream().filter(PatternMatcher.ExactBlock.class::isInstance).count());
        assertEquals(15, arc.cells().values().stream().filter(PatternMatcher.Air.class::isInstance).count());
        var nullCells = new java.util.HashSet<PatternPosition>();
        for (int i = 0; i < 5; i++) {
            nullCells.addAll(List.of(new PatternPosition(i, 4, 0), new PatternPosition(i, 4, 4), new PatternPosition(0, 4, i), new PatternPosition(4, 4, i)));
        }
        nullCells.addAll(List.of(new PatternPosition(0, 3, 0), new PatternPosition(4, 3, 0), new PatternPosition(0, 3, 4), new PatternPosition(4, 3, 4)));
        var clearance = new PatternMatcher.BlockTag(V180ArcCuttingData.CLEARANCE_TAG);
        assertEquals(20, nullCells.size());
        assertEquals(nullCells, Set.copyOf(arc.cells().entrySet().stream().filter(cell -> cell.getValue().equals(clearance)).map(Map.Entry::getKey).toList()));
        assertEquals(3, arc.cells().values().stream().filter(matcher -> matcher.equals(new PatternMatcher.BlockTag("advancedrocketrycommunity:coils"))).count());
        assertEquals(new PatternMatcher.Port("energy_input"), arc.cells().get(new PatternPosition(1, 4, 1)));
        assertEquals(new PatternSize(3, 1, 2), cutting.size()); assertEquals(new PatternPosition(1, 0, 0), cutting.controllerAnchor());
        assertEquals(new PatternMatcher.Port("item_input"), cutting.cells().get(new PatternPosition(0, 0, 0)));
        assertEquals(new PatternMatcher.Port("item_output"), cutting.cells().get(new PatternPosition(2, 0, 0)));
        assertEquals(new PatternMatcher.BlockTag("advancedrocketrycommunity:motors"), cutting.cells().get(new PatternPosition(0, 0, 1)));
        assertEquals(new PatternMatcher.ExactBlock("advancedrocketrycommunity:saw_blade"), cutting.cells().get(new PatternPosition(1, 0, 1)));
        for (var pattern : List.of(arc, cutting)) { assertEquals(EnumSet.allOf(PatternRotation.class), pattern.allowedRotations()); assertFalse(pattern.allowMirrorLocalX()); }
    }
    @Test void recipeNetworkRoundTripsRetainTypesFlagsAndSignaturesAndRefuseWrongSerializers() {
        for (String id : List.of("arc_furnace_steel", "cutting_silicon_wafer", "cutting_oak_log")) {
            var recipe = new ClassicMachineRecipe(rows(id)); var serializer = new ClassicMachineRecipe.Serializer(recipe.rows().type());
            var buffer = new net.minecraft.network.FriendlyByteBuf(io.netty.buffer.Unpooled.buffer());
            try {
                serializer.toNetwork(buffer, recipe); var decoded = serializer.fromNetwork(recipe.getId(), buffer);
                assertEquals(recipe.rows().canonicalJson(), decoded.rows().canonicalJson()); assertEquals(recipe.rows().jsonSignature(), decoded.rows().jsonSignature());
                assertEquals(recipe.rows().sawmill(), decoded.rows().sawmill()); assertEquals(0, buffer.readableBytes());
                assertThrows(IllegalArgumentException.class, () -> new ClassicMachineRecipe.Serializer().toNetwork(buffer, recipe));
                serializer.toNetwork(buffer, recipe);
                assertThrows(IllegalArgumentException.class, () -> new ClassicMachineRecipe.Serializer().fromNetwork(recipe.getId(), buffer));
            } finally { buffer.release(); }
        }
        var json = io.github.sunthemoon.advancedrocketrycommunity.datagen.V180ClassicMachineData.rodRecipe("iron");
        var old = ClassicRecipeCodec.decode(ResourceLocation.tryParse("test:lathe"), json.toString());
        assertEquals(io.github.sunthemoon.advancedrocketrycommunity.machine.recipe.RecipeJsonSignature.sha256(
                io.github.sunthemoon.advancedrocketrycommunity.machine.recipe.RecipeJsonSignature.canonical(json)), old.jsonSignature());
    }
    @Test void conservativeClearanceStillWaitsForUnloadedChunksAndRejectsNewMatcherKinds() {
        var pattern = pattern(V180ArcCuttingData.arcPattern()); var origin = pattern.controllerAnchor();
        var unloaded = new PatternPosition(0, 4, 0);
        var result = MultiblockPatternValidator.validate(pattern, new PatternTransform(PatternRotation.ZERO, false), origin,
                position -> position.equals(unloaded) ? PatternObservation.unloaded() : PatternObservation.loaded(block(pattern.cells().get(position))));
        assertEquals(PatternValidationStatus.WAITING_UNLOADED, result.status()); assertEquals(125, result.inspectedCells());
        var json = V180ArcCuttingData.arcPattern(); json.getAsJsonArray("cells").get(0).getAsJsonObject().getAsJsonObject("matcher").addProperty("any", true);
        assertThrows(IllegalArgumentException.class, () -> pattern(json));
    }
    @Test void legacyNullCellsAdmitClearanceDecorationButLegacyAirAndSolidBlocksStillRefuse() {
        var pattern = pattern(V180ArcCuttingData.arcPattern());
        var snow = new PatternBlock("minecraft:snow", Set.of(V180ArcCuttingData.CLEARANCE_TAG), false, false, Optional.empty());
        var stone = new PatternBlock("minecraft:stone", Set.of(), false, false, Optional.empty());
        var rim = new PatternPosition(1, 4, 0); var chamber = new PatternPosition(1, 2, 1);
        assertEquals(PatternValidationStatus.FORMED, validateWith(pattern, rim, snow).status());
        assertEquals(PatternValidationStatus.MISMATCH, validateWith(pattern, rim, stone).status());
        assertEquals(PatternValidationStatus.MISMATCH, validateWith(pattern, chamber, snow).status());
        var tag = V180ArcCuttingData.serverFiles().get("data/advancedrocketrycommunity/tags/blocks/machine_clearance.json");
        assertFalse(tag.get("replace").getAsBoolean());
        var values = new java.util.ArrayList<String>(); tag.getAsJsonArray("values").forEach(value -> values.add(value.getAsString()));
        assertTrue(values.containsAll(List.of("minecraft:air", "minecraft:cave_air", "minecraft:void_air", "minecraft:snow",
                "minecraft:torch", "minecraft:wall_torch", "minecraft:soul_torch", "minecraft:soul_wall_torch", "minecraft:redstone_torch",
                "minecraft:redstone_wall_torch", "minecraft:redstone_wire", "#minecraft:rails", "#minecraft:wool_carpets", "minecraft:vine")));
        assertEquals(values.size(), Set.copyOf(values).size());
    }
    private static PatternValidationResult validateWith(MultiblockPatternDefinition pattern, PatternPosition replaced, PatternBlock block) {
        return MultiblockPatternValidator.validate(pattern, new PatternTransform(PatternRotation.ZERO, false), pattern.controllerAnchor(),
                position -> PatternObservation.loaded(position.equals(replaced) ? block : block(pattern.cells().get(position))));
    }
    @Test void originalAssetsLanguagesAndBootstrapCraftsAreCompleteAndDeterministic() {
        assertEquals(V180ArcCuttingData.language(false).keySet(), V180ArcCuttingData.language(true).keySet());
        for (String id : List.of("arc_furnace", "cutting_machine", "blast_brick", "saw_blade", "iron_saw_blade")) {
            assertArrayEquals(V180ArcCuttingArt.png(id), V180ArcCuttingArt.png(id)); assertEquals(256, V180ArcCuttingArt.pixels(id).length);
            assertTrue(V180ArcCuttingData.clientFiles().containsKey("assets/advancedrocketrycommunity/models/item/" + id + ".json"));
            assertNotNull(json(id));
        }
        String craft = json("arc_furnace").toString(); assertFalse(craft.contains("steel")); assertFalse(craft.contains("motor")); assertFalse(craft.contains("circuit"));
    }
    private static MultiblockPatternDefinition pattern(JsonObject json) { return MultiblockPatternJsonCodec.decode(json.toString().getBytes(StandardCharsets.UTF_8)); }
    private static PatternBlock block(PatternMatcher matcher) {
        String id = matcher instanceof PatternMatcher.ExactBlock exact ? exact.blockId() : "minecraft:air";
        var tags = matcher instanceof PatternMatcher.BlockTag tag ? Set.of(tag.tagId()) : Set.<String>of();
        var port = matcher instanceof PatternMatcher.Port value ? Optional.of(value.channel()) : Optional.<String>empty();
        return new PatternBlock(id, tags, matcher instanceof PatternMatcher.Air, matcher instanceof PatternMatcher.Controller, port);
    }
}
