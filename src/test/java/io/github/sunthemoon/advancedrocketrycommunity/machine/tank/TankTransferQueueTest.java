package io.github.sunthemoon.advancedrocketrycommunity.machine.tank;

import static org.junit.jupiter.api.Assertions.*;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class TankTransferQueueTest {
    @Test void deduplicatedLaterTickAndGlobalBudget() {
        var queue = new TankTransferQueue<Integer>();
        for (int key = 0; key < 100; key++) {
            queue.offer(key, 10, 10);
            queue.offer(key, 10, 10);
        }
        assertEquals(100, queue.size());
        assertEquals(0, queue.tick(10, ignored -> fail("Same-tick transfer")));
        List<Integer> seen = new ArrayList<>();
        assertEquals(64, queue.tick(11, seen::add));
        assertEquals(0, queue.tick(11, ignored -> fail("Duplicate Level tick")));
        assertEquals(36, queue.tick(12, seen::add));
        assertEquals(100, seen.stream().distinct().count());
        assertEquals(0, queue.size());
    }

    @Test void fullQueueRetainsCallerDirtyStateAndFairlyAdmitsOverflowAgainstRepeatingEarlyTickers() {
        var queue = new TankTransferQueue<Integer>();
        int tanks = 2_300;
        long[] dirtySince = new long[tanks];
        boolean[] seen = new boolean[tanks];
        for (long tick = 0; tick < 150; tick++) {
            // Models fixed loaded-BE ticker order; each serviced tank requests again forever.
            for (int key = 0; key < tanks; key++) {
                queue.offer(key, tick, dirtySince[key]);
                assertTrue(queue.size() <= TankTransferQueue.MAX_QUEUED);
            }
            long now = tick;
            assertTrue(queue.tick(tick, key -> { seen[key] = true; dirtySince[key] = now; }) <= 64);
        }
        for (int key = 0; key < tanks; key++) { assertTrue(seen[key], "Starved overflow tank " + key); }
    }

    @Test void cancellationAndReentrantRequestsDoNotEnlargeDrain() {
        var queue = new TankTransferQueue<Integer>();
        queue.offer(1, 0, 0);
        queue.offer(2, 0, 0);
        queue.remove(2);
        List<Integer> seen = new ArrayList<>();
        assertEquals(1, queue.tick(1, key -> {
            seen.add(key);
            queue.offer(key, 1, 1);
            assertEquals(0, queue.tick(1, ignored -> fail("Reentrant queue drain")));
        }));
        assertEquals(List.of(1), seen);
        assertEquals(1, queue.size());
        queue.clear();
        assertEquals(0, queue.size());
    }
}
