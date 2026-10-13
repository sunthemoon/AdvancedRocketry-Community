package io.github.sunthemoon.advancedrocketrycommunity.machine.classic.runtime;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.function.Predicate;

/** Server-wide, deduplicated Level FIFO. A paid entry consumes both budgets. */
public final class ResearchWorkQueue<L, T> {
    public static final int MAX_ENTRIES = 16_384, PAID_BUDGET = 32, INSPECTION_BUDGET = 64;
    private final Map<L, LinkedHashSet<T>> queues = new LinkedHashMap<>();
    private int size, first;
    public boolean enqueue(L level, T entry) {
        var queue = queues.get(level);
        if (queue != null && queue.contains(entry)) { return true; }
        if (size == MAX_ENTRIES) { return false; }
        queues.computeIfAbsent(level, ignored -> new LinkedHashSet<>()).add(entry); size++; return true;
    }
    public boolean pending(L level, T entry) { return queues.containsKey(level) && queues.get(level).contains(entry); }
    public void remove(L level, T entry) {
        var queue = queues.get(level); if (queue != null && queue.remove(entry)) { size--; }
        if (queue != null && queue.isEmpty()) { queues.remove(level); }
    }
    public void unload(L level) { var queue = queues.remove(level); if (queue != null) { size -= queue.size(); } }
    public record Pass(int inspected, int paid, int remaining) { }
    public Pass tick(Predicate<T> advance) {
        var levels = new ArrayList<>(queues.keySet());
        int inspected = 0, paid = 0;
        if (levels.isEmpty()) { return new Pass(0, 0, 0); }
        int cursor = Math.floorMod(first++, levels.size()), empty = 0;
        while (inspected < INSPECTION_BUDGET && paid < PAID_BUDGET && empty < levels.size()) {
            L level = levels.get(cursor); cursor = (cursor + 1) % levels.size();
            var queue = queues.get(level);
            if (queue == null || queue.isEmpty()) { empty++; continue; }
            empty = 0; var iterator = queue.iterator(); T entry = iterator.next(); iterator.remove(); size--;
            inspected++; if (advance.test(entry)) { paid++; }
        }
        queues.entrySet().removeIf(entry -> entry.getValue().isEmpty());
        return new Pass(inspected, paid, size);
    }
    public void clear() { queues.clear(); size = 0; first = 0; }
}
