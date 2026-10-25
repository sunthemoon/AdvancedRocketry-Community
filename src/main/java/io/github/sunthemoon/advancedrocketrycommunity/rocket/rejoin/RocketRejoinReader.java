package io.github.sunthemoon.advancedrocketrycommunity.rocket.rejoin;

import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Exactly one daemon scan, with no executor-side queue. Owner work slots coalesce save notifications. */
final class RocketRejoinReader implements AutoCloseable {
    record Completion(Object identity, RocketRejoinPlayerFile.Expected expected, RocketRejoinPlayerFile.Result result) { }
    private ExecutorService executor;
    private CompletableFuture<Completion> active;
    boolean busy() { return active != null; }
    void start(Path directory, Object identity, RocketRejoinPlayerFile.Expected expected) {
        if (active != null) { throw new IllegalStateException("Concurrent rejoin scan"); }
        if (executor == null) {
            executor = Executors.newSingleThreadExecutor(run -> { var thread = new Thread(run, "arce-passenger-readback"); thread.setDaemon(true); return thread; });
        }
        active = CompletableFuture.supplyAsync(() -> new Completion(identity, expected, RocketRejoinPlayerFile.scan(directory, expected)), executor)
                .exceptionally(failure -> new Completion(identity, expected, new RocketRejoinPlayerFile.Result(RocketRejoinPlayerFile.Status.IO, 0, 0)));
    }
    Completion poll() {
        if (active == null || !active.isDone()) { return null; }
        var completed = active; active = null;
        return completed.join();
    }
    @Override public void close() { if (executor != null) { executor.shutdownNow(); } active = null; }
}
