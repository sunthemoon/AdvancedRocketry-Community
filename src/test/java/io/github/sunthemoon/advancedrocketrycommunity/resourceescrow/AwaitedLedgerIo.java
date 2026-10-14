package io.github.sunthemoon.advancedrocketrycommunity.resourceescrow;

import io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.CheckedSavedDataFile;
import java.nio.file.Path;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.LongConsumer;

/**
 * A real ledger IO thread that the test drives. Before each owner tick the test awaits every write submitted so far,
 * so that tick observes the completion. Waits count owner ticks, never wall-clock time, so machine load cannot change
 * an outcome. One writer per instance; closing the writer shuts this executor down.
 */
final class AwaitedLedgerIo extends AbstractExecutorService {
    /** Ten 200-tick UNKNOWN retry backoffs. The longest wait in these tests needs about 620 ticks. */
    static final int TICK_BOUND = 2_000;
    private final ExecutorService thread = Executors.newSingleThreadExecutor(task -> {
        Thread io = new Thread(task, "ARCE-escrow-ledger-test"); io.setDaemon(true); return io;
    });
    private final Queue<Future<?>> writes = new ConcurrentLinkedQueue<>();

    EscrowLedgerWriter writer(Path file, long generation, CheckedSavedDataFile.Committer committer) {
        return writer(file, generation, committer, cut -> { });
    }
    EscrowLedgerWriter writer(Path file, long generation, CheckedSavedDataFile.Committer committer, Consumer<String> checkpoint) {
        return new EscrowLedgerWriter(file, generation, committer, checkpoint, this);
    }
    /** Returns once every write submitted so far has queued its completion; a write that threw fails the test. */
    void awaitWrites() {
        for (Future<?> write; (write = writes.poll()) != null; ) {
            try { write.get(); }
            catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); throw new AssertionError(interrupted); }
            catch (ExecutionException failure) { throw new AssertionError("Ledger IO task threw", failure.getCause()); }
        }
    }
    /** Awaits the submitted writes, then runs one owner tick, until done or TICK_BOUND ticks. Returns the next unused tick. */
    long tickUntil(LongConsumer owner, long tick, BooleanSupplier done) {
        for (long end = tick + TICK_BOUND; !done.getAsBoolean() && tick < end; tick++) { awaitWrites(); owner.accept(tick); }
        return tick;
    }

    @Override public void execute(Runnable write) { writes.add(thread.submit(write)); }
    @Override public void shutdown() { thread.shutdown(); }
    @Override public List<Runnable> shutdownNow() { return thread.shutdownNow(); }
    @Override public boolean isShutdown() { return thread.isShutdown(); }
    @Override public boolean isTerminated() { return thread.isTerminated(); }
    @Override public boolean awaitTermination(long timeout, TimeUnit unit) throws InterruptedException {
        return thread.awaitTermination(timeout, unit);
    }
}
