package io.github.sunthemoon.advancedrocketrycommunity.resourceescrow;

import static org.junit.jupiter.api.Assertions.*;

import io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.CheckedSavedDataFile;
import io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.ManagedSavedDataType;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.CoarseDrainClock;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

@Timeout(15)
class EscrowLedgerDrainClockTest {
    @TempDir Path directory;
    @BeforeAll static void bootstrap() { MinecraftBootstrap.initialize(); }

    private EscrowLedgerWriter writer(CoarseDrainClock clock, CheckedSavedDataFile.Committer committer) {
        return new EscrowLedgerWriter(directory.resolve(ManagedSavedDataType.RESOURCE_ESCROW.fileName()),
                0, committer, cut -> { }, clock);
    }

    @Test void coarseWakeupsPreserveUnknownRetriesAndDoNotCaptureTheSuccessor() throws Exception {
        var clock = new CoarseDrainClock(); var base = EscrowTestFixtures.ledger();
        var outcomes = new ArrayList<EscrowLedgerWriter.Completion>(); var publishedAt = new ArrayList<Long>();
        Thread owner = Thread.currentThread();
        try (var writer = writer(clock, (staged, target) -> { throw new IOException("Injected unknown write"); })) {
            writer.tick(1_000_000);
            assertTrue(writer.request(() -> EscrowLedgerWriter.Snapshot.of(base.generation(1), Set.of()), completion -> {
                assertSame(owner, Thread.currentThread());
                outcomes.add(completion); publishedAt.add(clock.elapsedMillis());
                assertFalse(writer.request(() -> fail("Capture during drain"), result -> fail("Callback during drain")));
            }));
            assertTrue(writer.request(() -> fail("Unknown predecessor must retain the uncaptured successor"),
                    result -> fail("Unknown predecessor must not publish a successor")));
            clock.awaitWrites(); writer.drain(clock::nanoTime, clock::sleep);
            assertTrue(writer.idle()); assertTrue(writer.unavailable());
            assertEquals(EscrowLedgerWriter.DRAIN_UNKNOWN_ATTEMPTS, outcomes.size());
            assertEquals(List.of(0L, 200L, 425L), clock.starts());
            assertEquals(List.of(0L, 225L, 450L), publishedAt);
            assertEquals(1_000_425, writer.lastIoStart());
            assertEquals(475, clock.elapsedMillis(), "Timer rounding must not multiply the two 200-tick backoffs");
            assertTrue(outcomes.stream().allMatch(c -> c.generation() == 1 && c.outcome() == EscrowLedgerWriter.Outcome.UNKNOWN));
            assertFalse(writer.request(() -> fail("Escrow drain must keep requests closed"), outcomes::add));
        }
        assertTrue(clock.isTerminated());
    }

    @Test void coarseWakeupsPreserveTenTickCadenceAndCallbackBeforeSuccessorCapture() throws Exception {
        var clock = new CoarseDrainClock(); var base = EscrowTestFixtures.ledger();
        var outcomes = new ArrayList<EscrowLedgerWriter.Completion>();
        try (var writer = writer(clock, CheckedSavedDataFile::atomicMove)) {
            writer.tick(1_000_000);
            assertTrue(writer.request(() -> EscrowLedgerWriter.Snapshot.of(base.generation(1), Set.of()), outcomes::add));
            assertTrue(writer.request(() -> {
                assertEquals(List.of(1L), outcomes.stream().map(EscrowLedgerWriter.Completion::generation).toList());
                return EscrowLedgerWriter.Snapshot.of(base.generation(2), Set.of());
            }, outcomes::add));
            clock.awaitWrites(); writer.drain(clock::nanoTime, clock::sleep);
            assertEquals(List.of(1L, 2L), outcomes.stream().map(EscrowLedgerWriter.Completion::generation).toList());
            assertTrue(outcomes.stream().allMatch(c -> c.outcome() == EscrowLedgerWriter.Outcome.COMMITTED));
            assertEquals(List.of(0L, 25L), clock.starts());
            assertEquals(1_000_025, writer.lastIoStart()); assertEquals(75, clock.elapsedMillis());
        }
        assertTrue(clock.isTerminated());
    }

    @Test void interruptedWaitRestoresTheFlagAndAbortDoesNotPublishAnotherCompletion() throws Exception {
        var clock = new CoarseDrainClock(); var outcomes = new ArrayList<EscrowLedgerWriter.Completion>();
        var writer = writer(clock, (staged, target) -> { throw new IOException("Injected unknown write"); });
        try {
            writer.request(() -> EscrowLedgerWriter.Snapshot.of(EscrowTestFixtures.ledger().generation(1), Set.of()), outcomes::add);
            clock.awaitWrites();
            var failure = assertThrows(IllegalStateException.class, () -> writer.drain(clock::nanoTime,
                    nanos -> { throw new InterruptedException("Injected wait interruption"); }));
            assertEquals("Escrow shutdown drain interrupted", failure.getMessage());
            assertInstanceOf(InterruptedException.class, failure.getCause());
            assertTrue(Thread.currentThread().isInterrupted()); assertEquals(1, outcomes.size());
        } finally { Thread.interrupted(); writer.abort(); }
        assertEquals(1, outcomes.size()); assertEquals(1, clock.starts().size()); assertTrue(clock.isTerminated());
    }
}
