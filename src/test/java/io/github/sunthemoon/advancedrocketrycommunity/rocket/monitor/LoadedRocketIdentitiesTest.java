package io.github.sunthemoon.advancedrocketrycommunity.rocket.monitor;

import static org.junit.jupiter.api.Assertions.*;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class LoadedRocketIdentitiesTest {
    @Test void followsRecreatedPhysicalIdentityAndReloadsWithoutRetainingRemovedEntities() {
        var index = new LoadedRocketIdentities();
        var logical = UUID.randomUUID(); var old = UUID.randomUUID(); var replacement = UUID.randomUUID();
        index.joined(old, logical); index.joined(old, logical);
        assertEquals(1, index.size()); assertEquals(old, index.unique(logical));
        index.left(old); assertNull(index.unique(logical));
        index.joined(replacement, logical); assertEquals(replacement, index.unique(logical));
        index.clear(); assertEquals(0, index.size()); assertNull(index.unique(logical));
        index.joined(replacement, logical); assertEquals(replacement, index.unique(logical));
    }

    @Test void rejectsAmbiguousCopiesUntilOneLeavesAndHandlesIdentityChanges() {
        var index = new LoadedRocketIdentities();
        var logical = UUID.randomUUID(); var other = UUID.randomUUID(); var first = UUID.randomUUID(); var copy = UUID.randomUUID();
        assertFalse(index.ambiguous(logical));
        index.joined(first, logical); index.joined(copy, logical); assertNull(index.unique(logical)); assertTrue(index.ambiguous(logical));
        index.left(copy); assertEquals(first, index.unique(logical));
        index.joined(first, other); assertNull(index.unique(logical)); assertEquals(first, index.unique(other));
        index.left(copy); assertEquals(1, index.size());
    }

    @Test void overflowFailsClosedWithoutGrowingOrTrustingAnUnindexedDuplicate() {
        var index = new LoadedRocketIdentities(); var logical = UUID.randomUUID(); var first = UUID.randomUUID();
        index.joined(first, logical);
        for (int i = 1; i < LoadedRocketIdentities.MAX_ENTITIES; i++) { index.joined(UUID.randomUUID(), UUID.randomUUID()); }
        assertEquals(first, index.unique(logical));
        index.joined(UUID.randomUUID(), logical);
        assertEquals(LoadedRocketIdentities.MAX_ENTITIES, index.size()); assertNull(index.unique(logical)); assertTrue(index.ambiguous(logical));
        index.left(first); assertNull(index.unique(logical));
        index.clear(); index.joined(first, logical); assertEquals(first, index.unique(logical));
    }
}
