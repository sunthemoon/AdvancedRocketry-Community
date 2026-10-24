package io.github.sunthemoon.advancedrocketrycommunity.rocket.server;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Predicate;
import org.junit.jupiter.api.Test;

class SealedRecoverySelectionTest {
    @Test void legacyAndSealedCandidatesShareOneSelectionPerTick() throws Exception {
        var selector = selector(); var candidates = List.of(id(3), id(1), id(2));
        assertEquals(Optional.of(id(1)), select(selector, 10, candidates, value -> true));
        assertTrue(select(selector, 10, List.of(id(2)), value -> true).isEmpty());
        assertEquals(Optional.of(id(2)), select(selector, 11, candidates, value -> true));
        assertEquals(Optional.of(id(3)), select(selector, 12, candidates, value -> true));
        assertEquals(Optional.of(id(1)), select(selector, 13, candidates, value -> true));
    }
    @Test void liveOwnersAreExcludedWithoutStarvingOtherDurableRecords() throws Exception {
        var selector = selector(); var candidates = List.of(id(1), id(2), id(3));
        for (int tick = 0; tick < 8; tick++) {
            assertEquals(Optional.of(id(tick % 2 == 0 ? 2 : 3)), select(selector, tick, candidates, value -> !value.equals(id(1))));
        }
    }
    @Test void removedCursorAndDuplicateSourcesDoNotResetFairness() throws Exception {
        var selector = selector();
        assertEquals(Optional.of(id(2)), select(selector, 1, List.of(id(2), id(4)), value -> true));
        assertEquals(Optional.of(id(3)), select(selector, 2, List.of(id(1), id(3), id(3), id(4)), value -> true));
        assertEquals(Optional.of(id(4)), select(selector, 3, List.of(id(1), id(3), id(4)), value -> true));
        assertEquals(Optional.of(id(1)), select(selector, 4, List.of(id(1), id(4)), value -> true));
    }
    @Test void aNoWorkDecisionStillPreventsASecondRecoveryPassInTheSameTick() throws Exception {
        var selector = selector();
        assertTrue(select(selector, 1, List.of(id(1)), value -> false).isEmpty());
        assertTrue(select(selector, 1, List.of(id(1)), value -> true).isEmpty());
        assertEquals(Optional.of(id(1)), select(selector, 2, List.of(id(1)), value -> true));
    }
    private static UUID id(int value) { return new UUID(0, value); }
    private static Object selector() throws Exception {
        return Class.forName(SealedRecoverySelectionTest.class.getPackageName() + ".SealedRecoverySelection").getConstructor().newInstance();
    }
    @SuppressWarnings("unchecked") private static Optional<UUID> select(Object selector, long tick, List<UUID> candidates,
            Predicate<UUID> eligible) throws Exception {
        return (Optional<UUID>) selector.getClass().getMethod("select", long.class, List.class, Predicate.class)
                .invoke(selector, tick, candidates, eligible);
    }
}
