package io.github.sunthemoon.advancedrocketrycommunity.atmosphere.torch;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.LongStream;
import org.junit.jupiter.api.Test;

/** A0 checks of the per-Level queue: the 8,192 cap, FIFO fairness, the 256-check budget and bounded sweeps. */
class TorchQueueTest {
    @Test
    void offersAreDistinctAndCappedAt8192() {
        TorchQueue queue = new TorchQueue();
        for (long position = 0; position < TorchQueue.MAX_POSITIONS; position++) {
            assertEquals(TorchQueue.Offer.ADDED, queue.offer(position));
        }
        assertEquals(TorchQueue.Offer.PRESENT, queue.offer(17));
        assertTrue(queue.full());
        assertEquals(TorchQueue.Offer.FULL, queue.offer(-1));
        assertEquals(TorchQueue.MAX_POSITIONS, queue.metrics().queued());
        assertEquals(1, queue.metrics().overflows());
    }

    @Test
    void overloadIsReportedOncePerEpisodeUntilTheQueueDrainsToHalf() {
        TorchQueue queue = new TorchQueue();
        LongStream.range(0, TorchQueue.MAX_POSITIONS).forEach(queue::offer);
        assertTrue(queue.refuseOverloaded());
        assertFalse(queue.refuseOverloaded());
        for (int tick = 0; tick < 15; tick++) {
            queue.tick(position -> TorchQueue.Outcome.DONE, position -> false);
        }
        assertEquals(TorchQueue.MAX_POSITIONS - 15 * TorchQueue.CHECKS_PER_TICK, queue.metrics().queued());
        assertFalse(queue.refuseOverloaded(), "above half the episode continues");
        queue.tick(position -> TorchQueue.Outcome.DONE, position -> false);
        assertEquals(TorchQueue.MAX_POSITIONS / 2, queue.metrics().queued());
        assertTrue(queue.refuseOverloaded(), "a new episode starts after draining to half");
        assertEquals(4, queue.metrics().overflows(), "every refusal is counted");
    }

    @Test
    void oneTickChecksAtMost256PositionsInFifoOrder() {
        TorchQueue queue = new TorchQueue();
        LongStream.range(0, 1_000).forEach(queue::offer);
        List<Long> seen = new ArrayList<>();
        queue.tick(position -> {
            seen.add(position);
            return TorchQueue.Outcome.DONE;
        }, position -> false);
        assertEquals(LongStream.range(0, TorchQueue.CHECKS_PER_TICK).boxed().toList(), seen);
        assertEquals(1_000 - TorchQueue.CHECKS_PER_TICK, queue.metrics().queued());
    }

    @Test
    void deferredPositionsMoveToTheTailAndWaitForTheNextTick() {
        TorchQueue queue = new TorchQueue();
        LongStream.range(0, 4).forEach(queue::offer);
        List<Long> seen = new ArrayList<>();
        queue.tick(position -> {
            seen.add(position);
            return position == 1 ? TorchQueue.Outcome.DEFER : TorchQueue.Outcome.DONE;
        }, position -> false);
        assertEquals(List.of(0L, 1L, 2L, 3L), seen, "a deferred entry is not revisited in the same tick");
        queue.offer(9);
        seen.clear();
        queue.tick(position -> {
            seen.add(position);
            return TorchQueue.Outcome.DONE;
        }, position -> false);
        assertEquals(List.of(1L, 9L), seen);
        assertEquals(1, queue.metrics().deferrals());
    }

    @Test
    void sweepsShareTheBudgetOneForOneAndQueueTheirCandidates() {
        TorchQueue queue = new TorchQueue();
        LongStream.range(0, 1_000).forEach(queue::offer);
        assertTrue(queue.addSweep(LongStream.range(10_000, 10_500).iterator()));
        int[] checks = {0};
        queue.tick(position -> {
            checks[0]++;
            return TorchQueue.Outcome.DONE;
        }, position -> position % 100 == 0);
        TorchQueue.Metrics metrics = queue.metrics();
        assertEquals(TorchQueue.CHECKS_PER_TICK / 2, checks[0]);
        assertEquals(TorchQueue.CHECKS_PER_TICK / 2, metrics.sweepCells());
        assertEquals(1_000 - TorchQueue.CHECKS_PER_TICK / 2 + 2, metrics.queued(), "cells 10000 and 10100 queued");
        assertEquals(1, metrics.sweeps());
    }

    @Test
    void anIdleQueueGivesSweepsTheWholeBudgetAndFinishedSweepsLeave() {
        TorchQueue queue = new TorchQueue();
        queue.addSweep(LongStream.range(0, 300).iterator());
        queue.tick(position -> TorchQueue.Outcome.DONE, position -> false);
        assertEquals(TorchQueue.CHECKS_PER_TICK, queue.metrics().sweepCells());
        queue.tick(position -> TorchQueue.Outcome.DONE, position -> false);
        assertEquals(300, queue.metrics().sweepCells());
        assertEquals(0, queue.metrics().sweeps());
    }

    @Test
    void sweepsAreCappedAt128AndPauseWhileTheQueueIsFull() {
        TorchQueue queue = new TorchQueue();
        for (int index = 0; index < TorchQueue.MAX_SWEEPS; index++) {
            assertTrue(queue.addSweep(LongStream.of(index).iterator()));
        }
        assertFalse(queue.addSweep(LongStream.of(-5).iterator()));
        assertEquals(1, queue.metrics().droppedSweeps());

        TorchQueue full = new TorchQueue();
        LongStream.range(0, TorchQueue.MAX_POSITIONS).forEach(full::offer);
        full.addSweep(LongStream.range(-100, -1).iterator());
        full.tick(position -> TorchQueue.Outcome.DEFER, position -> true);
        assertEquals(0, full.metrics().sweepCells(), "a full queue has no room for what a sweep finds");
        assertEquals(TorchQueue.MAX_POSITIONS, full.metrics().queued());
    }
}
