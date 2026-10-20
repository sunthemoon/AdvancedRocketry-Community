package io.github.sunthemoon.advancedrocketrycommunity.resourceescrow;

import static org.junit.jupiter.api.Assertions.*;

import io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.CheckedSavedDataFile;
import io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.ManagedSavedDataType;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * W107C (Root decision R43 item 4): cut E4's stale writer callback. It is an in-process invariant with no
 * restart-observable state (the R-032 class), so this pins {@code EscrowLedgerWriter.tick}'s refusal as a residual,
 * not an S2 PASS: generation 1's IO task delivers its callback again after generation 2 is captured.
 */
class EscrowLedgerWriterStaleCallbackTest {
    @BeforeAll static void bootstrap() { MinecraftBootstrap.initialize(); }
    @TempDir Path directory;

    /** The awaited test IO thread, which also keeps every submitted task so that one can be delivered twice. */
    private static final class ReplayingIo extends AbstractExecutorService {
        final AwaitedLedgerIo io = new AwaitedLedgerIo();
        final List<Runnable> tasks = new CopyOnWriteArrayList<>();
        @Override public void execute(Runnable task) { tasks.add(task); io.execute(task); }
        void replay(int index) { io.execute(tasks.get(index)); }
        @Override public void shutdown() { io.shutdown(); }
        @Override public List<Runnable> shutdownNow() { return io.shutdownNow(); }
        @Override public boolean isShutdown() { return io.isShutdown(); }
        @Override public boolean isTerminated() { return io.isTerminated(); }
        @Override public boolean awaitTermination(long timeout, TimeUnit unit) throws InterruptedException {
            return io.awaitTermination(timeout, unit);
        }
    }

    @Test void staleCallbackAfterItsSuccessorIsRefusedWithoutAcknowledgingAnything() {
        var io = new ReplayingIo(); var moves = new AtomicInteger();
        var file = directory.resolve(ManagedSavedDataType.RESOURCE_ESCROW.fileName());
        var first = EscrowLedger.empty(UUID.randomUUID()).generation(1); var second = first.generation(2);
        var completions = new ArrayList<EscrowLedgerWriter.Completion>();
        try (var writer = new EscrowLedgerWriter(file, 0, (staged, target) -> {
            // The replayed generation-1 task never moves again, so the file keeps the latest acknowledged generation.
            if (moves.incrementAndGet() == 2) { throw new java.io.IOException("Replayed stale callback does not move"); }
            CheckedSavedDataFile.atomicMove(staged, target);
        }, cut -> { }, io)) {
            assertTrue(writer.request(() -> EscrowLedgerWriter.Snapshot.of(first, Set.of()), completions::add));
            io.io.awaitWrites(); writer.tick(1);
            assertEquals(1, completions.size());
            assertEquals(EscrowLedgerWriter.Outcome.COMMITTED, completions.get(0).outcome());
            // The successor is captured but waits for the ten-tick IO cadence, so its own task has not started.
            assertTrue(writer.request(() -> EscrowLedgerWriter.Snapshot.of(second, Set.of()), completions::add));
            assertEquals(1, io.tasks.size());
            assertEquals(2, writer.lastGeneration());

            io.replay(0); io.io.awaitWrites();
            var refusal = assertThrows(IllegalArgumentException.class, () -> writer.tick(2));
            assertEquals("Stale or foreign writer callback", refusal.getMessage());
            assertEquals(1, completions.size(), "The stale callback acknowledged nothing");
            assertFalse(writer.idle()); assertFalse(writer.unknown()); assertFalse(writer.unavailable());
            assertEquals(first, EscrowCodec.decode(CheckedSavedDataFile.readPayload(file, ManagedSavedDataType.RESOURCE_ESCROW).orElseThrow()));

            // The successor still completes normally through its own exact callback.
            io.io.tickUntil(writer::tick, 3, writer::idle);
            assertTrue(writer.idle());
            assertEquals(List.of(1L, 2L), completions.stream().map(EscrowLedgerWriter.Completion::generation).toList());
            assertEquals(EscrowLedgerWriter.Outcome.COMMITTED, completions.get(1).outcome());
            assertEquals(second, EscrowCodec.decode(CheckedSavedDataFile.readPayload(file, ManagedSavedDataType.RESOURCE_ESCROW).orElseThrow()));
            assertEquals(3, moves.get());
        }
    }
}
