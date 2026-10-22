package io.github.sunthemoon.advancedrocketrycommunity.persistence;

import static io.github.sunthemoon.advancedrocketrycommunity.persistence.CheckedRootWriter.Status.*;
import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.LongFunction;
import java.util.function.LongSupplier;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/** ADR-074 item 4 and design section 6.2: the shared writer core with a fake root and an executor seam. */
@Timeout(30)
class CheckedRootWriterTest {
    @Test void oneCaptureAtATimeAndARequestInFlightTakesAFreshCaptureAtTheNextStart() {
        var root = new FakeRoot(); var io = new ManualIo(); var writer = writer(root, io);
        root.mutate();
        assertTrue(writer.request("a", 0));
        assertEquals(List.of(2L), root.captures); assertTrue(writer.inFlight());
        root.mutate();
        assertTrue(writer.request("a", 1)); assertTrue(writer.request("a", 2));
        assertEquals(List.of(2L), root.captures, "A request in flight only sets the pending flag");
        assertEquals(1, io.queued());
        io.runAll(); writer.poll(3);
        assertEquals(List.of(2L), root.publishedSequences());
        assertTrue(root.dirty, "The older capture did not cover the newer mutation");
        writer.poll(9);
        assertEquals(List.of(2L), root.captures, "The 10-tick cadence holds the pending start");
        writer.poll(10);
        assertEquals(List.of(2L, 3L), root.captures, "The next start captures the then-current RAM");
        io.runAll(); writer.poll(11);
        assertFalse(root.dirty); assertFalse(writer.busy());
        assertNotSame(Thread.currentThread(), root.ioThread);
    }

    @Test void publicationIsOwnerOnlyAndAForeignCompletionIsRefused() throws Exception {
        var root = new FakeRoot(); var io = new ManualIo(); var writer = writer(root, io);
        root.mutate(); writer.request("a", 0);
        var refused = new AtomicBoolean();
        Thread other = new Thread(() -> {
            try { writer.poll(1); } catch (IllegalStateException expected) { refused.set(true); }
        });
        other.start(); other.join();
        assertTrue(refused.get(), "Another thread published or polled");
        assertThrows(IllegalStateException.class, () -> runOn(() -> writer.request("a", 1)));
        io.runAll(); writer.poll(1);
        assertEquals(1, root.published.size());
        io.rerunLast();
        assertThrows(IllegalStateException.class, () -> writer.poll(2), "A duplicate completion is foreign");
        assertEquals(1, root.published.size());
        root.mutate(); writer.poll(20); writer.request("a", 20);
        io.rerunLast();
        assertThrows(IllegalStateException.class, () -> writer.poll(21), "An earlier attempt's completion is foreign");
        io.runAll(); writer.poll(22);
        assertEquals(List.of(2L, 3L), root.publishedSequences());
    }

    @Test void anErrorOnTheWriterThreadIsUnknownWithOneErrorLineAndARuntimeExceptionIsAFailure() {
        var root = new FakeRoot(); var io = new ManualIo(); var writer = writer(root, io);
        try (var log = new LogLines(CheckedRootWriter.class)) {
            root.writer = sequence -> { throw new AssertionError("W129a injected writer Error"); };
            root.mutate(); writer.request("a", 0); io.runAll(); writer.poll(1);
            var unknown = root.published.get(0);
            assertEquals(UNKNOWN, unknown.status()); assertNull(unknown.payload());
            assertEquals(List.of("ERROR ARCE_CHECKED_WRITER_THROWABLE root=test sequence=2"), log.lines());
            root.writer = sequence -> { throw new IllegalStateException("W129a injected before replacement"); };
            writer.request("a", 400); io.runAll(); writer.poll(401);
            assertEquals(FAILED, root.published.get(1).status()); assertNull(root.published.get(1).payload());
            assertEquals(1, log.lines().size(), "A failure before replacement is not an unexpected Throwable");
        }
    }

    @Test void everyStartPathKeepsTheCadenceAndTheBackoffWhichRequestsCannotReset() {
        var root = new FakeRoot(); var io = new ManualIo(); var writer = writer(root, io);
        root.mutate(); writer.request("a", 100);
        root.writer = sequence -> new CheckedRootWriter.Attempt<>(FAILED, "failed");
        io.runAll(); writer.poll(120);
        assertEquals(FAILED, root.published.get(0).status());
        for (long tick = 121; tick < 320; tick += 7) {
            assertTrue(writer.request("a", tick)); writer.poll(tick);
        }
        writer.request("a", 319); writer.poll(319);
        assertEquals(List.of(2L), root.captures, "Requests and polls reset no backoff");
        writer.poll(320);
        assertEquals(List.of(2L, 2L), root.captures, "The pending request starts 200 ticks after the failure");
        root.writer = sequence -> new CheckedRootWriter.Attempt<>(COMMITTED, "ok");
        io.runAll(); writer.poll(321);
        root.mutate(); writer.request("a", 325);
        assertEquals(2, root.captures.size(), "A request also observes the 10-tick cadence");
        writer.request("a", 330);
        assertEquals(List.of(2L, 2L, 3L), root.captures, "A request at the cadence starts at once");
        io.runAll(); writer.poll(331);
        root.writer = sequence -> new CheckedRootWriter.Attempt<>(UNKNOWN, null);
        root.mutate(); writer.request("a", 400); io.runAll(); writer.poll(401);
        writer.request("a", 600);
        assertEquals(4, root.captures.size(), "UNKNOWN has the same backoff");
        writer.request("a", 601);
        assertEquals(5, root.captures.size());
    }

    @Test void aCommitClearsOnlyAnEqualPendingRequestAtOrBelowItsSequence() {
        var root = new FakeRoot(); var io = new ManualIo(); var writer = writer(root, io);
        root.mutate(); writer.request("a", 0);
        writer.request("a", 1);
        io.runAll(); writer.poll(2);
        assertFalse(writer.busy(), "A redundant request at the committed sequence is serviced");
        root.mutate(); writer.request("a", 20); writer.request("b", 21);
        io.runAll(); writer.poll(22);
        assertTrue(writer.busy(), "A request with other attributes is not serviced");
        writer.poll(30);
        assertEquals(List.of(2L, 3L, 3L), root.captures);
        io.runAll();
        assertEquals(List.of("a", "a", "b"), root.attributes);
        writer.poll(31);
        writer.request("a", 40); root.writer = sequence -> new CheckedRootWriter.Attempt<>(FAILED, null);
        writer.request("a", 41);
        io.runAll(); writer.poll(42);
        assertTrue(writer.busy(), "A failed attempt keeps the pending request for after the backoff");
    }

    @Test void shutdownRefusesRequestsAndTickStartsAndCountsAnUnpublishedAttempt() {
        var root = new FakeRoot(); var io = new ManualIo(); var writer = writer(root, io);
        root.mutate(); writer.request("a", 0); io.runAll();
        assertTrue(writer.completionReady());
        writer.latchShutdown(); writer.latchShutdown();
        assertFalse(writer.request("a", 50));
        writer.poll(50);
        assertTrue(root.published.isEmpty(), "Tick polling publishes nothing during shutdown");
        var clock = new VirtualClock(io);
        var report = writer.drain("a", clock, clock);
        assertTrue(report.success()); assertEquals(1, report.attempts(), "The completed but unpublished attempt counts");
        assertEquals(List.of(2L), root.captures);
    }

    @Test void anOlderOutcomeCannotFinishTheDrainAndAFreshFinalCaptureCoversTheTarget() {
        for (var older : List.of(COMMITTED, FAILED, UNKNOWN)) {
            var root = new FakeRoot(); var io = new ManualIo(); var writer = writer(root, io);
            root.writer = sequence -> new CheckedRootWriter.Attempt<>(sequence == 2 ? older : COMMITTED, null);
            root.mutate(); writer.request("a", 0);
            root.mutate();
            writer.latchShutdown();
            var clock = new VirtualClock(io);
            var report = writer.drain("a", clock, clock);
            assertTrue(report.success(), older + ": " + report);
            assertEquals(2, report.attempts()); assertEquals(3, report.target()); assertEquals(3, report.committed());
            assertEquals(List.of(2L, 3L), root.captures, older + " retained the successor work");
            assertEquals(List.of(2L, 3L), root.publishedSequences());
            if (older != COMMITTED) { assertTrue(clock.elapsedMillis() >= CheckedRootWriter.BACKOFF_TICKS); }
        }
    }

    @Test void oneBudgetSpansDrainCloseTargetChangesAndExceptionsAndExhaustionClaimsNothing() {
        var root = new FakeRoot(); var io = new ManualIo(); var writer = writer(root, io);
        try (var log = new LogLines(CheckedRootWriter.class)) {
            root.writer = sequence -> new CheckedRootWriter.Attempt<>(sequence <= 2 ? COMMITTED : FAILED, null);
            root.mutate(); writer.request("a", 0); io.runAll(); writer.poll(1);
            root.mutate(); writer.request("a", 20);
            root.failNextPublish = true;
            var clock = new VirtualClock(io);
            var thrown = writer.drain("a", clock, clock);
            assertFalse(thrown.success()); assertEquals("IllegalStateException", thrown.reason());
            assertEquals(1, thrown.attempts());
            var exhausted = writer.drain("a", clock, clock);
            assertFalse(exhausted.success()); assertEquals("BUDGET_EXHAUSTED", exhausted.reason());
            assertEquals(3, exhausted.attempts()); assertEquals(3, exhausted.target()); assertEquals(2, exhausted.committed());
            root.mutate();
            var closed = writer.close("a", clock, clock);
            assertFalse(closed.success()); assertEquals("BUDGET_EXHAUSTED", closed.reason());
            assertEquals(4, closed.target(), "A newer target is reported, without a new budget");
            assertEquals(3, closed.attempts()); assertEquals(List.of(2L, 3L, 3L, 3L), root.captures);
            assertTrue(root.dirty);
            assertEquals(List.of("WARN ARCE_CHECKED_WRITER_DRAIN_INCOMPLETE root=test target=3 committed=2 attempts=1 "
                    + "reason=IllegalStateException"), log.lines(), "One WARN per shutdown");
            assertFalse(writer.request("a", 900)); assertTrue(writer.closed());
            assertSame(closed, writer.abort());
        }
    }

    @Test void abortJoinsAStalledWriterWithoutPublishingAndKeepsAnInterruption() throws Exception {
        var root = new FakeRoot(); var entered = new CountDownLatch(1); var release = new CountDownLatch(1);
        var finished = new AtomicBoolean();
        root.writer = sequence -> {
            entered.countDown(); awaitUninterruptibly(release); finished.set(true);
            return new CheckedRootWriter.Attempt<>(COMMITTED, null);
        };
        var writer = new CheckedRootWriter<>("test", root, CheckedRootWriter.ioThread("ARCE-test-stalled"));
        root.mutate(); writer.request("a", 0);
        assertTrue(entered.await(10, TimeUnit.SECONDS));
        var released = new AtomicBoolean(); var returned = new AtomicBoolean();
        Thread helper = releaseAfterInterrupt(Thread.currentThread(), returned, released, release);
        var report = writer.abort(); returned.set(true);
        assertTrue(released.get(), "Abort returned while IO could still replace the file");
        assertTrue(finished.get());
        assertTrue(Thread.interrupted(), "The interruption was not preserved for the caller");
        helper.join();
        assertFalse(report.success()); assertEquals("ABORTED_UNRESOLVED", report.reason());
        assertTrue(root.published.isEmpty(), "Abort published a completion");
        assertFalse(writer.request("a", 1));
    }

    @Test void anInterruptedCloseReportsIncompleteAndJoinsBeforeReturning() throws Exception {
        var root = new FakeRoot(); var entered = new CountDownLatch(1); var release = new CountDownLatch(1);
        var finished = new AtomicBoolean();
        root.writer = sequence -> {
            entered.countDown(); awaitUninterruptibly(release); finished.set(true);
            return new CheckedRootWriter.Attempt<>(COMMITTED, null);
        };
        var writer = new CheckedRootWriter<>("test", root, CheckedRootWriter.ioThread("ARCE-test-interrupted"));
        root.mutate(); writer.request("a", 0);
        assertTrue(entered.await(10, TimeUnit.SECONDS));
        var released = new AtomicBoolean(); var returned = new AtomicBoolean();
        Thread helper = releaseAfterInterrupt(Thread.currentThread(), returned, released, release);
        var report = writer.close("a", System::nanoTime, TimeUnit.NANOSECONDS::sleep); returned.set(true);
        assertTrue(released.get(), "Close returned while IO could still replace the file");
        assertTrue(finished.get()); assertTrue(Thread.interrupted());
        helper.join();
        assertFalse(report.success()); assertEquals("INTERRUPTED", report.reason()); assertEquals(1, report.attempts());
        assertTrue(root.published.isEmpty(), "An interrupted teardown published after giving up");
        assertTrue(root.dirty); assertSame(report, writer.close("a", System::nanoTime, TimeUnit.NANOSECONDS::sleep));
    }

    @Test void aDetachedAwaitPumpsPublicationOnTheMillisecondClockAndHonoursTheCadence() {
        var root = new FakeRoot(); var io = new ManualIo(); var writer = writer(root, io);
        var clock = new VirtualClock(io);
        root.mutate(); writer.request("a", 0); writer.await(clock, clock);
        assertFalse(writer.busy()); assertFalse(root.dirty);
        root.mutate(); writer.request("a", 0); writer.await(clock, clock);
        assertEquals(List.of(2L, 3L), root.publishedSequences());
        assertTrue(clock.starts.get(1) - clock.starts.get(0) >= CheckedRootWriter.CADENCE_TICKS);
        writer.latchShutdown();
        assertThrows(IllegalStateException.class, () -> writer.await(clock, clock));
    }

    private static CheckedRootWriter<Long, String, String> writer(FakeRoot root, ManualIo io) {
        return new CheckedRootWriter<>("test", root, io);
    }

    private static Thread releaseAfterInterrupt(Thread owner, AtomicBoolean returned, AtomicBoolean released,
                                                CountDownLatch release) {
        Thread helper = new Thread(() -> {
            try {
                while (owner.getState() != Thread.State.TIMED_WAITING && owner.getState() != Thread.State.WAITING) {
                    Thread.sleep(5);
                }
                Thread.sleep(100); owner.interrupt();
                Thread.sleep(300);
                if (!returned.get()) { released.set(true); }
            } catch (InterruptedException stop) {
                Thread.currentThread().interrupt();
            } finally { release.countDown(); }
        }, "W129a-release");
        helper.start();
        return helper;
    }

    private static void awaitUninterruptibly(CountDownLatch latch) {
        boolean interrupted = false;
        while (true) {
            try { latch.await(); break; } catch (InterruptedException again) { interrupted = true; }
        }
        if (interrupted) { Thread.currentThread().interrupt(); }
    }

    private static void runOn(Runnable task) throws Exception {
        var failure = new java.util.concurrent.atomic.AtomicReference<RuntimeException>();
        Thread thread = new Thread(() -> { try { task.run(); } catch (RuntimeException f) { failure.set(f); } });
        thread.start(); thread.join();
        if (failure.get() != null) { throw failure.get(); }
    }

    /** An in-memory root: a sequence, vanilla's dirty flag and a writer function the test replaces. */
    static final class FakeRoot implements CheckedRootWriter.Root<Long, String, String> {
        long sequence = 1;
        boolean dirty, failNextPublish;
        final List<Long> captures = new ArrayList<>();
        final List<String> attributes = new CopyOnWriteArrayList<>();
        final List<CheckedRootWriter.Completion<String>> published = new ArrayList<>();
        volatile LongFunction<CheckedRootWriter.Attempt<String>> writer = s -> new CheckedRootWriter.Attempt<>(COMMITTED, "ok");
        volatile Thread ioThread;

        void mutate() { sequence++; dirty = true; }
        List<Long> publishedSequences() { return published.stream().map(CheckedRootWriter.Completion::sequence).toList(); }
        @Override public long mutationSequence() { return sequence; }
        @Override public Long capture(long at) { captures.add(at); return at; }
        @Override public CheckedRootWriter.Attempt<String> write(Long capture, String attempted) {
            ioThread = Thread.currentThread(); attributes.add(attempted); return writer.apply(capture);
        }
        @Override public void publish(CheckedRootWriter.Completion<String> completion) {
            if (failNextPublish) { failNextPublish = false; throw new IllegalStateException("W129a injected publish"); }
            published.add(completion);
            if (completion.status() == COMMITTED && completion.sequence() == sequence) { dirty = false; }
        }
        @Override public boolean needsWrite() { return dirty; }
        @Override public String merge(String pending, String later) { return later; }
        @Override public boolean serviced(String attempted, String requested) { return attempted.equals(requested); }
    }

    /** Queues IO; the test runs each task on its own helper thread, never the owner thread. */
    static final class ManualIo extends AbstractExecutorService {
        private final ArrayDeque<Runnable> queued = new ArrayDeque<>();
        private final List<Runnable> ran = new ArrayList<>();
        private boolean shut;

        @Override public synchronized void execute(Runnable task) {
            if (shut) { throw new RejectedExecutionException("shut down"); }
            queued.add(task);
        }
        synchronized int queued() { return queued.size(); }
        void runAll() {
            while (true) {
                Runnable task;
                synchronized (this) { task = queued.poll(); if (task != null) { ran.add(task); } }
                if (task == null) { return; }
                runOnHelper(task);
            }
        }
        void rerunLast() { runOnHelper(ran.get(ran.size() - 1)); }
        private static void runOnHelper(Runnable task) {
            Thread thread = new Thread(task, "W129a-manual-io"); thread.start();
            try { thread.join(); } catch (InterruptedException stop) { Thread.currentThread().interrupt(); }
        }
        @Override public synchronized void shutdown() { shut = true; }
        @Override public synchronized List<Runnable> shutdownNow() { shut = true; var left = List.copyOf(queued); queued.clear(); return left; }
        @Override public synchronized boolean isShutdown() { return shut; }
        @Override public synchronized boolean isTerminated() { return shut && queued.isEmpty(); }
        @Override public boolean awaitTermination(long timeout, TimeUnit unit) { runAll(); return isTerminated(); }
    }

    /** A virtual monotonic clock whose waits run the queued IO first, as the drain's real clock would. */
    static final class VirtualClock implements LongSupplier, CheckedRootWriter.DrainWait {
        private final ManualIo io; private long nanos = 1_000L;
        final List<Long> starts = new ArrayList<>();
        VirtualClock(ManualIo io) { this.io = io; }
        long elapsedMillis() { return TimeUnit.NANOSECONDS.toMillis(nanos); }
        @Override public long getAsLong() { return nanos; }
        @Override public void sleep(long wait) {
            if (wait <= 0 || wait > 1_000_000) { throw new AssertionError("Wait to the next millisecond: " + wait); }
            int queued = io.queued(); for (int i = 0; i < queued; i++) { starts.add(elapsedMillis()); }
            io.runAll(); nanos += wait;
        }
    }

    /** ERROR, WARN and INFO lines of one logger, as "LEVEL message". */
    static final class LogLines implements AutoCloseable {
        private final org.apache.logging.log4j.core.Logger logger;
        private final org.apache.logging.log4j.core.appender.AbstractAppender appender;
        private final List<String> lines = new CopyOnWriteArrayList<>();
        LogLines(Class<?> type) {
            logger = (org.apache.logging.log4j.core.Logger) org.apache.logging.log4j.LogManager.getLogger(type);
            appender = new org.apache.logging.log4j.core.appender.AbstractAppender("w129a-" + type.getSimpleName(),
                    null, null, true, org.apache.logging.log4j.core.config.Property.EMPTY_ARRAY) {
                @Override public void append(org.apache.logging.log4j.core.LogEvent event) {
                    if (event.getLevel().isMoreSpecificThan(org.apache.logging.log4j.Level.WARN)) {
                        lines.add(event.getLevel() + " " + event.getMessage().getFormattedMessage());
                    }
                }
            };
            appender.start(); logger.addAppender(appender);
        }
        List<String> lines() { return List.copyOf(lines); }
        @Override public void close() { logger.removeAppender(appender); appender.stop(); }
    }
}
