package io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.persistence;

import static org.junit.jupiter.api.Assertions.*;

import io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.CheckedSavedDataPolicy;
import io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.ManagedSavedDataType;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.CoarseDrainClock;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

@Timeout(15)
class RocketJournalDrainClockTest {
    private static final ManagedSavedDataType TYPE = ManagedSavedDataType.ROCKET_TRANSFERS;
    @TempDir Path directory;
    @BeforeAll static void bootstrap() { MinecraftBootstrap.initialize(); }

    private RocketJournalWriter writer(CoarseDrainClock clock) {
        return new RocketJournalWriter(directory.resolve(TYPE.fileName()), CheckedSavedDataPolicy.DirectoryForce.PLATFORM, clock);
    }
    private static RocketJournalWriter.Snapshot snapshot(long sequence) {
        return new RocketJournalWriter.Snapshot(sequence, List.of());
    }

    @Test void coarseWakeupsCountElapsedOwnerTicksForBothFailureOutcomes() throws Exception {
        for (boolean unknown : List.of(false, true)) {
            var clock = new CoarseDrainClock();
            var outcomes = new ArrayList<RocketJournalWriter.Completion>();
            var publishedAt = new ArrayList<Long>();
            Thread owner = Thread.currentThread();
            try (var writer = writer(clock)) {
                writer.tick(1_000_000, outcomes::add);
                assertTrue(writer.request(snapshot(1), new CheckedSavedDataPolicy(TYPE, (staged, target) -> {
                    if (unknown) { throw new AssertionError("Injected unknown write"); }
                    throw new IOException("Injected definite write failure");
                })));
                assertTrue(writer.request(snapshot(2), CheckedSavedDataPolicy.atomic(TYPE)));
                clock.awaitWrites();
                writer.drain(completion -> {
                    assertSame(owner, Thread.currentThread());
                    outcomes.add(completion); publishedAt.add(clock.elapsedMillis());
                    assertFalse(writer.request(snapshot(3), CheckedSavedDataPolicy.atomic(TYPE)));
                }, clock::nanoTime, clock::sleep);
                assertTrue(writer.idle());
                assertEquals(RocketJournalWriter.DRAIN_UNKNOWN_ATTEMPTS, outcomes.size());
                assertEquals(List.of(0L, 200L, 425L), clock.starts());
                assertEquals(List.of(0L, 225L, 450L), publishedAt);
                assertEquals(475, clock.elapsedMillis(), "Timer rounding must not multiply the two 200-tick backoffs");
                assertTrue(outcomes.stream().allMatch(c -> c.sequence() == 1 && c.outcome() == (unknown
                        ? RocketJournalCheckpoint.Outcome.UNKNOWN : RocketJournalCheckpoint.Outcome.NOT_COMMITTED)));
                assertTrue(writer.request(snapshot(3), CheckedSavedDataPolicy.atomic(TYPE)), "A journal drain reopens requests");
                writer.abort();
                assertFalse(writer.request(snapshot(4), CheckedSavedDataPolicy.atomic(TYPE)));
            }
        }
    }

    @Test void coarseWakeupsPreserveSuccessorPublicationOrderAtAHighOwnerTick() throws Exception {
        var clock = new CoarseDrainClock(); var outcomes = new ArrayList<RocketJournalWriter.Completion>();
        try (var writer = writer(clock)) {
            writer.tick(1_000_000, outcomes::add);
            assertTrue(writer.request(snapshot(1), CheckedSavedDataPolicy.atomic(TYPE)));
            assertTrue(writer.request(snapshot(2), CheckedSavedDataPolicy.atomic(TYPE)));
            clock.awaitWrites(); writer.drain(outcomes::add, clock::nanoTime, clock::sleep);
            assertEquals(List.of(1L, 2L), outcomes.stream().map(RocketJournalWriter.Completion::sequence).toList());
            assertTrue(outcomes.stream().allMatch(c -> c.outcome() == RocketJournalCheckpoint.Outcome.COMMITTED));
            assertEquals(List.of(0L, 0L), clock.starts());
            assertEquals(50, clock.elapsedMillis());
        }
        assertTrue(clock.isTerminated());
    }

    @Test void interruptedWaitRestoresTheFlagAndAbortDoesNotPublishAnotherCompletion() throws Exception {
        var clock = new CoarseDrainClock(); var outcomes = new ArrayList<RocketJournalWriter.Completion>();
        var writer = writer(clock);
        try {
            writer.request(snapshot(1), new CheckedSavedDataPolicy(TYPE, (staged, target) -> {
                throw new AssertionError("Injected unknown write");
            }));
            clock.awaitWrites();
            var failure = assertThrows(IllegalStateException.class, () -> writer.drain(outcomes::add,
                    clock::nanoTime, nanos -> { throw new InterruptedException("Injected wait interruption"); }));
            assertEquals("Journal drain interrupted", failure.getMessage());
            assertInstanceOf(InterruptedException.class, failure.getCause());
            assertTrue(Thread.currentThread().isInterrupted());
            assertEquals(1, outcomes.size());
        } finally { Thread.interrupted(); writer.abort(); }
        assertEquals(1, outcomes.size()); assertEquals(1, clock.starts().size()); assertTrue(clock.isTerminated());
    }
}
