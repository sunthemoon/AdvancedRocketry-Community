package io.github.sunthemoon.advancedrocketrycommunity.machine.classic.runtime;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.BiPredicate;

/** Pure fair, resumable lookup. Results are published only after every candidate was checked. */
public final class ClassicRecipeSearch<K, R> {
    public static final int MAX_RECIPES = 1_024;
    public static final int PER_CONTROLLER = 32;
    public static final int PER_LEVEL = 256;
    private static final int MAX_CONTROLLERS = 16_384;
    private final ArrayDeque<K> queue = new ArrayDeque<>();
    private final Map<K, Cursor<R>> cursors = new HashMap<>();

    public boolean enqueue(K key, List<R> ordered) {
        if (ordered.size() > MAX_RECIPES) { throw new IllegalArgumentException("Recipe catalog exceeds 1024"); }
        if (cursors.containsKey(key)) { return true; }
        if (cursors.size() == MAX_CONTROLLERS) { return false; }
        cursors.put(key, new Cursor<>(List.copyOf(ordered))); queue.addLast(key); return true;
    }

    public void cancel(K key) { cursors.remove(key); queue.remove(key); }
    public void clear() { cursors.clear(); queue.clear(); }
    public boolean pending(K key) { return cursors.containsKey(key); }

    public int tick(BiPredicate<K, R> matches, BiConsumer<K, Optional<R>> complete) {
        int remaining = PER_LEVEL, visits = queue.size(), checks = 0;
        while (visits-- > 0 && remaining > 0 && !queue.isEmpty()) {
            K key = queue.removeFirst(); Cursor<R> cursor = cursors.get(key);
            if (cursor == null) { continue; }
            int budget = Math.min(PER_CONTROLLER, remaining);
            while (budget-- > 0 && cursor.index < cursor.recipes.size()) {
                R recipe = cursor.recipes.get(cursor.index++);
                boolean matched = matches.test(key, recipe);
                if (matched && cursor.first == null) { cursor.first = recipe; }
                remaining--; checks++;
            }
            if (cursor.index == cursor.recipes.size()) {
                cursors.remove(key); complete.accept(key, Optional.ofNullable(cursor.first));
            } else { queue.addLast(key); }
        }
        return checks;
    }

    private static final class Cursor<R> {
        final List<R> recipes; int index; R first;
        Cursor(List<R> recipes) { this.recipes = recipes; }
    }
}
