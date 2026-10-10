package io.github.sunthemoon.advancedrocketrycommunity.gametest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class ChunkSaveWatchStateTest {
    @Test void retainsInclusiveTimingAndSeparatesTheTwoWatchedChunks() {
        var state = new ChunkSaveWatchState(() -> { }, 1L, 2L);
        assertFalse(state.any(1L));
        state.record(1L, 10L);
        state.record(1L, 50L);
        state.record(2L, 90L);
        state.record(3L, 20L);
        assertTrue(state.any(1L));
        assertTrue(state.between(1L, 10L, 10L));
        assertTrue(state.between(1L, 50L, 50L));
        assertFalse(state.between(1L, 11L, 49L));
        assertFalse(state.between(1L, 90L, 90L));
        assertTrue(state.between(2L, 90L, 90L));
        assertFalse(state.between(1L, 50L, 10L));
    }

    @Test void successStopIsIdempotentAndDispatchedSavesCannotChangeTheFrozenHistory() {
        AtomicInteger detached = new AtomicInteger();
        var state = new ChunkSaveWatchState(detached::incrementAndGet, 1L);
        state.record(1L, 10L);
        String history = state.toString();
        state.stop();
        state.record(1L, 11L);
        state.stop();
        state.close(null); // A terminal/batch or server-stop fallback after an explicit stop.
        assertTrue(state.isClosed());
        assertEquals(1, detached.get());
        assertEquals(history, state.toString());
        assertFalse(state.between(1L, 11L, 11L));
    }

    @Test void assertionOrTimeoutCleanupRetainsThePrimaryFailureAndClosesBeforeDetaching() {
        RuntimeException primary = new RuntimeException("test failure");
        RuntimeException detachFailure = new RuntimeException("detach failure");
        AtomicInteger detached = new AtomicInteger();
        ChunkSaveWatchState[] owner = new ChunkSaveWatchState[1];
        owner[0] = new ChunkSaveWatchState(() -> {
            detached.incrementAndGet();
            assertTrue(owner[0].isClosed());
            owner[0].record(1L, 99L); // Reentrant/already-dispatched callback has no observation authority.
            throw detachFailure;
        }, 1L);
        owner[0].close(primary);
        owner[0].close(primary);
        assertEquals(1, detached.get());
        assertFalse(owner[0].any(1L));
        assertEquals(1, primary.getSuppressed().length);
        assertSame(detachFailure, primary.getSuppressed()[0]);
    }

    @Test void standaloneCleanupFailureRemainsVisibleAndCannotReactivateTheObserver() {
        Error detachFailure = new AssertionError("detach failure");
        var state = new ChunkSaveWatchState(() -> { throw detachFailure; }, 1L);
        assertSame(detachFailure, assertThrows(AssertionError.class, state::stop));
        state.record(1L, 99L);
        state.close(null);
        assertTrue(state.isClosed());
        assertFalse(state.any(1L));
    }

    @Test void thePrimaryFailureIsNeverAddedAsItsOwnSuppressedFailure() {
        RuntimeException primary = new RuntimeException("same failure");
        var state = new ChunkSaveWatchState(() -> { throw primary; }, 1L);
        state.close(primary);
        assertTrue(state.isClosed());
        assertEquals(0, primary.getSuppressed().length);
    }

    @Test void repeatedSavesAtOneTickDoNotExhaustTheHistoryBudget() {
        var state = new ChunkSaveWatchState(() -> { }, 1L, 1L);
        for (int i = 0; i < ChunkSaveWatchState.MAX_SAVED_TICKS_PER_CHUNK * 2; i++) {
            state.record(1L, 17L);
        }
        assertFalse(state.isClosed());
        assertTrue(state.between(1L, 17L, 17L));
        assertEquals("{1=[17]}", state.toString());
    }

    @Test void exactlyTheDistinctTickBudgetStillSupportsBothTimingEndpoints() {
        var state = new ChunkSaveWatchState(() -> { }, 1L, 2L);
        for (int i = 0; i < ChunkSaveWatchState.MAX_SAVED_TICKS_PER_CHUNK; i++) {
            state.record(1L, i);
            state.record(2L, -i - 1L);
        }
        state.record(1L, 0L); // A duplicate at capacity is still admissible.
        assertFalse(state.isClosed());
        assertTrue(state.between(1L, 0L, 0L));
        long last = ChunkSaveWatchState.MAX_SAVED_TICKS_PER_CHUNK - 1L;
        assertTrue(state.between(1L, last, last));
        assertTrue(state.between(2L, -last - 1L, -last - 1L));
        state.stop();
    }

    @Test void overflowingHistoryDetachesWithoutThrowingIntoTheSaveAndEveryQueryRejectsIt() {
        AtomicInteger detached = new AtomicInteger();
        var state = new ChunkSaveWatchState(detached::incrementAndGet, 1L);
        for (int i = 0; i <= ChunkSaveWatchState.MAX_SAVED_TICKS_PER_CHUNK; i++) {
            state.record(1L, i);
        }
        assertTrue(state.isClosed());
        assertEquals(1, detached.get());
        String history = state.toString();
        IllegalStateException failure = assertThrows(IllegalStateException.class, () -> state.any(1L));
        assertSame(failure, assertThrows(IllegalStateException.class, () -> state.between(1L, 0L, 0L)));
        assertSame(failure, assertThrows(IllegalStateException.class, state::stop));
        state.record(1L, 99_999L);
        assertEquals(history, state.toString());
        assertEquals(1, detached.get());
    }

    @Test void overflowPreservesDetachmentFailureWithoutAffectingTheSaveCallback() {
        RuntimeException detachFailure = new RuntimeException("detach failure");
        var state = new ChunkSaveWatchState(() -> { throw detachFailure; }, 1L);
        for (int i = 0; i <= ChunkSaveWatchState.MAX_SAVED_TICKS_PER_CHUNK; i++) {
            state.record(1L, i);
        }
        IllegalStateException overflow = assertThrows(IllegalStateException.class, state::stop);
        assertEquals(1, overflow.getSuppressed().length);
        assertSame(detachFailure, overflow.getSuppressed()[0]);
    }

    @Test void rejectsUnsupportedWatchInputsAndQueriesWithoutAcquiringALifetime() {
        AtomicInteger detached = new AtomicInteger();
        assertThrows(IllegalArgumentException.class, () -> new ChunkSaveWatchState(detached::incrementAndGet));
        assertThrows(IllegalArgumentException.class,
                () -> new ChunkSaveWatchState(detached::incrementAndGet, 1L, 2L, 3L));
        assertThrows(NullPointerException.class, () -> new ChunkSaveWatchState(null, 1L));
        var state = new ChunkSaveWatchState(detached::incrementAndGet, 1L);
        assertThrows(IllegalArgumentException.class, () -> state.any(2L));
        assertThrows(IllegalArgumentException.class, () -> state.between(2L, 0L, 10L));
        assertEquals(0, detached.get());
    }
}
