package io.github.sunthemoon.advancedrocketrycommunity.recipegraph;

import static io.github.sunthemoon.advancedrocketrycommunity.recipegraph.PackagedRecipeGraph.ENERGY;
import static io.github.sunthemoon.advancedrocketrycommunity.recipegraph.PackagedRecipeGraph.id;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.sunthemoon.advancedrocketrycommunity.recipegraph.RecipeGraphReachability.Producer;
import io.github.sunthemoon.advancedrocketrycommunity.recipegraph.RecipeGraphReachability.Success;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

/** Packaged producer models, independent of Minecraft bootstrap and of the advancement goal list. */
class PackagedRecipeGraphTest {
    private static PackagedRecipeGraph graph;

    @TempDir
    Path temporary;

    @BeforeAll
    static void load() throws IOException {
        graph = PackagedRecipeGraph.load();
    }

    @Test
    void everyPackagedRecipeHasAProducerAndNoTypeIsSkipped() throws IOException {
        Set<String> recipes = new TreeSet<>();
        try (var files = Files.walk(graph.root.resolve("data"))) {
            for (Path file : files.filter(Files::isRegularFile).toList()) {
                Path relative = graph.root.resolve("data").relativize(file);
                if (relative.getNameCount() > 2 && relative.getName(1).toString().equals("recipes")
                        && file.toString().endsWith(".json")) {
                    String path = relative.subpath(2, relative.getNameCount()).toString().replace('\\', '/');
                    recipes.add("recipe:" + relative.getName(0) + ":" + path.substring(0, path.length() - 5));
                }
            }
        }
        assertFalse(recipes.isEmpty());
        assertEquals(recipes, graph.producers.keySet().stream().filter(key -> key.startsWith("recipe:"))
                .collect(Collectors.toCollection(TreeSet::new)));
        assertEquals(Set.of(), graph.unmodelledTypes);
    }

    @ParameterizedTest
    @ValueSource(strings = {"advancedrocketrycommunity:future_machine", "minecraft:future_recipe"})
    void unknownPackagedTypesFailWithTypeAndRecipeIdentity(String type) throws IOException {
        Path recipe = temporary.resolve("data/advancedrocketrycommunity/recipes/unknown.json");
        Files.createDirectories(recipe.getParent());
        Files.writeString(recipe, "{\"type\":\"" + type + "\"}");
        IllegalStateException failure = assertThrows(IllegalStateException.class,
                () -> PackagedRecipeGraph.load(temporary));
        assertEquals("Unmodelled recipe type " + type + " in " + id("unknown"), failure.getMessage());
    }

    @ParameterizedTest
    @CsvSource({
            "lathe_iron_rod, lathe, #forge:ingots/iron, advancedrocketrycommunity:iron_rod",
            "arc_furnace_steel, arc_furnace, #forge:ingots/iron;minecraft:charcoal, advancedrocketrycommunity:steel_ingot",
            "cutting_silicon_wafer, cutting_machine, #advancedrocketrycommunity:boules/silicon, advancedrocketrycommunity:silicon_wafer",
            "crystallizing_dilithium, crystallizer, #forge:dusts/dilithium, advancedrocketrycommunity:dilithium_crystal",
            "chemical_reacting_rocket_fuel, chemical_reactor, fluid:advancedrocketrycommunity:oxygen;fluid:advancedrocketrycommunity:hydrogen, fluid:advancedrocketrycommunity:rocket_fuel",
            "laser_etching_basic_circuit_plate, precision_laser_etcher, #forge:plates/gold;#forge:dusts/redstone;advancedrocketrycommunity:silicon_wafer;advancedrocketrycommunity:laser_lens, advancedrocketrycommunity:basic_circuit_plate",
            "centrifuging_enriched_lava, centrifuge, fluid:advancedrocketrycommunity:enriched_lava, fluid:minecraft:lava;advancedrocketrycommunity:copper_nugget;minecraft:iron_nugget;advancedrocketrycommunity:tin_nugget;minecraft:gold_nugget;advancedrocketrycommunity:iridium_nugget"
    })
    void allSevenClassicTypesRequireTheirMachineEnergyAndInputsAndProduceEveryOutput(
            String recipe, String machine, String inputs, String outputs) {
        Producer producer = graph.producers.get("recipe:" + id(recipe));
        assertNotNull(producer);
        Set<List<String>> required = new HashSet<>();
        required.add(List.of("machine:" + id(machine)));
        required.add(List.of(ENERGY));
        for (String input : inputs.split(";")) {
            required.add(List.of(input));
        }
        assertEquals(required, new HashSet<>(producer.slots()));
        assertEquals(Set.of(outputs.split(";")), new HashSet<>(producer.outputs()));

        Set<String> roots = producer.slots().stream().flatMap(List::stream).collect(Collectors.toSet());
        assertTrue(evaluate(producer, roots).available().keySet().containsAll(producer.outputs()));
        // Check each AND prerequisite in isolation, without another recipe supplying an alternative route.
        for (List<String> slot : producer.slots()) {
            Set<String> missing = new HashSet<>(roots);
            missing.removeAll(slot);
            assertTrue(evaluate(producer, missing).unavailable().containsAll(producer.outputs()),
                    () -> recipe + " still produces outputs without " + slot);
        }
    }

    @Test
    void retainedEtcherLensIsARequirementNotAnOutputOrAConsumedReachabilityFact() {
        Producer etcher = graph.producers.get("recipe:" + id("laser_etching_basic_circuit_plate"));
        String lens = id("laser_lens");
        assertTrue(etcher.slots().contains(List.of(lens)));
        assertFalse(etcher.outputs().contains(lens), "Etching cannot manufacture a lens");
        Set<String> roots = etcher.slots().stream().flatMap(List::stream).collect(Collectors.toSet());
        Success result = evaluate(etcher, roots);
        assertTrue(result.available().containsKey(lens), "Etching retains the required lens");
        assertEquals(0, result.available().get(lens).round());
        assertTrue(result.available().containsKey(id("basic_circuit_plate")));
    }

    @ParameterizedTest
    @CsvSource({"helmet, #forge:armors/helmets", "chestplate, #forge:armors/chestplates",
            "leggings, #forge:armors/leggings", "boots, #forge:armors/boots"})
    void spaceBreathingRecipesNeedTheirArmorSealPlatesAndTheChestTankAndYieldOnlyTheEnchantment(
            String piece, String armor) {
        Producer producer = graph.producers.get("recipe:" + id("chemical_reacting_space_breathing_" + piece));
        assertNotNull(producer);
        Set<List<String>> required = new HashSet<>(Set.of(List.of("machine:" + id("chemical_reactor")), List.of(ENERGY),
                List.of(armor), List.of(id("pipe_seal")), List.of("#" + id("sheets/titanium_aluminide"))));
        if (piece.equals("chestplate")) {
            required.add(List.of(id("super_high_pressure_tank")));
        }
        assertEquals(required, new HashSet<>(producer.slots()));
        assertEquals(List.of(PackagedRecipeGraph.SPACE_BREATHING), producer.outputs(), "The armor piece is retained, not made");
        Set<String> roots = producer.slots().stream().flatMap(List::stream).collect(Collectors.toSet());
        assertTrue(evaluate(producer, roots).available().containsKey(PackagedRecipeGraph.SPACE_BREATHING));
        for (List<String> slot : producer.slots()) {
            Set<String> missing = new HashSet<>(roots);
            missing.removeAll(slot);
            assertTrue(evaluate(producer, missing).unavailable().contains(PackagedRecipeGraph.SPACE_BREATHING),
                    () -> piece + " still seals without " + slot);
        }
        assertFalse(PackagedRecipeGraph.tagMembers(armor.substring(1), "items").isEmpty(), "Forge armor tag on the classpath");
    }

    @Test
    void cartridgeRefreshRetainsTheCartridgeAndYieldsTheLegacyCharcoal() {
        Producer refresh = graph.producers.get("recipe:" + id("chemical_reacting_carbon_cartridge_refresh"));
        assertNotNull(refresh);
        assertEquals(Set.of(List.of("machine:" + id("chemical_reactor")), List.of(ENERGY),
                List.of(id("carbon_scrubber_cartridge"))), new HashSet<>(refresh.slots()));
        assertEquals(List.of("minecraft:charcoal"), refresh.outputs(), "Refreshing cannot manufacture a second cartridge");
    }

    @Test
    void spaceBreathingIsReachableInSurvivalOnlyThroughTheChemicalReactor() {
        Success result = assertInstanceOf(Success.class, RecipeGraphReachability.evaluate(graph.nodes, graph.roots,
                graph.producerList(), RecipeGraphReachability.DEFAULT_LIMITS));
        assertTrue(result.available().containsKey(PackagedRecipeGraph.SPACE_BREATHING), "space_breathing unreachable");
        assertEquals(Set.of("chemical_reacting_space_breathing_helmet", "chemical_reacting_space_breathing_chestplate",
                        "chemical_reacting_space_breathing_leggings", "chemical_reacting_space_breathing_boots").stream()
                        .map(recipe -> "recipe:" + id(recipe)).collect(Collectors.toSet()),
                graph.producers.values().stream().filter(producer -> producer.outputs().contains(PackagedRecipeGraph.SPACE_BREATHING))
                        .map(Producer::id).collect(Collectors.toSet()));
        Success withoutReactor = assertInstanceOf(Success.class, RecipeGraphReachability.evaluate(graph.nodes, graph.roots,
                graph.withoutInputs(Set.of("machine:" + id("chemical_reactor"))), RecipeGraphReachability.DEFAULT_LIMITS));
        assertFalse(withoutReactor.available().containsKey(PackagedRecipeGraph.SPACE_BREATHING));
    }

    @Test
    void unknownSpecialRecipesFailInsteadOfBeingSkipped() throws IOException {
        Path recipe = temporary.resolve("data/advancedrocketrycommunity/recipes/future_special.json");
        Files.createDirectories(recipe.getParent());
        Files.writeString(recipe, "{\"type\":\"advancedrocketrycommunity:chemical_reacting\",\"special\":\"future\","
                + "\"item_inputs\":[{\"ingredient\":{\"item\":\"minecraft:stone\"},\"count\":1}],\"item_outputs\":[],"
                + "\"fluid_inputs\":[],\"fluid_outputs\":[],\"processing_time\":1,\"energy_per_tick\":1}");
        IllegalStateException failure = assertThrows(IllegalStateException.class, () -> PackagedRecipeGraph.load(temporary));
        assertEquals("Unmodelled special future in " + id("future_special"), failure.getMessage());
    }

    @Test
    void newMachinePatternsRequireControllersHatchesAndStructuralParts() {
        Set<String> motors = Set.of(id("motor"), id("advanced_motor"), id("enhanced_motor"), id("elite_motor"));
        Map<String, Set<Set<String>>> expected = Map.of(
                "crystallizer", Set.of(Set.of(id("crystallizer")), Set.of(id("quartz_crucible")),
                        Set.of(id("item_input_hatch")), Set.of(id("item_output_hatch")),
                        Set.of(id("fluid_input_hatch")), Set.of(id("fluid_output_hatch")), Set.of(id("power_input_plug"))),
                "chemical_reactor", Set.of(Set.of(id("chemical_reactor")), motors,
                        Set.of(id("item_input_hatch")), Set.of(id("item_output_hatch")),
                        Set.of(id("fluid_input_hatch")), Set.of(id("fluid_output_hatch")), Set.of(id("power_input_plug"))),
                "precision_laser_etcher", Set.of(Set.of(id("precision_laser_etcher")), motors,
                        Set.of(id("machine_casing")), Set.of(id("endgame_casing")), survivalBlockTag("minecraft:slabs"),
                        Set.of(id("item_input_hatch")), Set.of(id("item_output_hatch")), Set.of(id("power_input_plug"))),
                "centrifuge", Set.of(Set.of(id("centrifuge")), motors,
                        Set.of(id("machine_casing")), Set.of(id("endgame_casing")), Set.of(id("item_output_hatch")),
                        Set.of(id("fluid_input_hatch")), Set.of(id("fluid_output_hatch")), Set.of(id("power_input_plug"))));
        expected.forEach((machine, slots) -> {
            Producer producer = graph.producers.get("machine:" + machine);
            assertNotNull(producer);
            assertEquals(slots, producer.slots().stream().map(Set::copyOf).collect(Collectors.toSet()), machine);
            assertEquals(List.of("machine:" + id(machine)), producer.outputs());
        });
        Success result = assertInstanceOf(Success.class, RecipeGraphReachability.evaluate(graph.nodes, graph.roots,
                graph.producerList(), RecipeGraphReachability.DEFAULT_LIMITS));
        assertEquals(9, PackagedRecipeGraph.CLASSIC_MACHINES.size());
        for (String machine : PackagedRecipeGraph.CLASSIC_MACHINES) {
            assertTrue(result.available().containsKey(id(machine)), "Unreachable controller " + machine);
            assertTrue(result.available().containsKey("machine:" + id(machine)), "Unformable " + machine);
        }
    }

    private static Success evaluate(Producer producer, Set<String> roots) {
        Set<String> nodes = new HashSet<>(producer.outputs());
        producer.slots().forEach(nodes::addAll);
        return assertInstanceOf(Success.class, RecipeGraphReachability.evaluate(nodes, roots, List.of(producer),
                RecipeGraphReachability.DEFAULT_LIMITS));
    }

    private static Set<String> survivalBlockTag(String tag) {
        Set<String> items = new TreeSet<>();
        for (String member : PackagedRecipeGraph.tagMembers(tag, "blocks")) {
            if (member.startsWith("#")) {
                items.addAll(survivalBlockTag(member.substring(1)));
            } else if (!PackagedRecipeGraph.NON_SURVIVAL.contains(member)) {
                items.add(member);
            }
        }
        assertFalse(items.isEmpty(), "Expected packaged vanilla slab members");
        return items;
    }
}
