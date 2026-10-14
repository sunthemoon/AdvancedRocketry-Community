package io.github.sunthemoon.advancedrocketrycommunity.progression.classic.firstevent;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** W44 A0: the bounded deferred-grant queue (section 3.5): fairness, spacing, the cap and connection scope. */
class FirstEventGrantQueueTest {
    private static final FirstEventKind WARP = FirstEventKind.FIRST_STATION_WARP;
    private static final FirstEventKind MOON = FirstEventKind.FIRST_MOON_LANDING;

    @Test void atMostSixteenEntriesPerTickAndEachAtMostOncePerTwentyTicks() {
        var queue = new FirstEventGrantQueue();
        for (int index = 0; index < 40; index++) { assertTrue(queue.add(new UUID(1, index), WARP, 7, 0L)); }
        List<UUID> examined = new ArrayList<>();
        queue.tick(0L, (player, kind, connection) -> { examined.add(player); return FirstEventGrantQueue.Outcome.RETRY; });
        assertEquals(16, examined.size());
        queue.tick(1L, (player, kind, connection) -> { examined.add(player); return FirstEventGrantQueue.Outcome.RETRY; });
        queue.tick(2L, (player, kind, connection) -> { examined.add(player); return FirstEventGrantQueue.Outcome.RETRY; });
        assertEquals(40, examined.size(), "a fair pass reaches every entry within ceil(40 / 16) ticks");
        assertEquals(40, examined.stream().distinct().count());
        examined.clear();
        for (long tick = 3L; tick < 20L; tick++) {
            queue.tick(tick, (player, kind, connection) -> { examined.add(player); return FirstEventGrantQueue.Outcome.RETRY; });
        }
        assertTrue(examined.isEmpty(), "no entry is rechecked within 20 ticks");
        queue.tick(20L, (player, kind, connection) -> { examined.add(player); return FirstEventGrantQueue.Outcome.SETTLED; });
        assertEquals(16, examined.size());
        assertEquals(24, queue.size(), "settled entries leave the queue");
    }

    @Test void theCapFollowsTheReceiptBoundsAndOnePendingBitPerKind() {
        var queue = new FirstEventGrantQueue();
        assertEquals(4_112, FirstEventGrantQueue.MAX_ENTRIES);
        for (int index = 0; index < FirstEventGrantQueue.MAX_ENTRIES; index++) {
            assertTrue(queue.add(new UUID(2, index), index < 16 ? MOON : WARP, 1, 0L));
        }
        assertFalse(queue.add(new UUID(3, 1), WARP, 1, 0L));
        assertTrue(queue.add(new UUID(2, 20), WARP, 1, 0L), "the same pending bit is not a second entry");
        assertEquals(FirstEventGrantQueue.MAX_ENTRIES, queue.size());
        assertEquals(16, queue.size(MOON));
    }

    @Test void aStaleDisconnectNeverRemovesANewerConnectionsEntry() {
        var queue = new FirstEventGrantQueue();
        UUID player = new UUID(4, 1);
        queue.add(player, MOON, 1, 0L);
        queue.add(player, WARP, 1, 0L);
        queue.add(player, MOON, 2, 5L);
        queue.removeConnection(player, 1);
        assertTrue(queue.contains(player, MOON), "the reconnected entry stays");
        assertFalse(queue.contains(player, WARP));
        queue.removeConnection(player, 2);
        assertEquals(0, queue.size());
    }

    @Test void readinessWakesOnlyThatKindWithinTheSameBoundedQueue() {
        var queue = new FirstEventGrantQueue();
        queue.add(new UUID(5, 1), MOON, 1, 0L);
        queue.add(new UUID(5, 2), WARP, 1, 0L);
        queue.tick(0L, (player, kind, connection) -> FirstEventGrantQueue.Outcome.RETRY);
        queue.wake(WARP, 3L);
        List<FirstEventKind> examined = new ArrayList<>();
        queue.tick(3L, (player, kind, connection) -> { examined.add(kind); return FirstEventGrantQueue.Outcome.RETRY; });
        assertEquals(List.of(WARP), examined);
    }
}
