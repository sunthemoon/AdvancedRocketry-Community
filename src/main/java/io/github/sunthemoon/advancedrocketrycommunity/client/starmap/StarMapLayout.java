package io.github.sunthemoon.advancedrocketrycommunity.client.starmap;

import io.github.sunthemoon.advancedrocketrycommunity.celestial.network.CelestialSnapshot;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.resources.ResourceLocation;

/** Deterministic schematic tree, not a simulation of orbital distance or position. */
public record StarMapLayout(List<Node> nodes, double maxX, double maxY) {
    public static final int MAX_NODES = 128;

    public StarMapLayout { nodes = List.copyOf(nodes); }

    public static StarMapLayout create(CelestialSnapshot snapshot) {
        if (snapshot.entries().size() > MAX_NODES) { throw new IllegalArgumentException("Too many map nodes"); }
        var entries = snapshot.entries().stream().sorted(java.util.Comparator.comparing(e -> e.bodyId().toString())).toList();
        var byId = new HashMap<ResourceLocation, CelestialSnapshot.Entry>();
        var children = new HashMap<ResourceLocation, List<ResourceLocation>>();
        for (var entry : entries) {
            if (byId.put(entry.bodyId(), entry) != null) { throw new IllegalArgumentException("Duplicate map node"); }
        }
        for (var entry : entries) {
            entry.parentId().ifPresent(parent -> {
                if (!byId.containsKey(parent)) { throw new IllegalArgumentException("Missing map parent"); }
                children.computeIfAbsent(parent, key -> new ArrayList<>()).add(entry.bodyId());
            });
        }
        var placed = new HashMap<ResourceLocation, Node>();
        int[] row = {0};
        for (var entry : entries) {
            if (entry.parentId().isEmpty()) { place(entry.bodyId(), null, 0, children, placed, row); }
        }
        if (placed.size() != entries.size()) { throw new IllegalArgumentException("Cyclic map hierarchy"); }
        var nodes = entries.stream().map(entry -> placed.get(entry.bodyId())).toList();
        return new StarMapLayout(nodes, nodes.stream().mapToDouble(Node::x).max().orElse(0),
                nodes.stream().mapToDouble(Node::y).max().orElse(0));
    }

    private static double place(ResourceLocation id, ResourceLocation parent, int depth,
            Map<ResourceLocation, List<ResourceLocation>> children, Map<ResourceLocation, Node> placed, int[] row) {
        if (depth >= MAX_NODES) { throw new IllegalArgumentException("Excessive map depth"); }
        var descendants = children.getOrDefault(id, List.of());
        double y;
        if (descendants.isEmpty()) {
            y = row[0]++ * 50.0;
        } else {
            double sum = 0;
            for (var child : descendants) { sum += place(child, id, depth + 1, children, placed, row); }
            y = sum / descendants.size();
        }
        placed.put(id, new Node(id, parent, depth * 110.0, y));
        return y;
    }

    public record Node(ResourceLocation id, ResourceLocation parent, double x, double y) { }
}
