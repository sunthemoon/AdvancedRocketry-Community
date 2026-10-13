package io.github.sunthemoon.advancedrocketrycommunity.recipegraph;

import static io.github.sunthemoon.advancedrocketrycommunity.recipegraph.PackagedRecipeGraph.id;
import static org.junit.jupiter.api.Assertions.*;

import io.github.sunthemoon.advancedrocketrycommunity.recipegraph.RecipeGraphReachability.Producer;
import io.github.sunthemoon.advancedrocketrycommunity.recipegraph.RecipeGraphReachability.Success;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** Non-recipe routes and the exact, non-root optional-input policy; no Minecraft classes are initialized. */
class CodeAcquisitionGraphTest {
    private static PackagedRecipeGraph graph;
    @TempDir Path temporary;

    @BeforeAll
    static void load() throws IOException { graph = PackagedRecipeGraph.load(); }

    @Test
    void optionalInputsAreExactlyTheFiveDustItemsAndTheirFiveTags() {
        assertEquals(Set.of(id("silicon_dust"), id("steel_dust"), id("titanium_dust"),
                id("titanium_aluminide_dust"), id("titanium_iridium_dust"),
                "#forge:dusts/silicon", "#forge:dusts/steel", "#forge:dusts/titanium",
                "#forge:dusts/titanium_aluminide", "#forge:dusts/titanium_iridium"),
                PackagedRecipeGraph.OPTIONAL_CROSS_MOD_INPUTS);
        assertTrue(graph.nodes.containsAll(PackagedRecipeGraph.OPTIONAL_CROSS_MOD_INPUTS));
        assertTrue(java.util.Collections.disjoint(graph.roots, PackagedRecipeGraph.OPTIONAL_CROSS_MOD_INPUTS));
    }

    @Test
    void everyRequiredGraphNodeIsAvailableAndOptionalInputsCannotSupplyItsOnlyRoute() {
        Success baseline = evaluate(graph.roots, graph.producerList());
        assertEquals(PackagedRecipeGraph.OPTIONAL_CROSS_MOD_INPUTS, baseline.unavailable(),
                "Any additional unavailable node is required; no further exemption is authorized");
        Set<String> roots = new HashSet<>(graph.roots);
        roots.addAll(PackagedRecipeGraph.OPTIONAL_CROSS_MOD_INPUTS);
        Success supplied = evaluate(roots, graph.producerList());
        roots.removeAll(PackagedRecipeGraph.OPTIONAL_CROSS_MOD_INPUTS);
        Success forbidden = evaluate(roots, graph.withoutInputs(PackagedRecipeGraph.OPTIONAL_CROSS_MOD_INPUTS));
        Set<String> requiredAvailable = new TreeSet<>(supplied.available().keySet());
        requiredAvailable.removeAll(PackagedRecipeGraph.OPTIONAL_CROSS_MOD_INPUTS);
        assertEquals(requiredAvailable, forbidden.available().keySet(),
                "A required route depends only on a cross-mod input");
        assertEquals(PackagedRecipeGraph.OPTIONAL_CROSS_MOD_INPUTS, forbidden.unavailable());
    }

    @ParameterizedTest
    @ValueSource(strings = {"drain:hydrogen", "drain:oxygen", "drain:nitrogen", "gas_harvester",
            "gas_harvest:gas_giant.json", "volcano", "pump:enriched_lava",
            "flight:advancedrocketrycommunity:earth_mars", "lightwood:access:tau_ceti_f"})
    void codeBackedProducersNeedEveryDeclaredPrerequisite(String key) {
        Producer producer = graph.producers.get("code:" + key);
        assertNotNull(producer, key);
        assertNotNull(graph.codeSources.get(producer.id()), "Runtime source citation is required");
        Set<String> nodes = new HashSet<>(producer.outputs());
        Set<String> roots = new HashSet<>();
        producer.slots().forEach(slot -> { nodes.addAll(slot); roots.addAll(slot); });
        Success complete = assertInstanceOf(Success.class, RecipeGraphReachability.evaluate(nodes, roots,
                List.of(producer), RecipeGraphReachability.DEFAULT_LIMITS));
        assertTrue(complete.available().keySet().containsAll(producer.outputs()));
        for (List<String> slot : producer.slots()) {
            Set<String> missing = new HashSet<>(roots);
            missing.removeAll(slot);
            Success incomplete = assertInstanceOf(Success.class, RecipeGraphReachability.evaluate(nodes, missing,
                    List.of(producer), RecipeGraphReachability.DEFAULT_LIMITS));
            assertTrue(incomplete.unavailable().containsAll(producer.outputs()), () -> key + " without " + slot);
        }
    }

    @Test
    void gasesAreNotWorldSourcesAndRocketFuelStillRequiresItsRealRecipe() {
        for (String gas : List.of("hydrogen", "oxygen", "nitrogen")) {
            assertFalse(graph.nodes.contains("source:" + id(gas)));
            assertTrue(graph.producers.get("code:drain:" + gas).slots().contains(List.of(id(gas + "_canister"))));
        }
        Producer fuel = graph.producers.get("recipe:" + id("chemical_reacting_rocket_fuel"));
        assertTrue(fuel.slots().contains(List.of("fluid:" + id("oxygen"))));
        assertTrue(fuel.slots().contains(List.of("fluid:" + id("hydrogen"))));
        Success available = evaluate(graph.roots, graph.producerList());
        assertEquals(fuel.id(), available.available().get("fluid:" + id("rocket_fuel")).producer());
    }

    @Test
    void exoplanetAccessRequiresStationWarpThenAnInSystemRocketRoute() {
        Producer warp = graph.producers.get("code:warp:" + id("tau_ceti_f"));
        assertTrue(warp.slots().contains(List.of(id("warp_core"))));
        assertTrue(warp.slots().contains(List.of(id("warp_controller"))));
        assertTrue(warp.slots().contains(List.of("known:" + id("tau_ceti_f"))));
        Producer flight = graph.producers.get("code:flight:" + id("tau_ceti_f_surface_orbit") + ":reverse");
        assertTrue(flight.slots().contains(List.of("orbit:" + id("tau_ceti_f"))));
        assertTrue(flight.slots().contains(List.of(PackagedRecipeGraph.ROCKET)));
        assertEquals(List.of("access:tau_ceti_f"), flight.outputs());
    }

    @Test
    void electrolyzerStillRequiresCanistersWaterEnergyAndItsRealController() {
        Producer producer = graph.producers.get("recipe:" + id("electrolyzer_water"));
        assertNotNull(producer);
        assertEquals(Set.of(List.of(id("electrolyzer")), List.of(id("empty_canister")),
                List.of("fluid:minecraft:water"), List.of(PackagedRecipeGraph.ENERGY)), new HashSet<>(producer.slots()));
        assertEquals(Set.of(id("hydrogen_canister"), id("oxygen_canister")), new HashSet<>(producer.outputs()));
        assertNotNull(graph.codeSources.get(producer.id()));
    }

    @Test
    void gasHarvestRequiresAGasGiantTableAndAnAllowedLaunchTarget() throws IOException {
        Path root = fixture();
        Path body = root.resolve("data/advancedrocketrycommunity/celestial_bodies/gas_giant.json");
        var json = PackagedRecipeGraph.read(body);
        json.getAsJsonObject("capabilities").addProperty("gas_giant", false);
        Files.writeString(body, json.toString());
        PackagedRecipeGraph disabled = PackagedRecipeGraph.load(root);
        assertFalse(disabled.producers.containsKey("code:gas_harvest:gas_giant.json"));
        Success result = assertInstanceOf(Success.class, RecipeGraphReachability.evaluate(disabled.nodes, disabled.roots,
                disabled.producerList(), RecipeGraphReachability.DEFAULT_LIMITS));
        assertTrue(result.unavailable().contains("fluid:" + id("nitrogen")));
    }

    @Test
    void removedWorldgenDefinitionsCannotSupplyLightwoodOrVolcanoFluids() throws IOException {
        Path root = fixture();
        Files.delete(root.resolve("data/advancedrocketrycommunity/worldgen/configured_feature/lightwood_tree.json"));
        Files.delete(root.resolve("data/advancedrocketrycommunity/worldgen/structure_set/volcanoes.json"));
        PackagedRecipeGraph disabled = PackagedRecipeGraph.load(root);
        assertFalse(disabled.producers.containsKey("code:lightwood:access:tau_ceti_f"));
        assertFalse(disabled.producers.containsKey("code:volcano"));
        Success result = assertInstanceOf(Success.class, RecipeGraphReachability.evaluate(disabled.nodes, disabled.roots,
                disabled.producerList(), RecipeGraphReachability.DEFAULT_LIMITS));
        assertTrue(result.unavailable().contains(id("lightwood_log")));
        assertTrue(result.unavailable().contains("fluid:" + id("enriched_lava")));
    }

    @Test
    void worldgenLootModelsRetainToolAndChanceRequirements() {
        Producer charred = graph.producers.get("code:charred_tree:access:venus");
        assertNotNull(charred);
        assertTrue(charred.slots().contains(List.of("tool:silk_touch")), "Ordinary chopping yields charcoal, not the log");
        assertTrue(graph.producers.get("code:lightwood_leaves").slots().contains(List.of("minecraft:shears")));
        assertEquals(List.of("block:" + id("lightwood_leaves")),
                graph.producers.get("code:lightwood_sapling").slots().get(0));
    }

    @Test
    void zeroChanceLootCannotSupplySaplings() throws IOException {
        Path root = fixture();
        Path loot = root.resolve("data/advancedrocketrycommunity/loot_tables/blocks/lightwood_leaves.json");
        var json = PackagedRecipeGraph.read(loot);
        json.getAsJsonArray("pools").get(0).getAsJsonObject().getAsJsonArray("entries").get(0)
                .getAsJsonObject().getAsJsonArray("children").get(1).getAsJsonObject()
                .getAsJsonArray("conditions").get(1).getAsJsonObject().addProperty("chance", 0);
        Files.writeString(loot, json.toString());
        PackagedRecipeGraph disabled = PackagedRecipeGraph.load(root);
        assertFalse(disabled.producers.containsKey("code:lightwood_sapling"));
        assertTrue(disabled.producers.containsKey("code:lightwood_leaves"));
    }

    private Path fixture() throws IOException {
        // Copy JSON data only, never assets or a server world. JUnit owns and removes this temporary fixture.
        try (var paths = Files.walk(graph.root.resolve("data"))) {
            for (Path path : paths.filter(file -> file.toString().endsWith(".json")).toList()) {
                Path destination = temporary.resolve(graph.root.relativize(path));
                Files.createDirectories(destination.getParent());
                Files.copy(path, destination);
            }
        }
        return temporary;
    }

    private static Success evaluate(Set<String> roots, List<Producer> producers) {
        return assertInstanceOf(Success.class, RecipeGraphReachability.evaluate(graph.nodes, roots, producers,
                RecipeGraphReachability.DEFAULT_LIMITS));
    }
}
