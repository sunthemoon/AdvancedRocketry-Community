package io.github.sunthemoon.advancedrocketrycommunity.rocket.flight;

import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class RocketPassengerReconnectQueueTest {
    @Test
    void rotatesUnderThePerTickBudgetWithoutLosingWaitingPlayers() {
        var queue = new RocketPassengerReconnectQueue();
        var players = java.util.stream.IntStream.range(0, 6).mapToObj(i -> new UUID(0, i)).toList();
        players.forEach(p -> assertTrue(queue.offer(p, 0)));
        assertEquals(players.subList(0, 4), queue.next(1).pending());
        assertEquals(List.of(players.get(4), players.get(5), players.get(0), players.get(1)), queue.next(2).pending());
    }

    @Test
    void repeatedOfferDoesNotExtendExpiry() {
        var queue = new RocketPassengerReconnectQueue();
        var player = UUID.randomUUID();
        assertTrue(queue.offer(player, 5));
        assertTrue(queue.offer(player, 204));
        assertEquals(List.of(player), queue.next(204).pending());
        assertEquals(List.of(player), queue.next(205).expired());
        assertTrue(queue.next(206).pending().isEmpty());
    }

    @Test
    void rejectsOverflowWithoutEvictingExistingEntries() {
        var queue = new RocketPassengerReconnectQueue();
        for (int i = 0; i < RocketPassengerReconnectQueue.CAPACITY; i++) {
            assertTrue(queue.offer(new UUID(0, i), 0));
        }
        assertFalse(queue.offer(new UUID(1, 0), 0));
        assertTrue(queue.offer(new UUID(0, 0), 0));
        assertEquals(new UUID(0, 0), queue.next(1).pending().get(0));
    }

    @Test
    void completionAndLifecycleClearRemoveRetries() {
        var queue = new RocketPassengerReconnectQueue();
        var first = UUID.randomUUID();
        var second = UUID.randomUUID();
        queue.offer(first, 0);
        queue.offer(second, 0);
        queue.complete(first);
        assertEquals(List.of(second), queue.next(1).pending());
        queue.clear();
        assertTrue(queue.next(2).pending().isEmpty());
    }

    @Test
    void timeRollbackExpiresRatherThanExtendingWait() {
        var queue = new RocketPassengerReconnectQueue();
        var player = UUID.randomUUID();
        queue.offer(player, 100);
        assertEquals(List.of(player), queue.next(99).expired());
    }

    @Test
    void completedSessionDoesNotDonateItsDeadlineToTheNextSession() {
        var queue = new RocketPassengerReconnectQueue();
        var player = UUID.randomUUID();
        queue.offer(player, 0);
        queue.complete(player);
        assertTrue(queue.offer(player, 199));
        assertEquals(List.of(player), queue.next(200).pending());
        assertEquals(List.of(player), queue.next(398).pending());
        assertEquals(List.of(player), queue.next(399).expired());
    }

    @Test
    void rejectsInvalidInputAndReturnsImmutableBatches() {
        var queue = new RocketPassengerReconnectQueue();
        assertThrows(NullPointerException.class, () -> queue.offer(null, 0));
        assertThrows(IllegalArgumentException.class, () -> queue.offer(UUID.randomUUID(), -1));
        queue.offer(UUID.randomUUID(), 0);
        assertThrows(UnsupportedOperationException.class, () -> queue.next(1).pending().clear());
    }
}
