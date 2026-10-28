package io.github.sunthemoon.advancedrocketrycommunity.client.compat.jei;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Predicate;

/**
 * Holds recipes that name an item tag back from JEI until the client has this connection's tags (W188).
 *
 * <p>Machine recipes sync their authored ingredient JSON, so the client expands tags itself. A joining player receives
 * the recipes before the tags, and JEI 15.56 registers on the recipe update, so a tag recipe registered then has no
 * items. Tags bound before the connection (single-player, an earlier server) are not this server's. Held recipes go
 * to JEI's runtime once the tags arrive. Render thread only; free of JEI types so that it can be tested without JEI.
 *
 * @param <M> what released recipes are added to: JEI's runtime recipe manager
 */
final class ServerTagGate<M> {
    private final Map<String, Held<M, ?>> held = new LinkedHashMap<>();
    private boolean tagsArrived;

    /** A connection began or ended: tags bound now are not the next server's. */
    void connectionChanged() {
        tagsArrived = false;
        held.clear();
    }

    /** JEI stopped; its next start registers every recipe again. */
    void runtimeStopped() {
        held.clear();
    }

    /** This connection's tags arrived. */
    void tagsArrived() {
        tagsArrived = true;
    }

    int held(String category) {
        Held<M, ?> entry = held.get(category);
        return entry == null ? 0 : entry.recipes().size();
    }

    /**
     * Returns the recipes JEI may register now, after {@code shown}. Until this connection's tags arrive, a recipe that
     * names a tag is held instead and goes to {@code add} on {@link #release}. Replaces what the category held before.
     */
    <T> List<T> admit(String category, List<T> recipes, Predicate<T> namesTag, Predicate<T> shown,
            BiConsumer<M, List<T>> add) {
        List<T> now = new ArrayList<>();
        List<T> later = new ArrayList<>();
        for (T recipe : recipes) {
            if (!tagsArrived && namesTag.test(recipe)) {
                later.add(recipe);
            } else if (shown.test(recipe)) {
                now.add(recipe);
            }
        }
        held.remove(category);
        if (!later.isEmpty()) {
            held.put(category, new Held<>(List.copyOf(later), shown, add, now.size()));
        }
        return List.copyOf(now);
    }

    /**
     * Once the tags have arrived, adds each category's held recipes that {@code shown} accepts to {@code target}, once,
     * and returns each such category's new total. Does nothing before then.
     */
    List<Released> release(M target) {
        if (!tagsArrived || held.isEmpty()) {
            return List.of();
        }
        // Cleared first, so a failure inside JEI can never add the same recipe twice.
        Map<String, Held<M, ?>> releasing = new LinkedHashMap<>(held);
        held.clear();
        List<Released> released = new ArrayList<>(releasing.size());
        releasing.forEach((category, entry) -> released.add(new Released(category, entry.addTo(target))));
        return List.copyOf(released);
    }

    /** A category's total in JEI after its held recipes were added. */
    record Released(String category, int total) {
    }

    private record Held<M, T>(List<T> recipes, Predicate<T> shown, BiConsumer<M, List<T>> add, int registered) {
        int addTo(M target) {
            List<T> ready = recipes.stream().filter(shown).toList();
            if (!ready.isEmpty()) {
                add.accept(target, ready);
            }
            return registered + ready.size();
        }
    }
}
