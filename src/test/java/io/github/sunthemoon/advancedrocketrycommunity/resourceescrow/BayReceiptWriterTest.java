package io.github.sunthemoon.advancedrocketrycommunity.resourceescrow;

import static io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.BayReceiptFixtures.*;
import static org.junit.jupiter.api.Assertions.*;

import io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.CheckedSavedDataFile;
import io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.ManagedSavedDataType;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import java.io.IOError;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** W27c1 A1: the checked receipt writer on real files with injected committers. */
final class BayReceiptWriterTest {
    @TempDir Path temp;
    @BeforeAll static void bootstrap() { MinecraftBootstrap.initialize(); }

    private Path file() { return temp.resolve(ManagedSavedDataType.BAY_RECEIPTS.fileName()); }
    private static BayReceiptRoot generation(long generation, long receipts) {
        var list = new ArrayList<BayReceipt>();
        for (long i = 1; i <= receipts; i++) { list.add(receipt(i, id(9), id(50 + i), BayReceipt.Phase.INTENT)); }
        return new BayReceiptRoot(STORE, receipts, generation, list);
    }
    private static CheckedSavedDataFile.Committer switchable(AtomicReference<CheckedSavedDataFile.Committer> mode) {
        return (staged, target) -> mode.get().commit(staged, target);
    }
    private static final CheckedSavedDataFile.Committer AMBIGUOUS = (staged, target) -> { throw new IOException("Injected ambiguous replacement"); };
    private static final CheckedSavedDataFile.Committer MOVE_THEN_THROW = (staged, target) -> {
        CheckedSavedDataFile.atomicMove(staged, target); throw new IOException("Injected failure after replacement");
    };

    @Test void theServerThreadCapturesAndTheIoThreadEncodesDigestsWritesAndReadsBack() throws Exception {
        var io = new AwaitedLedgerIo(); var ioThread = new AtomicReference<Thread>();
        var completions = new ArrayList<BayReceiptWriter.Completion>(); var owner = Thread.currentThread();
        var captureThread = new AtomicReference<Thread>();
        try (var writer = new BayReceiptWriter(file(), STORE, 0, (staged, target) -> {
                ioThread.set(Thread.currentThread()); CheckedSavedDataFile.atomicMove(staged, target);
            }, io)) {
            var root = generation(1, 3);
            assertTrue(writer.request(() -> { captureThread.set(Thread.currentThread()); return root; }, completions::add));
            io.tickUntil(writer::tick, 1, writer::idle);
            assertSame(owner, captureThread.get()); assertNotSame(owner, ioThread.get());
            assertEquals(1, completions.size()); var completion = completions.get(0);
            assertEquals(BayReceiptWriter.Outcome.COMMITTED, completion.outcome()); assertEquals(1, completion.generation());
            assertEquals(BayReceiptCodec.digest(BayReceiptCodec.encode(root)), completion.digest());
            assertEquals(BayReceiptCodec.encode(root), CheckedSavedDataFile.readPayload(file(), ManagedSavedDataType.BAY_RECEIPTS).orElseThrow());
            assertEquals(1, writer.durableGeneration()); assertFalse(writer.unavailable());
            assertThrows(IllegalArgumentException.class, () -> writer.request(() -> generation(1, 1), completions::add),
                    "a generation never repeats");
        }
    }

    @Test void anUnknownOutcomeRetriesTheExactCandidateEvery200TicksAndResolvesOnTheOwnerThread() throws Exception {
        var mode = new AtomicReference<>(AMBIGUOUS); var io = new AwaitedLedgerIo(); var starts = new ArrayList<Long>();
        var completions = new ArrayList<BayReceiptWriter.Completion>();
        try (var writer = new BayReceiptWriter(file(), STORE, 0, switchable(mode), io)) {
            assertTrue(writer.request(() -> generation(1, 2), completions::add));
            long tick = 1; long last = writer.lastIoStart(); starts.add(last); var observed = new ArrayList<Long>();
            while (starts.size() < 3 && tick < AwaitedLedgerIo.TICK_BOUND) {
                io.awaitWrites(); int before = completions.size(); writer.tick(tick);
                if (completions.size() > before) { observed.add(tick); }
                if (writer.lastIoStart() != last) { last = writer.lastIoStart(); starts.add(last); }
                tick++;
            }
            assertEquals(List.of(observed.get(0) + 200, observed.get(1) + 200), starts.subList(1, 3),
                    "the exact candidate is retried 200 ticks after each unknown outcome");
            io.awaitWrites(); writer.tick(tick++);
            assertTrue(completions.size() >= 3 && completions.stream().allMatch(c -> c.outcome() == BayReceiptWriter.Outcome.UNKNOWN));
            assertEquals(1, completions.stream().map(BayReceiptWriter.Completion::digest).distinct().count(), "one exact digest");
            assertTrue(writer.unavailable(), "three unresolved failures make the store unavailable");
            assertTrue(Files.notExists(file()));
            mode.set(CheckedSavedDataFile::atomicMove);
            io.tickUntil(writer::tick, tick, writer::idle);
            var last2 = completions.get(completions.size() - 1);
            assertEquals(BayReceiptWriter.Outcome.COMMITTED, last2.outcome()); assertEquals(completions.get(0).digest(), last2.digest());
            assertFalse(writer.unavailable(), "an exact acknowledgement restores availability");
        }
    }

    @Test void aReplacementThatThrowsAfterTheMoveResolvesByBoundedReadback() throws Exception {
        var io = new AwaitedLedgerIo(); var completions = new ArrayList<BayReceiptWriter.Completion>();
        try (var writer = new BayReceiptWriter(file(), STORE, 0, MOVE_THEN_THROW, io)) {
            writer.request(() -> generation(1, 1), completions::add);
            io.tickUntil(writer::tick, 1, writer::idle);
            assertEquals(List.of(BayReceiptWriter.Outcome.COMMITTED), completions.stream().map(BayReceiptWriter.Completion::outcome).toList());
        }
    }

    @Test void aDefiniteFailureBeforeReplacementCountsButAGuardRefusalDoesNot() throws Exception {
        var io = new AwaitedLedgerIo(); var completions = new ArrayList<BayReceiptWriter.Completion>();
        try (var writer = new BayReceiptWriter(file(), STORE, 0, CheckedSavedDataFile::atomicMove, io)) {
            writer.request(() -> generation(1, 1), completions::add, () -> false);
            long tick = io.tickUntil(writer::tick, 1, writer::idle);
            assertTrue(Files.notExists(file()) && Files.notExists(temp.resolve(file().getFileName() + ".arce-pending")));
            Files.createDirectories(file()); // The target cannot be replaced: refused before any staging or move.
            for (long generation = 2; generation <= 4; generation++) {
                long next = generation; writer.request(() -> generation(next, 1), completions::add);
                tick = io.tickUntil(writer::tick, tick, writer::idle);
            }
            assertEquals(List.of(BayReceiptWriter.Outcome.GUARD_REFUSED, BayReceiptWriter.Outcome.FAILED_BEFORE_REPLACE,
                    BayReceiptWriter.Outcome.FAILED_BEFORE_REPLACE, BayReceiptWriter.Outcome.FAILED_BEFORE_REPLACE),
                    completions.stream().map(BayReceiptWriter.Completion::outcome).toList());
            assertTrue(writer.unavailable(), "three definite failures, not counting the guard refusal");
            assertTrue(Files.isDirectory(file()));
        }
    }

    @Test void writesStartAtMostOncePerTenTicksWithOneCapturedGenerationAndOneSuccessor() throws Exception {
        var io = new AwaitedLedgerIo(); var completions = new ArrayList<BayReceiptWriter.Completion>(); var captures = new AtomicInteger();
        try (var writer = new BayReceiptWriter(file(), STORE, 0, CheckedSavedDataFile::atomicMove, io)) {
            writer.tick(100);
            assertTrue(writer.request(() -> { captures.incrementAndGet(); return generation(1, 1); }, completions::add));
            assertTrue(writer.request(() -> { captures.incrementAndGet(); return generation(2, 2); }, completions::add));
            assertFalse(writer.request(() -> generation(3, 3), completions::add), "one successor only");
            assertEquals(1, captures.get(), "the successor captures only after the active generation completes");
            assertEquals(100, writer.lastIoStart());
            io.awaitWrites(); writer.tick(101);
            assertEquals(2, captures.get()); assertEquals(100, writer.lastIoStart(), "the successor waits for the cadence");
            for (long tick = 102; tick < 110; tick++) { io.awaitWrites(); writer.tick(tick); assertEquals(100, writer.lastIoStart()); }
            io.awaitWrites(); writer.tick(110); assertEquals(110, writer.lastIoStart());
            io.tickUntil(writer::tick, 111, writer::idle);
            assertEquals(List.of(1L, 2L), completions.stream().map(BayReceiptWriter.Completion::generation).toList());
        }
    }

    @Test void theStopDrainPublishesActiveAndSuccessorAndTheCrashPathPublishesNothing() throws Exception {
        var completions = new ArrayList<BayReceiptWriter.Completion>();
        var writer = new BayReceiptWriter(file(), STORE, 0, CheckedSavedDataFile::atomicMove);
        writer.request(() -> generation(1, 1), completions::add); writer.request(() -> generation(2, 2), completions::add);
        writer.close();
        assertEquals(List.of(1L, 2L), completions.stream().map(BayReceiptWriter.Completion::generation).toList());
        assertFalse(writer.request(() -> generation(3, 3), completions::add), "a stopped writer refuses requests");
        var before = Files.readAllBytes(file());
        var crashed = new BayReceiptWriter(file(), STORE, 2, (staged, target) -> { throw new IOException("never reached"); });
        crashed.request(() -> generation(3, 3), completions::add);
        crashed.abort();
        assertEquals(2, completions.size(), "the crash path publishes no completion");
        assertArrayEquals(before, Files.readAllBytes(file()), "the target file stays authoritative");
    }

    @Test void theStopDrainGivesUpAfterThreeUnknownOutcomesAndLeavesTheTargetFile() throws Exception {
        var completions = new ArrayList<BayReceiptWriter.Completion>();
        var writer = new BayReceiptWriter(file(), STORE, 0, AMBIGUOUS);
        writer.request(() -> generation(1, 1), completions::add);
        long started = System.nanoTime(); writer.close();
        assertTrue(System.nanoTime() - started < 30_000_000_000L);
        assertTrue(completions.size() >= 3 && completions.stream().allMatch(c -> c.outcome() == BayReceiptWriter.Outcome.UNKNOWN));
        assertTrue(Files.notExists(file()));
    }

    /** W27C1-R6: an Error on the IO thread is an unknown outcome of the exact candidate, and a stop drain still ends. */
    @Test void anErrorOnTheIoThreadIsAnUnknownOutcomeOfTheExactCandidateAndTheStopDrainStillEnds() throws Exception {
        CheckedSavedDataFile.Committer error = (staged, target) -> { throw new IOError(new IOException("Injected IO error")); };
        var mode = new AtomicReference<>(error); var io = new AwaitedLedgerIo(); var completions = new ArrayList<BayReceiptWriter.Completion>();
        try (var writer = new BayReceiptWriter(file(), STORE, 0, switchable(mode), io)) {
            assertTrue(writer.request(() -> generation(1, 2), completions::add));
            long tick = io.tickUntil(writer::tick, 1, () -> !completions.isEmpty());
            assertEquals(BayReceiptWriter.Outcome.UNKNOWN, completions.get(0).outcome());
            assertEquals(BayReceiptCodec.digest(BayReceiptCodec.encode(generation(1, 2))), completions.get(0).digest(),
                    "the candidate existed before the error, so it is the exact candidate");
            assertTrue(writer.unknown() && !writer.idle());
            mode.set(CheckedSavedDataFile::atomicMove);
            io.tickUntil(writer::tick, tick, writer::idle);
            var last = completions.get(completions.size() - 1);
            assertEquals(BayReceiptWriter.Outcome.COMMITTED, last.outcome()); assertEquals(completions.get(0).digest(), last.digest());
        }
        byte[] durable = Files.readAllBytes(file());
        // The production IO thread with an Error on every attempt: the drain gives up after three unknown outcomes.
        var drained = assertTimeoutPreemptively(Duration.ofSeconds(60), () -> {
            var outcomes = new ArrayList<BayReceiptWriter.Outcome>();
            var writer = new BayReceiptWriter(file(), STORE, 1, error);
            assertTrue(writer.request(() -> generation(2, 1), completion -> outcomes.add(completion.outcome())));
            writer.close();
            return outcomes;
        });
        assertTrue(drained.size() >= 3 && drained.stream().allMatch(BayReceiptWriter.Outcome.UNKNOWN::equals), drained::toString);
        assertArrayEquals(durable, Files.readAllBytes(file()), "the target file stays authoritative");
    }

    @Test void theStartupBaselineMustReadBackAsTheLoadedRoot() throws Exception {
        var root = generation(1, 2);
        CheckedSavedDataFile.replace(file(), ManagedSavedDataType.BAY_RECEIPTS, () -> BayReceiptCodec.encode(root));
        try (var writer = new BayReceiptWriter(file(), STORE, 1, CheckedSavedDataFile::atomicMove)) {
            assertTrue(writer.baseline(root)); assertFalse(writer.unavailable());
        }
        try (var writer = new BayReceiptWriter(file(), STORE, 1, CheckedSavedDataFile::atomicMove)) {
            assertFalse(writer.baseline(new BayReceiptRoot(STORE, 2, 1, root.receipts().subList(0, 1))));
            assertTrue(writer.unavailable()); assertFalse(writer.request(() -> generation(2, 1), c -> { }));
        }
    }
}
