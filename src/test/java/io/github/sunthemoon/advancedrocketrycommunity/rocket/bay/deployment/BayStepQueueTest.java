package io.github.sunthemoon.advancedrocketrycommunity.rocket.bay.deployment;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** W27d1 A0: the one round-robin step queue, 32 steps per tick, shared by checkpoint and restart work. */
final class BayStepQueueTest {
    private static UUID id(int n) { return new UUID(128, n); }

    @Test void aTickRunsAtMostThirtyTwoStepsAndEveryQueuedKeyHasItsTurnFirst() {
        var queue = new BayStepQueue();
        for (int n = 0; n < 40; n++) { queue.offer(id(n)); }
        List<UUID> ran = new ArrayList<>();
        assertEquals(BayStepQueue.STEPS_PER_TICK, queue.run(key -> { ran.add(key); return BayStepQueue.Result.AGAIN; }));
        assertEquals(32, ran.size());
        for (int n = 0; n < 32; n++) { assertEquals(id(n), ran.get(n), "position order before any repeat"); }
        ran.clear();
        queue.run(key -> { ran.add(key); return BayStepQueue.Result.IDLE; });
        for (int n = 0; n < 8; n++) { assertEquals(id(32 + n), ran.get(n), "the keys not yet served come next"); }
        assertEquals(id(0), ran.get(8), "then the first repeat, at the back of the order");
    }

    @Test void oneKeyAskingAgainCannotStarveAnotherAndWaitingKeysCostOneStepPerTick() {
        var queue = new BayStepQueue();
        queue.offer(id(1)); queue.offer(id(2)); queue.offer(id(3));
        List<UUID> ran = new ArrayList<>();
        int steps = queue.run(key -> {
            ran.add(key);
            if (key.equals(id(1))) { return BayStepQueue.Result.AGAIN; }
            return key.equals(id(2)) ? BayStepQueue.Result.NEXT_TICK : BayStepQueue.Result.IDLE;
        });
        assertEquals(32, steps, "the busy key uses the rest of the budget");
        assertEquals(List.of(id(1), id(2), id(3), id(1)), ran.subList(0, 4));
        assertEquals(1, ran.stream().filter(id(2)::equals).count(), "NEXT_TICK runs once per tick");
        assertTrue(queue.queued(id(2)), "and is queued for the next tick");
        assertFalse(queue.queued(id(3)), "IDLE waits for an event");
        queue.offer(id(1)); queue.offer(id(1));
        assertEquals(2, queue.size(), "a key is queued once");
    }

    @Test void removeAndClearDropQueuedWork() {
        var queue = new BayStepQueue();
        queue.offer(id(1)); queue.offer(id(2)); queue.remove(id(1));
        assertFalse(queue.queued(id(1)));
        assertEquals(1, queue.run(key -> BayStepQueue.Result.NEXT_TICK));
        assertTrue(queue.queued(id(2)));
        queue.clear();
        assertEquals(0, queue.run(key -> BayStepQueue.Result.AGAIN));
    }
}
