package io.github.sunthemoon.advancedrocketrycommunity.satellite.service;

import static org.junit.jupiter.api.Assertions.*;

import io.github.sunthemoon.advancedrocketrycommunity.satellite.model.SatelliteLimits;
import java.util.ArrayList;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class DiscoveryReplayQueueTest {
    @Test void replayIsDeduplicatedBoundedAndFair() {
        var queue = new DiscoveryReplayQueue(); var ids = new ArrayList<UUID>();
        for (int i = 0; i < 11; i++) { ids.add(UUID.randomUUID()); queue.add(ids.get(i)); queue.add(ids.get(i)); }
        var seen = new ArrayList<UUID>();
        assertEquals(8, queue.drain(id -> { seen.add(id); return false; }));
        assertEquals(ids.subList(0, 8), seen);
        seen.clear();
        assertEquals(8, queue.drain(id -> { seen.add(id); return true; }));
        assertEquals(ids.subList(8, 11), seen.subList(0, 3));
        assertEquals(3, queue.size());
        assertEquals(3, queue.drain(id -> true));
        assertEquals(0, queue.drain(id -> { fail("Empty queue inspected work"); return true; }));
    }

    @Test void onePendingReceiptIsNotRetriedEightTimesInATick() {
        var queue = new DiscoveryReplayQueue(); queue.add(UUID.randomUUID());
        assertEquals(1, queue.drain(id -> false));
        assertEquals(1, queue.size());
    }

    @Test void failureRetainsTheReceiptAndDoesNotDiscardUninspectedWork() {
        var queue = new DiscoveryReplayQueue(); UUID first = UUID.randomUUID(), second = UUID.randomUUID();
        queue.add(first); queue.add(second);
        assertThrows(IllegalStateException.class, () -> queue.drain(id -> { throw new IllegalStateException("injected"); }));
        assertEquals(2, queue.size());
        var seen = new ArrayList<UUID>(); queue.drain(id -> { seen.add(id); return true; });
        assertEquals(java.util.List.of(second, first), seen);
    }

    @Test void queueCannotExceedExistingMissionBoundAndClearDropsWorldReferences() {
        var queue = new DiscoveryReplayQueue();
        for (int i = 0; i < SatelliteLimits.MAX_MISSIONS; i++) { queue.add(new UUID(0, i)); }
        queue.add(new UUID(0, 0));
        assertEquals(SatelliteLimits.MAX_MISSIONS, queue.size());
        assertThrows(IllegalStateException.class, () -> queue.add(new UUID(1, 0)));
        queue.clear(); assertEquals(0, queue.size()); queue.add(new UUID(1, 0)); assertEquals(1, queue.size());
    }
}
