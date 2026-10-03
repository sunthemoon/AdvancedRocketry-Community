package io.github.sunthemoon.advancedrocketrycommunity.celestial.exoplanet.worldgen;

import java.util.ArrayDeque;
import java.util.Collection;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

/**
 * A planned tree as offsets from its origin: log cells and leaf cells with their leaf distance (vanilla's
 * {@code distance} property, 1–6). Leaves farther than six steps from any log through other leaves would decay, so the
 * plan leaves them out.
 */
public record TreePlan(Set<BlockPos> logs, Map<BlockPos, Integer> leaves) {
    public static final int MAX_LEAF_DISTANCE = 6;

    public TreePlan {
        logs = Set.copyOf(logs);
        leaves = Map.copyOf(leaves);
    }

    /** Leaf distances from the logs through the candidate cells (six-neighbour steps), keeping 1 to 6. */
    public static TreePlan of(Set<BlockPos> logs, Collection<BlockPos> leafCandidates) {
        Set<BlockPos> candidates = new HashSet<>(leafCandidates);
        candidates.removeAll(logs);
        Map<BlockPos, Integer> distances = new HashMap<>();
        Deque<BlockPos> queue = new ArrayDeque<>();
        for (BlockPos log : logs) {
            for (Direction direction : Direction.values()) {
                BlockPos next = log.relative(direction);
                if (candidates.contains(next) && !distances.containsKey(next)) {
                    distances.put(next, 1);
                    queue.add(next);
                }
            }
        }
        while (!queue.isEmpty()) {
            BlockPos cell = queue.poll();
            int distance = distances.get(cell);
            if (distance >= MAX_LEAF_DISTANCE) {
                continue;
            }
            for (Direction direction : Direction.values()) {
                BlockPos next = cell.relative(direction);
                if (candidates.contains(next) && !distances.containsKey(next)) {
                    distances.put(next, distance + 1);
                    queue.add(next);
                }
            }
        }
        return new TreePlan(logs, distances);
    }

    /** The largest horizontal offset of any cell from the origin, along either axis. */
    public int reach() {
        int reach = 0;
        for (BlockPos log : logs) {
            reach = Math.max(reach, Math.max(Math.abs(log.getX()), Math.abs(log.getZ())));
        }
        for (BlockPos leaf : leaves.keySet()) {
            reach = Math.max(reach, Math.max(Math.abs(leaf.getX()), Math.abs(leaf.getZ())));
        }
        return reach;
    }
}
