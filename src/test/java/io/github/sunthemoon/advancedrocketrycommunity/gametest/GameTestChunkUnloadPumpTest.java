package io.github.sunthemoon.advancedrocketrycommunity.gametest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class GameTestChunkUnloadPumpTest {
    @BeforeAll static void bootstrap() { MinecraftBootstrap.initialize(); }

    @Test void emptyQueueDoesNoWork() {
        assertEquals(0, GameTestChunkUnloadPump.drainReady(new ArrayDeque<>()));
    }

    @Test void unfinishedSaveFutureIsNotForcedAndReadyCallbackNeedsNoSpareTime() {
        Queue<Runnable> queue = new ArrayDeque<>();
        var save = new CompletableFuture<Void>();
        var unloaded = new AtomicBoolean();
        // Same executor handoff as ChunkMap.scheduleUnload, not a fabricated unload event.
        var completion = save.thenRunAsync(() -> unloaded.set(true), queue::add);
        assertEquals(0, GameTestChunkUnloadPump.drainReady(queue));
        assertFalse(save.isDone());
        assertFalse(unloaded.get());
        save.complete(null);
        assertFalse(unloaded.get());
        assertEquals(1, GameTestChunkUnloadPump.drainReady(queue));
        assertTrue(unloaded.get());
        assertTrue(completion.isDone());
    }

    @Test void preservesFifoAndLeavesWorkBeyondThePerTickBound() {
        Queue<Runnable> queue = new ArrayDeque<>();
        List<Integer> actual = new ArrayList<>();
        List<Integer> expected = new ArrayList<>();
        for (int i = 0; i < GameTestChunkUnloadPump.MAX_TASKS_PER_TICK + 1; i++) {
            int ordinal = i;
            queue.add(() -> actual.add(ordinal));
            if (i < GameTestChunkUnloadPump.MAX_TASKS_PER_TICK) { expected.add(i); }
        }
        assertEquals(GameTestChunkUnloadPump.MAX_TASKS_PER_TICK, GameTestChunkUnloadPump.drainReady(queue));
        assertEquals(expected, actual);
        assertEquals(1, queue.size());
        assertEquals(1, GameTestChunkUnloadPump.drainReady(queue));
        assertEquals(GameTestChunkUnloadPump.MAX_TASKS_PER_TICK, actual.get(actual.size() - 1));
        assertTrue(queue.isEmpty());
    }

    @Test void callbacksThatRequeueCannotRunWithoutBound() {
        Queue<Runnable> queue = new ArrayDeque<>();
        Runnable again = new Runnable() {
            @Override public void run() { queue.add(this); }
        };
        queue.add(again);
        assertEquals(GameTestChunkUnloadPump.MAX_TASKS_PER_TICK, GameTestChunkUnloadPump.drainReady(queue));
        assertEquals(1, queue.size());
        assertSame(again, queue.peek());
    }

    @Test void changedFutureRetryWaitsUntilItsReplacementCompletes() {
        Queue<Runnable> queue = new ArrayDeque<>();
        var replacement = new CompletableFuture<Void>();
        var unloaded = new AtomicBoolean();
        queue.add(() -> replacement.thenRunAsync(() -> unloaded.set(true), queue::add));
        assertEquals(1, GameTestChunkUnloadPump.drainReady(queue));
        assertFalse(unloaded.get());
        assertFalse(replacement.isDone());
        replacement.complete(null);
        assertEquals(1, GameTestChunkUnloadPump.drainReady(queue));
        assertTrue(unloaded.get());
    }

    @Test void callbackFailurePropagatesWithoutDiscardingTheRest() {
        Queue<Runnable> queue = new ArrayDeque<>();
        var failure = new IllegalStateException("callback failure");
        queue.add(() -> { throw failure; });
        var remaining = new AtomicBoolean();
        queue.add(() -> remaining.set(true));
        assertSame(failure, assertThrows(IllegalStateException.class, () -> GameTestChunkUnloadPump.drainReady(queue)));
        assertFalse(remaining.get());
        assertEquals(1, queue.size());
    }
}
