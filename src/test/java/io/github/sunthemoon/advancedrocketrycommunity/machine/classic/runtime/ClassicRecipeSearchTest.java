package io.github.sunthemoon.advancedrocketrycommunity.machine.classic.runtime;

import static org.junit.jupiter.api.Assertions.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class ClassicRecipeSearchTest {
    @Test void selectionWaitsForTheCompleteCandidateSet() {
        var queue = new ClassicRecipeSearch<String, Integer>(); var results = new ArrayList<Optional<Integer>>();
        queue.enqueue("one", IntStream.range(0, 65).boxed().toList());
        assertEquals(32, queue.tick((key, recipe) -> recipe == 1 || recipe == 64, (key, recipe) -> results.add(recipe)));
        assertTrue(results.isEmpty()); assertEquals(32, queue.tick((key, recipe) -> recipe == 1 || recipe == 64, (key, recipe) -> results.add(recipe)));
        assertTrue(results.isEmpty()); assertEquals(1, queue.tick((key, recipe) -> recipe == 64, (key, recipe) -> results.add(recipe)));
        assertEquals(List.of(Optional.of(1)), results);
    }
    @Test void levelAndControllerBudgetsAreIndependentAndFair() {
        var queue = new ClassicRecipeSearch<Integer, Integer>(); var visited = new ArrayList<Integer>();
        var catalog = IntStream.range(0, 1_024).boxed().toList();
        for (int key = 0; key < 10; key++) { queue.enqueue(key, catalog); }
        assertEquals(256, queue.tick((key, recipe) -> { visited.add(key); return false; }, (key, recipe) -> fail("Early completion")));
        assertEquals(8, visited.stream().distinct().count());
        for (int key = 0; key < 8; key++) { int target = key; assertEquals(32, visited.stream().filter(value -> value == target).count()); }
        visited.clear(); queue.tick((key, recipe) -> { visited.add(key); return false; }, (key, recipe) -> fail());
        assertEquals(8, visited.get(0)); assertEquals(9, visited.get(32));
    }
    @Test void cancelAndReloadCannotPublishOldCandidates() {
        var queue = new ClassicRecipeSearch<String, Integer>();
        queue.enqueue("one", IntStream.range(0, 64).boxed().toList()); queue.tick((key, recipe) -> true, (key, recipe) -> fail());
        queue.cancel("one"); assertFalse(queue.pending("one"));
        assertEquals(0, queue.tick((key, recipe) -> true, (key, recipe) -> fail()));
        queue.enqueue("one", List.of(100)); queue.clear(); assertEquals(0, queue.tick((key, recipe) -> true, (key, recipe) -> fail()));
    }
    @Test void catalogLimitIsNotRelaxed() {
        var queue = new ClassicRecipeSearch<String, Integer>();
        assertTrue(queue.enqueue("maximum", IntStream.range(0, 1_024).boxed().toList()));
        assertThrows(IllegalArgumentException.class, () -> queue.enqueue("oversize", IntStream.range(0, 1_025).boxed().toList()));
    }
    @Test void emptyCatalogCompletesWithoutARecipeCheck() {
        var queue = new ClassicRecipeSearch<String, Integer>(); var results = new ArrayList<Optional<Integer>>();
        queue.enqueue("one", List.of()); assertEquals(0, queue.tick((key, recipe) -> { fail(); return true; }, (key, recipe) -> results.add(recipe)));
        assertEquals(List.of(Optional.empty()), results);
    }
}
