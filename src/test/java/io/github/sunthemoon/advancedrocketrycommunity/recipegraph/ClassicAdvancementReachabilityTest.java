package io.github.sunthemoon.advancedrocketrycommunity.recipegraph;

import static io.github.sunthemoon.advancedrocketrycommunity.recipegraph.PackagedRecipeGraph.ENERGY;
import static io.github.sunthemoon.advancedrocketrycommunity.recipegraph.PackagedRecipeGraph.id;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.sunthemoon.advancedrocketrycommunity.progression.classic.ClassicInventoryAdvancements.Goal;
import io.github.sunthemoon.advancedrocketrycommunity.progression.classic.ClassicMilestoneAdvancements.Kind;
import io.github.sunthemoon.advancedrocketrycommunity.progression.classic.ClassicMilestoneAdvancements.Milestone;
import io.github.sunthemoon.advancedrocketrycommunity.recipegraph.RecipeGraphReachability.Available;
import io.github.sunthemoon.advancedrocketrycommunity.recipegraph.RecipeGraphReachability.Evaluation;
import io.github.sunthemoon.advancedrocketrycommunity.recipegraph.RecipeGraphReachability.Producer;
import io.github.sunthemoon.advancedrocketrycommunity.recipegraph.RecipeGraphReachability.Success;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * ADR-066 §6.3 rule for the classic advancements: each one's survival prerequisites are reachable from a fresh
 * Overworld through the packaged recipe graph, machine patterns, ore loot and rocket access, without commands or
 * other mods. The minimal witness route of every advancement is written to
 * {@code build/reports/classic-advancement-reachability.md}.
 */
class ClassicAdvancementReachabilityTest {
    /** Advancements that cannot be reached in this tree yet, each with the missing content that blocks it. */
    static final Map<String, String> AWAITING = Map.of(
            "crystallizer", "the C16c crystallizer is not merged (W10); the criterion is minecraft:impossible",
            "dilithium", "nothing produces a dilithium crystal until the C16c crystallizer recipes merge (W10)",
            "one_small_step", "the D3 world-first Moon landing source outbox must join the journal-3 migration;"
                    + " the criterion is minecraft:impossible",
            "first_station_warp", "the D3 world-first warp source outbox must join W07's station registry 5"
                    + " migration; the criterion is minecraft:impossible");

    private static PackagedRecipeGraph graph;
    private static Success result;
    private static Map<String, String> goals;

    @BeforeAll
    static void evaluate() throws IOException {
        graph = PackagedRecipeGraph.load();
        goals = goals(graph);
        Evaluation evaluation = RecipeGraphReachability.evaluate(graph.nodes, graph.roots, graph.producerList(),
                RecipeGraphReachability.DEFAULT_LIMITS);
        result = assertInstanceOf(Success.class, evaluation, () -> "Graph rejected: " + evaluation);
        writeReport();
    }

    /** The goal node of each advancement; inventory goals are their items, events their prerequisite node. */
    private static Map<String, String> goals(PackagedRecipeGraph graph) {
        Map<String, String> goals = new LinkedHashMap<>();
        for (Goal goal : Goal.values()) {
            if (goal.items().size() == 1) {
                goals.put(goal.suffix(), goal.items().get(0));
            } else {
                // The suit: all four pieces together.
                graph.produce("goal", goal.items().stream().map(List::of).toList(), List.of("goal:" + goal.suffix()));
                goals.put(goal.suffix(), "goal:" + goal.suffix());
            }
        }
        List<String> formed = PackagedRecipeGraph.CLASSIC_MACHINES.stream().map(machine -> "machine:" + id(machine))
                .toList();
        graph.produce("event", List.of(formed), List.of("event:formed_multiblock"));
        graph.produce("event", List.of(List.of(id("rocket_seat")), List.of("minecraft:tnt")),
                List.of("event:seat_on_tnt"));
        graph.produce("event", List.of(List.of("access:moon")), List.of("event:moon_landing"));
        graph.produce("event", List.of(List.of("access:space"), List.of(id("station_deployment_kit")),
                List.of(id("warp_core")), List.of(ENERGY)), List.of("event:station_warp"));
        for (Milestone milestone : Milestone.values()) {
            goals.put(milestone.suffix(), switch (milestone.kind()) {
                case INVENTORY -> milestone.target();
                case EVENT -> "event:" + milestone.target();
                case ARRIVAL -> "access:" + milestone.target().substring(milestone.target().indexOf(':') + 1);
                case PENDING -> null;
            });
        }
        goals.values().stream().filter(java.util.Objects::nonNull).forEach(graph::add);
        return goals;
    }

    @Test
    void everyAdvancementOutsideTheAwaitingListIsReachableInSurvival() {
        assertEquals(17, goals.size());
        List<String> unreachable = new ArrayList<>();
        goals.forEach((suffix, goal) -> {
            if (!AWAITING.containsKey(suffix) && (goal == null || !result.available().containsKey(goal))) {
                unreachable.add(suffix + " -> " + goal);
            }
        });
        assertEquals(List.of(), unreachable, "Unreachable classic advancements");
    }

    @Test
    void awaitingEntriesAreExactlyThePendingCriteriaAndTheirMissingContent() {
        Set<String> pending = java.util.Arrays.stream(Milestone.values()).filter(m -> m.kind() == Kind.PENDING)
                .map(Milestone::suffix).collect(Collectors.toSet());
        assertEquals(Set.of("crystallizer", "one_small_step", "first_station_warp"), pending);
        assertTrue(AWAITING.keySet().containsAll(pending), "A pending criterion lacks its reason");
        Set<String> content = new HashSet<>(AWAITING.keySet());
        content.removeAll(pending);
        assertEquals(Set.of("dilithium"), content);
        // While the crystallizer is absent nothing in the packaged data produces the crystal.
        assertTrue(graph.producers.values().stream().noneMatch(producer -> producer.outputs()
                        .contains(Milestone.DILITHIUM.target()) && producer.id().startsWith("recipe:")),
                "A dilithium crystal recipe exists; remove dilithium from AWAITING");
    }

    @Test
    void survivalRootsAreTheOverworldVanillaAndNoNonSurvivalInput() {
        assertEquals(Set.of(), graph.nonSurvivalInputs, "Recipes use items that survival cannot obtain");
        assertTrue(graph.roots.stream().allMatch(root -> root.startsWith("minecraft:") || root.startsWith("#minecraft:")
                        || root.startsWith("#block:minecraft:") || root.startsWith("fluid:minecraft:")
                        || root.equals(PackagedRecipeGraph.OVERWORLD)),
                "A root is not a vanilla survival assumption: " + graph.roots);
        assertTrue(result.available().containsKey(ENERGY), "No survival FE source");
        assertTrue(result.available().containsKey("access:moon") && result.available().containsKey("access:space"),
                "No survival rocket access to the Moon and Space");
        for (String machine : PackagedRecipeGraph.CLASSIC_MACHINES) {
            assertTrue(result.available().containsKey("machine:" + id(machine)), "Unformable " + machine);
        }
    }

    private static void writeReport() throws IOException {
        StringBuilder report = new StringBuilder("# Classic advancement reachability (ADR-066 section 6.3)\n\n");
        report.append("Generated by ClassicAdvancementReachabilityTest from the packaged resources. Each route is the ")
                .append("evaluator's first-round witness: every ingredient is listed once, at its earliest round.\n");
        Map<String, Producer> producers = new TreeMap<>(graph.producers);
        goals.forEach((suffix, goal) -> {
            report.append("\n## classic/").append(suffix).append("\n\n");
            if (AWAITING.containsKey(suffix)) {
                report.append("Awaiting content: ").append(AWAITING.get(suffix)).append(".\n");
                return;
            }
            Set<String> printed = new HashSet<>();
            route(goal, 0, producers, printed, report);
        });
        Path file = Path.of("build/reports/classic-advancement-reachability.md");
        Files.createDirectories(file.getParent());
        Files.writeString(file, report.toString());
    }

    private static void route(String node, int depth, Map<String, Producer> producers, Set<String> printed,
                              StringBuilder report) {
        Available available = result.available().get(node);
        report.append("  ".repeat(depth)).append("- ").append(node);
        if (available == null) {
            report.append(" (unreachable)\n");
            return;
        }
        if (available.producer() == null) {
            report.append(" (root)\n");
            return;
        }
        if (!printed.add(node)) {
            report.append(" (above)\n");
            return;
        }
        report.append(" <- ").append(available.producer()).append(" (round ").append(available.round()).append(")\n");
        for (String member : available.slotMembers()) {
            route(member, depth + 1, producers, printed, report);
        }
    }
}
