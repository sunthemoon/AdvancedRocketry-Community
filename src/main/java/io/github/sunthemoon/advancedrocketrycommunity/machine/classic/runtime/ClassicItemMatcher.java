package io.github.sunthemoon.advancedrocketrycommunity.machine.classic.runtime;

import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/** Bounded integral flow avoids greedy false negatives for overlapping tag ingredients. */
public final class ClassicItemMatcher {
    public record Supply(String id, int count) {
        public Supply { if (count < 0 || count > 64) { throw new IllegalArgumentException("Slot count"); } }
    }
    public record Demand(Set<String> alternatives, int count) {
        public Demand {
            alternatives = Set.copyOf(alternatives);
            if (alternatives.isEmpty() || alternatives.size() > 32 || count < 1 || count > 64) {
                throw new IllegalArgumentException("Ingredient bound");
            }
        }
    }
    public static Optional<int[]> consumed(List<Supply> supply, List<Demand> demand) {
        if (supply.size() > 256 || demand.size() > 4) { throw new IllegalArgumentException("Match bound"); }
        int source = 0, rowBase = 1, slotBase = rowBase + demand.size(), sink = slotBase + supply.size();
        int[][] residual = new int[sink + 1][sink + 1]; int required = 0;
        for (int row = 0; row < demand.size(); row++) {
            Demand wanted = demand.get(row); residual[source][rowBase + row] = wanted.count(); required += wanted.count();
            for (int slot = 0; slot < supply.size(); slot++) {
                if (wanted.alternatives().contains(supply.get(slot).id())) { residual[rowBase + row][slotBase + slot] = 64; }
            }
        }
        for (int slot = 0; slot < supply.size(); slot++) { residual[slotBase + slot][sink] = supply.get(slot).count(); }
        int flow = 0;
        while (flow < required) {
            int[] parent = new int[sink + 1]; Arrays.fill(parent, -1); parent[source] = source;
            var pending = new ArrayDeque<Integer>(); pending.add(source);
            while (!pending.isEmpty() && parent[sink] < 0) {
                int node = pending.removeFirst();
                for (int next = 0; next <= sink; next++) {
                    if (parent[next] < 0 && residual[node][next] > 0) { parent[next] = node; pending.addLast(next); }
                }
            }
            if (parent[sink] < 0) { return Optional.empty(); }
            int amount = required - flow;
            for (int node = sink; node != source; node = parent[node]) { amount = Math.min(amount, residual[parent[node]][node]); }
            for (int node = sink; node != source; node = parent[node]) {
                residual[parent[node]][node] -= amount; residual[node][parent[node]] += amount;
            }
            flow += amount;
        }
        int[] consumed = new int[supply.size()];
        for (int slot = 0; slot < supply.size(); slot++) { consumed[slot] = supply.get(slot).count() - residual[slotBase + slot][sink]; }
        return Optional.of(consumed);
    }
    private ClassicItemMatcher() { }
}
