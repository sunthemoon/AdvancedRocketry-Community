package io.github.sunthemoon.advancedrocketrycommunity.persistence;

import com.mojang.logging.LogUtils;
import java.lang.management.ManagementFactory;
import java.lang.management.ThreadMXBean;
import java.util.Objects;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.function.LongSupplier;
import org.slf4j.Logger;

/**
 * ADR-074 item 4: one checked writer per root, owned by that root for one server lifetime; no static state. The owner
 * thread captures and publishes. One daemon IO thread encodes, checks and replaces, and reads only the capture. At
 * most one capture exists; a request while it is in flight leaves one pending flag, and every attempt captures fresh.
 */
public final class CheckedRootWriter<C, A, P> {
    public static final int CADENCE_TICKS = 10, BACKOFF_TICKS = 200, SHUTDOWN_ATTEMPTS = 3;
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final long NEVER = Long.MIN_VALUE / 4;

    public enum Status { COMMITTED, FAILED, UNKNOWN }

    /** The root's half. Only {@link #write} runs on the IO thread, and it may read nothing but its capture. */
    public interface Root<C, A, P> {
        /** Owner thread: the root's current mutation sequence. */
        long mutationSequence();
        /** Owner thread: an immutable capture of the current RAM at {@code sequence}. It builds no NBT. */
        C capture(long sequence);
        /** IO thread. A RuntimeException means nothing was replaced (FAILED); any other Throwable gives UNKNOWN. */
        Attempt<P> write(C capture, A attributes);
        /** Owner thread: apply the root's outcome rules. The payload is null when the attempt threw. */
        void publish(Completion<P> completion);
        /** Owner thread: RAM holds work that no published commit covers (vanilla's dirty flag for a journal). */
        boolean needsWrite();
        /** A later request's attributes merged into the pending ones. */
        A merge(A pending, A later);
        /** Whether an attempt made with {@code attempted} serviced a request for {@code requested}. */
        boolean serviced(A attempted, A requested);
    }

    public record Attempt<P>(Status status, P payload) {
        public Attempt { Objects.requireNonNull(status, "status"); }
    }

    /** {@code attempt} names one capture; a completion of any other attempt is foreign. */
    public record Completion<P>(long attempt, long sequence, Status status, P payload, long writerNanos,
                                long writerCpuNanos) { }

    /** A stop-path drain, close or abort. {@code success} only for covered work with nothing active or pending. */
    public record DrainReport(boolean success, long target, long committed, int attempts, String reason) { }

    @FunctionalInterface
    public interface DrainWait { void sleep(long nanos) throws InterruptedException; }

    private record Active<A>(long attempt, long sequence, A attributes) { }

    private final Thread owner = Thread.currentThread();
    private final String label;
    private final Root<C, A, P> root;
    private final ExecutorService io;
    private final ArrayBlockingQueue<Completion<P>> completions = new ArrayBlockingQueue<>(1);
    private Active<A> active;
    private A pending;
    private long pendingSequence, attempts, now, lastStart = NEVER, failedAt = NEVER, lastCommitted;
    private boolean shutdown, closed, warned, drainClock;
    private int shutdownAttempts;
    private long target, drainBase, drainOrigin;
    private DrainReport last;

    public CheckedRootWriter(String label, String threadName, Root<C, A, P> root) {
        this(label, root, ioThread(threadName));
    }

    /** Unit-test seam: the executor that runs the IO attempts. */
    public CheckedRootWriter(String label, Root<C, A, P> root, ExecutorService io) {
        this.label = Objects.requireNonNull(label, "label");
        this.root = Objects.requireNonNull(root, "root");
        this.io = Objects.requireNonNull(io, "io");
    }

    /** One daemon thread at most; it ends after a minute without work, so detached test roots leak none. */
    public static ExecutorService ioThread(String name) {
        var executor = new ThreadPoolExecutor(1, 1, 60, TimeUnit.SECONDS, new LinkedBlockingQueue<>(), task -> {
            Thread thread = new Thread(task, name); thread.setDaemon(true); return thread;
        });
        executor.allowCoreThreadTimeOut(true);
        return executor;
    }

    /**
     * Merges into the one pending flag and starts at once if the writer is idle and the cadence allows. Refused after
     * shutdown or close. Repeated requests never reset the cadence or the failure backoff.
     */
    public boolean request(A attributes, long tick) {
        owner(); Objects.requireNonNull(attributes, "attributes"); observe(tick);
        if (closed || shutdown) { return false; }
        pending = pending == null ? attributes : root.merge(pending, attributes);
        pendingSequence = root.mutationSequence();
        if (active == null && mayStart()) { start(); }
        return true;
    }

    /** Tick START: publish at most one completion, then start the pending request if the cadence allows. */
    public void poll(long tick) {
        owner(); observe(tick);
        if (shutdown) { return; }
        Completion<P> completion = completions.poll();
        if (completion != null) { publish(completion); }
        if (active == null && pending != null && mayStart()) { start(); }
    }

    /** A capture is in flight (published or not) or a request is pending. */
    public boolean busy() { owner(); return active != null || pending != null; }
    public boolean inFlight() { owner(); return active != null; }
    public boolean completionReady() { owner(); return !completions.isEmpty(); }
    public boolean shuttingDown() { owner(); return shutdown; }
    public boolean closed() { owner(); return closed; }
    public long lastCommittedSequence() { owner(); return lastCommitted; }

    /** Idempotent. An attempt in flight at shutdown entry, even one completed but unpublished, is attempt 1. */
    public void latchShutdown() {
        owner();
        if (!shutdown) { shutdown = true; shutdownAttempts = active == null ? 0 : 1; }
    }

    /**
     * Detached roots only (the root enforces it): pump publication until idle, on the drain's millisecond clock.
     * Production never awaits IO from a running server.
     */
    public void await(LongSupplier nanoTime, DrainWait wait) {
        owner();
        if (shutdown || closed) { throw new IllegalStateException("A stopping " + label + " writer drains instead"); }
        long base = now, origin = nanoTime.getAsLong();
        while (busy()) {
            observe(base + TimeUnit.NANOSECONDS.toMillis(nanoTime.getAsLong() - origin));
            Completion<P> completion = completions.poll();
            if (completion != null) { publish(completion); }
            if (active == null && pending != null && mayStart()) { start(); }
            if (busy()) {
                try { wait.sleep(untilNextMillisecond(nanoTime.getAsLong() - origin)); }
                catch (InterruptedException interruption) {
                    Thread.currentThread().interrupt(); throw new IllegalStateException("Writer await interrupted", interruption);
                }
            }
        }
    }

    /**
     * ADR-074 item 10: drain until nothing needs a write and nothing is active or pending, within one shutdown budget
     * of three attempts shared by every drain and close of this writer. A newer target raises F, never the budget.
     */
    public DrainReport drain(A attributes, LongSupplier nanoTime, DrainWait wait) {
        owner(); Objects.requireNonNull(attributes, "attributes");
        latchShutdown();
        if (closed) { return last; }
        if (!drainClock) { drainClock = true; drainBase = now; drainOrigin = nanoTime.getAsLong(); }
        boolean interrupted = false;
        try {
            raiseTarget();
            while (true) {
                observe(drainBase + TimeUnit.NANOSECONDS.toMillis(nanoTime.getAsLong() - drainOrigin));
                Completion<P> completion = completions.poll();
                if (completion != null) { publish(completion); raiseTarget(); }
                if (active == null && pending == null && !root.needsWrite()) { return finish(true, "COVERED"); }
                if (active == null) {
                    if (shutdownAttempts >= SHUTDOWN_ATTEMPTS) { return finish(false, "BUDGET_EXHAUSTED"); }
                    if (mayStart()) {
                        pending = pending == null ? attributes : root.merge(pending, attributes);
                        pendingSequence = root.mutationSequence();
                        shutdownAttempts++;
                        start();
                        continue;
                    }
                }
                try { wait.sleep(untilNextMillisecond(nanoTime.getAsLong() - drainOrigin)); }
                catch (InterruptedException interruption) { interrupted = true; return finish(false, "INTERRUPTED"); }
            }
        } catch (RuntimeException failure) {
            return finish(false, failure.getClass().getSimpleName());
        } finally {
            if (interrupted) { Thread.currentThread().interrupt(); }
        }
    }

    /** Resumes the same drain without a new budget, then joins IO even when publication is abandoned. Idempotent. */
    public DrainReport close(A attributes, LongSupplier nanoTime, DrainWait wait) {
        owner();
        if (closed) { return last; }
        try { return drain(attributes, nanoTime, wait); }
        finally { closed = true; pending = null; join(); }
    }

    /** Stops admission and joins without publishing. Never reports a successful drain. Idempotent. */
    public DrainReport abort() {
        owner();
        if (closed) { return last; }
        latchShutdown(); closed = true; pending = null;
        boolean unresolved = active != null || root.needsWrite();
        join(); completions.clear(); active = null;
        last = new DrainReport(false, target, lastCommitted, shutdownAttempts, unresolved ? "ABORTED_UNRESOLVED" : "ABORTED_IDLE");
        return last;
    }

    private void start() {
        A attributes = pending; long requested = pendingSequence, sequence = root.mutationSequence();
        C capture = root.capture(sequence);
        long attempt = ++attempts;
        pending = null; active = new Active<>(attempt, sequence, attributes); lastStart = now;
        try {
            io.execute(() -> run(attempt, sequence, capture, attributes));
        } catch (RuntimeException rejected) {
            active = null; pending = attributes; pendingSequence = requested; throw rejected;
        }
    }

    private void run(long attempt, long sequence, C capture, A attributes) {
        ThreadMXBean threads = ManagementFactory.getThreadMXBean();
        long started = System.nanoTime(), cpu = cpu(threads);
        Attempt<P> result;
        try {
            result = Objects.requireNonNull(root.write(capture, attributes), "attempt");
        } catch (RuntimeException beforeReplacement) {
            result = new Attempt<>(Status.FAILED, null);
        } catch (Throwable unexpected) {
            // An Error may follow replacement; the previous file generation is no longer known (W27C1-R6, W11b2).
            LOGGER.error("ARCE_CHECKED_WRITER_THROWABLE root={} sequence={}", label, sequence, unexpected);
            result = new Attempt<>(Status.UNKNOWN, null);
        }
        completions.add(new Completion<>(attempt, sequence, result.status(), result.payload(),
                System.nanoTime() - started, cpu(threads) - cpu));
    }

    private void publish(Completion<P> completion) {
        if (active == null || completion.attempt() != active.attempt()) {
            throw new IllegalStateException("Foreign " + label + " writer completion");
        }
        A attempted = active.attributes(); active = null;
        if (completion.status() == Status.COMMITTED) {
            lastCommitted = Math.max(lastCommitted, completion.sequence());
            if (pending != null && pendingSequence <= completion.sequence() && root.serviced(attempted, pending)) {
                pending = null;
            }
        } else {
            failedAt = now;
        }
        root.publish(completion);
    }

    private DrainReport finish(boolean success, String reason) {
        last = new DrainReport(success, target, lastCommitted, shutdownAttempts, reason);
        if (success) {
            LOGGER.info("ARCE_CHECKED_WRITER_DRAINED root={} target={} committed={} attempts={}",
                    label, target, lastCommitted, shutdownAttempts);
        } else if (!warned) {
            warned = true;
            LOGGER.warn("ARCE_CHECKED_WRITER_DRAIN_INCOMPLETE root={} target={} committed={} attempts={} reason={}",
                    label, target, lastCommitted, shutdownAttempts, reason);
        }
        return last;
    }

    private void raiseTarget() {
        if (root.needsWrite()) { target = Math.max(target, root.mutationSequence()); }
    }

    private boolean mayStart() {
        return now - lastStart >= CADENCE_TICKS && now - failedAt >= BACKOFF_TICKS;
    }

    private void observe(long tick) { now = Math.max(now, tick); }

    private void join() {
        // Executor shutdown is not termination: keep world ownership until IO has stopped, even when interrupted.
        boolean interrupted = Thread.interrupted();
        io.shutdown();
        while (true) {
            try { if (io.awaitTermination(1, TimeUnit.SECONDS)) { break; } }
            catch (InterruptedException interruption) { interrupted = true; }
        }
        if (interrupted) { Thread.currentThread().interrupt(); }
    }

    private static long untilNextMillisecond(long elapsed) { return 1_000_000 - Math.floorMod(elapsed, 1_000_000L); }

    private static long cpu(ThreadMXBean threads) {
        return threads.isCurrentThreadCpuTimeSupported() ? threads.getCurrentThreadCpuTime() : 0;
    }

    private void owner() {
        if (Thread.currentThread() != owner) {
            throw new IllegalStateException("The " + label + " writer requires its owner thread");
        }
    }
}
