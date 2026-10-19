package io.github.sunthemoon.advancedrocketrycommunity.testsupport;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/** Real IO, but deterministic 25 ms timer wakeups. Only the owner advances the virtual clock. */
public final class CoarseDrainClock extends AbstractExecutorService {
    // Exercise subtraction across nanoTime's signed wraparound as well as coarse wakeups.
    private static final long ORIGIN = Long.MAX_VALUE - TimeUnit.MILLISECONDS.toNanos(10);
    private final ExecutorService io = Executors.newSingleThreadExecutor(task -> {
        Thread thread = new Thread(task, "ARCE-drain-clock-test"); thread.setDaemon(true); return thread;
    });
    private final List<Future<?>> writes = new ArrayList<>();
    private final List<Long> starts = new ArrayList<>();
    private long elapsed;

    public long nanoTime() { return ORIGIN + elapsed; }
    public long elapsedMillis() { return TimeUnit.NANOSECONDS.toMillis(elapsed); }
    public List<Long> starts() { return List.copyOf(starts); }
    public void sleep(long nanos) throws InterruptedException {
        if (nanos <= 0 || nanos > TimeUnit.MILLISECONDS.toNanos(1)) {
            throw new AssertionError("Drain must wait for the next millisecond boundary: " + nanos);
        }
        awaitWrites();
        elapsed += TimeUnit.MILLISECONDS.toNanos(25);
    }
    public void awaitWrites() throws InterruptedException {
        for (Future<?> write : writes) {
            try { write.get(5, TimeUnit.SECONDS); }
            catch (ExecutionException | TimeoutException failure) { throw new AssertionError("Drain IO did not complete", failure); }
        }
        writes.clear();
    }
    @Override public void execute(Runnable task) {
        starts.add(elapsedMillis()); writes.add(io.submit(task));
    }
    @Override public void shutdown() { io.shutdown(); }
    @Override public List<Runnable> shutdownNow() { return io.shutdownNow(); }
    @Override public boolean isShutdown() { return io.isShutdown(); }
    @Override public boolean isTerminated() { return io.isTerminated(); }
    @Override public boolean awaitTermination(long timeout, TimeUnit unit) throws InterruptedException {
        return io.awaitTermination(timeout, unit);
    }
}
