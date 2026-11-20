package io.github.sunthemoon.advancedrocketrycommunity.recipegraph;

import static org.junit.jupiter.api.Assertions.*;

import io.github.sunthemoon.advancedrocketrycommunity.recipegraph.RecipeGraphReachability.Producer;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** Pure JSON/source analysis: does not load registries, start a world, or change product data. */
class OperationalTechGraphTest {
    private static OperationalTechGraph model;

    @BeforeAll static void load() throws IOException { model = OperationalTechGraph.load(); }

    @Test void writesDeterministicWitnessReport() throws Exception {
        String raw = OperationalGraphWitness.render(model);
        assertEquals(raw, OperationalGraphWitness.render(OperationalTechGraph.load()));
        var producers = new ArrayList<>(model.graph.producerList());
        Collections.reverse(producers);
        assertEquals(OperationalGraphWitness.evaluate(model.graph), RecipeGraphReachability.evaluate(
                model.graph.nodes, model.graph.roots, producers, RecipeGraphReachability.DEFAULT_LIMITS));
        Path report = Path.of("build/reports/w420-operational-graph.txt");
        Files.createDirectories(report.getParent());
        Files.writeString(report, raw, StandardCharsets.UTF_8);
        String hash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(raw.getBytes(StandardCharsets.UTF_8)));
        Files.writeString(Path.of("build/reports/w420-operational-graph.sha256"), hash + "\n", StandardCharsets.US_ASCII);
        // The checked-in report is an archive, not a pointer to build/. On first generation it is absent.
        Path archive = Path.of("docs/releases/v1.8.0/RECIPE-GRAPH-REPORT.md");
        if (Files.exists(archive)) {
            String stored = Files.readString(archive).replace("\r\n", "\n");
            assertTrue(stored.contains("Raw report SHA-256: `" + hash + "`"));
            assertTrue(stored.endsWith("```text\n" + raw + "```\n"), "Archive differs; preserve old evidence and record a new analysis, never silently refresh it");
        }
    }

    @Test void everyRequiredOperationIsConditionallyReachableAndNoNewExceptionIsAllowed() {
        var result = OperationalGraphWitness.evaluate(model.graph);
        var missing = new TreeSet<>(model.required); missing.removeAll(result.available().keySet());
        assertEquals(Set.of(), missing, () -> "Required findings: " + missing + "\n" + OperationalGraphWitness.render(model));
        assertEquals(PackagedRecipeGraph.OPTIONAL_CROSS_MOD_INPUTS, result.unavailable());
        assertTrue(OperationalGraphWitness.cyclic(OperationalGraphWitness.residual(model.graph, result)).isEmpty());
        assertTrue(Collections.disjoint(model.required, model.graph.roots));
    }

    @Test void eachOperationalProducerNeedsEveryAndSlotAndHasALineCitation() {
        model.graph.producerList().stream().filter(p -> p.id().startsWith("operational:")).forEach(producer -> {
            assertTrue(model.graph.codeSources.get(producer.id()).matches("(?s).*\\.java:[0-9]+.*"), producer.id());
            Set<String> nodes = new HashSet<>(producer.outputs());
            Set<String> roots = new HashSet<>();
            producer.slots().forEach(slot -> { nodes.addAll(slot); roots.addAll(slot); });
            var success = assertInstanceOf(RecipeGraphReachability.Success.class, RecipeGraphReachability.evaluate(
                    nodes, roots, List.of(producer), RecipeGraphReachability.DEFAULT_LIMITS));
            assertTrue(success.available().keySet().containsAll(producer.outputs()));
            for (var slot : producer.slots()) {
                var missing = new HashSet<>(roots); missing.removeAll(slot);
                var result = assertInstanceOf(RecipeGraphReachability.Success.class, RecipeGraphReachability.evaluate(
                        nodes, missing, List.of(producer), RecipeGraphReachability.DEFAULT_LIMITS));
                assertTrue(result.unavailable().containsAll(producer.outputs()), producer.id() + " without " + slot);
            }
        });
    }

    @Test void acquiringControllersCannotBypassGroundAnalysisResearchOrStationRequirements() {
        var pruned = model.graph.producerList().stream().filter(p -> !p.id().startsWith("operational:")).toList();
        var result = assertInstanceOf(RecipeGraphReachability.Success.class, RecipeGraphReachability.evaluate(
                model.graph.nodes, model.graph.roots, pruned, RecipeGraphReachability.DEFAULT_LIMITS));
        assertTrue(result.unavailable().containsAll(model.required));
        assertTrue(result.available().containsKey(PackagedRecipeGraph.id("observatory")));
        assertTrue(result.available().containsKey(PackagedRecipeGraph.id("black_hole_generator")));
    }

    @Test void groundAndSatelliteResearchHaveIndependentThresholdWitnesses() {
        for (String removed : List.of("operational:op:data:", "operational:op:ground:")) {
            var producers = model.graph.producerList().stream().filter(p -> !p.id().startsWith(removed)).toList();
            var result = assertInstanceOf(RecipeGraphReachability.Success.class, RecipeGraphReachability.evaluate(
                    model.graph.nodes, model.graph.roots, producers, RecipeGraphReachability.DEFAULT_LIMITS));
            for (String threshold : List.of("0", "120", "240", "360")) {
                assertTrue(result.available().containsKey("op:research:lifetime:" + threshold), removed + " threshold " + threshold);
            }
        }
    }

    @Test void operationInventoryAndTreeCoverageAreExplicit() {
        assertTrue(model.required.containsAll(Set.of("op:laser:logical", "op:laser:physical", "op:railgun:local",
                "op:railgun:orbital", "op:gravity:active", "op:elevator:bound", "op:elevator:cargo", "op:elevator:ride",
                "op:solar:single", "op:solar:array", "op:beacon:active", "op:black-hole:generate:advancedrocketrycommunity:cygnus_x1")));
        assertEquals(18, model.required.stream().filter(node -> node.startsWith("op:tree:")).count()); // 17 criteria + TNT adapter
        assertEquals(16, model.displayParents.size());
        assertTrue(model.required.containsAll(Set.of("op:research:lifetime:0", "op:research:lifetime:120",
                "op:research:lifetime:240", "op:research:lifetime:360")));
        assertEquals(Set.of("base-model", "endgame", "elevator", "ground", "research-tree", "satellites", "solar/beacon"), model.unmodelled.keySet());
    }

    @Test void unseededCycleHasEveryNodeAndAnActualClosedPathButSeededOrAlternativeCyclesAreNotHardLocks() throws IOException {
        var graph = PackagedRecipeGraph.load();
        graph.produce("fixture:a", List.of(List.of("fixture:b")), List.of("fixture:a"));
        graph.produce("fixture:b", List.of(List.of("fixture:a")), List.of("fixture:b"));
        var result = OperationalGraphWitness.evaluate(graph);
        var residual = OperationalGraphWitness.residual(graph, result);
        assertEquals(Set.of("fixture:a", "fixture:b"), OperationalGraphWitness.cyclic(residual));
        assertEquals(List.of("fixture:a", "fixture:b", "fixture:a"), OperationalGraphWitness.cyclePath("fixture:a", residual));
        assertEquals(List.of("fixture:b", "fixture:a", "fixture:b"), OperationalGraphWitness.blockedPath("fixture:b", residual));
        graph.roots.add("fixture:a");
        assertTrue(OperationalGraphWitness.cyclic(OperationalGraphWitness.residual(graph, OperationalGraphWitness.evaluate(graph))).isEmpty());
        assertTrue(OperationalGraphWitness.cyclic(OperationalGraphWitness.dependencies(graph)).containsAll(Set.of("fixture:a", "fixture:b")));
        graph.roots.remove("fixture:a");
        graph.produce("fixture:escape", List.of(List.of(PackagedRecipeGraph.OVERWORLD)), List.of("fixture:b"));
        assertTrue(OperationalGraphWitness.cyclic(OperationalGraphWitness.residual(graph, OperationalGraphWitness.evaluate(graph))).isEmpty());
    }

    @Test void blockedAndRequirementWithOrAlternativeIsNotMisreportedAsReachable() throws IOException {
        var graph = PackagedRecipeGraph.load();
        graph.produce("fixture:and", List.of(List.of("fixture:missing"), List.of("fixture:other", PackagedRecipeGraph.OVERWORLD)),
                List.of("fixture:output"));
        var result = OperationalGraphWitness.evaluate(graph);
        assertFalse(result.available().containsKey("fixture:output"));
        var residual = OperationalGraphWitness.residual(graph, result);
        assertEquals(Set.of("fixture:missing"), residual.get("fixture:output"));
        assertEquals(List.of("fixture:output", "fixture:missing"), OperationalGraphWitness.blockedPath("fixture:output", residual));
        assertTrue(OperationalGraphWitness.cyclic(residual).isEmpty());
    }
}
