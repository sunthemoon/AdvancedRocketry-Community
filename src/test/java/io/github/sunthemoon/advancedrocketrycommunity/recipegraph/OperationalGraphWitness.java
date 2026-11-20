package io.github.sunthemoon.advancedrocketrycommunity.recipegraph;

import io.github.sunthemoon.advancedrocketrycommunity.recipegraph.RecipeGraphReachability.Producer;
import io.github.sunthemoon.advancedrocketrycommunity.recipegraph.RecipeGraphReachability.Success;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/** Deterministic diagnostics, including residual cycles (not harmless, seeded recipe cycles). */
final class OperationalGraphWitness {
    private OperationalGraphWitness() { }

    static Success evaluate(PackagedRecipeGraph graph) {
        var result = RecipeGraphReachability.evaluate(graph.nodes, graph.roots, graph.producerList(),
                RecipeGraphReachability.DEFAULT_LIMITS);
        if (!(result instanceof Success success)) { throw new IllegalStateException("Graph rejected: " + result); }
        return success;
    }

    /** Output -> unsatisfied AND slots -> all unavailable OR alternatives, across all producers. */
    static Map<String, Set<String>> residual(PackagedRecipeGraph graph, Success result) {
        Map<String, Set<String>> edges = new TreeMap<>();
        result.unavailable().forEach(node -> edges.put(node, new TreeSet<>()));
        for (Producer producer : graph.producerList()) {
            for (String output : producer.outputs()) {
                if (!edges.containsKey(output)) { continue; }
                for (List<String> slot : producer.slots()) {
                    if (slot.stream().noneMatch(result.available()::containsKey)) { edges.get(output).addAll(slot); }
                }
            }
        }
        return edges;
    }

    /** Structural dependency cycles include harmless item/tag/recycling loops with reachable escapes. */
    static Map<String, Set<String>> dependencies(PackagedRecipeGraph graph) {
        Map<String, Set<String>> edges = new TreeMap<>();
        graph.nodes.forEach(node -> edges.put(node, new TreeSet<>()));
        for (Producer producer : graph.producerList()) {
            for (String output : producer.outputs()) {
                producer.slots().forEach(slot -> edges.get(output).addAll(slot));
            }
        }
        return edges;
    }

    /** Iterative searches avoid recursion/stack limits; only the unavailable residual is searched. */
    static Set<String> cyclic(Map<String, Set<String>> residual) {
        Set<String> cyclic = new TreeSet<>();
        for (String origin : residual.keySet()) {
            Set<String> seen = new HashSet<>();
            var pending = new ArrayDeque<>(residual.get(origin));
            while (!pending.isEmpty()) {
                String node = pending.removeFirst();
                if (node.equals(origin)) { cyclic.add(origin); break; }
                if (seen.add(node)) { pending.addAll(residual.getOrDefault(node, Set.of())); }
            }
        }
        return cyclic;
    }

    /** One deterministic blocking path per unavailable node; the full hypergraph retains every alternative. */
    static List<String> blockedPath(String start, Map<String, Set<String>> edges) {
        List<String> path = new ArrayList<>();
        String node = start;
        while (!path.contains(node)) {
            path.add(node);
            var next = edges.getOrDefault(node, Set.of());
            if (next.isEmpty()) { return path; }
            node = new TreeSet<>(next).first();
        }
        path.add(node);
        return path;
    }

    static String render(OperationalTechGraph model) {
        var graph = model.graph;
        Success result = evaluate(graph);
        var residual = residual(graph, result);
        var cyclic = cyclic(residual);
        var dependencies = dependencies(graph);
        var structuralCycles = cyclic(dependencies);
        var missing = new TreeSet<>(model.required);
        missing.removeAll(result.available().keySet());
        StringBuilder out = new StringBuilder("W420 operational graph witness v1\n");
        out.append("Semantics: existential AND slots / OR alternatives; dynamic guards below remain unproved.\n")
                .append("Counts: nodes=").append(graph.nodes.size()).append(" producers=").append(graph.producers.size())
                .append(" roots=").append(graph.roots.size()).append(" reachable=").append(result.available().size())
                .append(" unavailable=").append(result.unavailable().size()).append(" required=").append(model.required.size())
                .append(" required-unreachable=").append(missing.size()).append(" residual-cyclic=").append(cyclic.size())
                .append(" structural-cyclic=").append(structuralCycles.size()).append('\n');
        out.append("\nNODES (ROOT, REQUIRED, or auxiliary)\n");
        for (String node : new TreeSet<>(graph.nodes)) {
            out.append(node).append(graph.roots.contains(node) ? " ROOT" : model.required.contains(node) ? " REQUIRED" : " auxiliary").append('\n');
        }
        out.append("\nEDGES (producer: AND [OR alternatives] -> outputs; source)\n");
        for (var entry : new TreeMap<>(graph.producers).entrySet()) {
            Producer p = entry.getValue();
            out.append(p.id()).append(": ").append(p.slots()).append(" -> ").append(p.outputs())
                    .append("; ").append(graph.codeSources.getOrDefault(p.id(), "PackagedRecipeGraph; packaged " + p.id())).append('\n');
        }
        out.append("\nREACHABLE SET / EARLIEST WITNESSES (each member recursively has a lower round)\n");
        result.available().forEach((node, witness) -> out.append(node).append(" round=").append(witness.round())
                .append(" via=").append(witness.producer()).append(" prerequisites=").append(witness.slotMembers()).append('\n'));
        out.append("\nUNREACHABLE / BLOCKING PATHS (full alternatives are in EDGES)\n");
        result.unavailable().forEach(node -> out.append(node).append(model.required.contains(node) ? " FINDING" : " optional compatibility input")
                .append(" path=").append(blockedPath(node, residual)).append('\n'));
        out.append("\nRESIDUAL CYCLIC NODES (union-of-blockers cycles; not an SCC-based claim of exclusive causation)\n");
        cyclic.forEach(node -> out.append(node).append(" path=").append(cyclePath(node, residual)).append('\n'));
        out.append("\nALL STRUCTURALLY CYCLIC NODES (union of alternatives, not necessarily a hard lock)\n");
        structuralCycles.forEach(node -> out.append(node).append(result.available().containsKey(node) ? " reachable" : " unavailable")
                .append(" cycle=").append(cyclePath(node, dependencies)).append('\n'));
        out.append("\nDISPLAY PARENTS (not gameplay prerequisite edges)\n");
        model.displayParents.forEach((child, parent) -> out.append(child).append(" -> ").append(parent).append('\n'));
        out.append("\nUNMODELLED PRECONDITIONS / LIMITS\n");
        model.unmodelled.forEach((key, reason) -> out.append(key).append(": ").append(reason).append('\n'));
        return out.toString();
    }

    static List<String> cyclePath(String start, Map<String, Set<String>> edges) {
        var pending = new ArrayDeque<List<String>>();
        pending.add(List.of(start));
        Set<String> seen = new HashSet<>();
        while (!pending.isEmpty()) {
            var path = pending.removeFirst();
            String last = path.get(path.size() - 1);
            if (!seen.add(last)) { continue; }
            for (String next : new TreeSet<>(edges.getOrDefault(last, Set.of()))) {
                var extended = new ArrayList<>(path); extended.add(next);
                if (next.equals(start)) { return extended; }
                pending.add(extended);
            }
        }
        return List.of();
    }
}
