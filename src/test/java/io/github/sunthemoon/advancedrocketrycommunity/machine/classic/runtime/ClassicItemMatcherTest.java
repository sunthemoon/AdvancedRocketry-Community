package io.github.sunthemoon.advancedrocketrycommunity.machine.classic.runtime;

import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
import java.util.Set;
import java.util.Collections;
import org.junit.jupiter.api.Test;

class ClassicItemMatcherTest {
    @Test void overlappingRowsReassignFlexibleInputs() {
        var supplies = List.of(new ClassicItemMatcher.Supply("a", 1), new ClassicItemMatcher.Supply("b", 1));
        var demands = List.of(new ClassicItemMatcher.Demand(Set.of("a", "b"), 1), new ClassicItemMatcher.Demand(Set.of("a"), 1));
        assertArrayEquals(new int[]{1, 1}, ClassicItemMatcher.consumed(supplies, demands).orElseThrow());
    }
    @Test void aTagDemandCanSplitAcrossSlotsAndAlternatives() {
        var supplies = List.of(new ClassicItemMatcher.Supply("a", 32), new ClassicItemMatcher.Supply("b", 32));
        assertArrayEquals(new int[]{32, 32}, ClassicItemMatcher.consumed(supplies,
                List.of(new ClassicItemMatcher.Demand(Set.of("a", "b"), 64))).orElseThrow());
    }
    @Test void insufficientOrUnrelatedInputsDoNotCreateAnAllocation() {
        assertTrue(ClassicItemMatcher.consumed(List.of(new ClassicItemMatcher.Supply("a", 63)),
                List.of(new ClassicItemMatcher.Demand(Set.of("a"), 64))).isEmpty());
        assertTrue(ClassicItemMatcher.consumed(List.of(new ClassicItemMatcher.Supply("b", 64)),
                List.of(new ClassicItemMatcher.Demand(Set.of("a"), 1))).isEmpty());
    }
    @Test void maximumNativeSlotAndRecipeBoundsAreEnforced() {
        assertTrue(ClassicItemMatcher.consumed(Collections.nCopies(256, new ClassicItemMatcher.Supply("a", 64)),
                Collections.nCopies(4, new ClassicItemMatcher.Demand(Set.of("a"), 64))).isPresent());
        assertThrows(IllegalArgumentException.class, () -> ClassicItemMatcher.consumed(
                Collections.nCopies(257, new ClassicItemMatcher.Supply("a", 1)), List.of()));
        assertThrows(IllegalArgumentException.class, () -> ClassicItemMatcher.consumed(List.of(),
                Collections.nCopies(5, new ClassicItemMatcher.Demand(Set.of("a"), 1))));
        assertThrows(IllegalArgumentException.class, () -> new ClassicItemMatcher.Supply("a", 65));
        assertThrows(IllegalArgumentException.class, () -> new ClassicItemMatcher.Demand(Set.of("a"), 0));
    }
}
