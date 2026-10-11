package io.github.sunthemoon.arceadaptertest;

import java.util.function.Consumer;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

/** Owned enqueue/closure boundary; queued native work still belongs to the external process deadline. */
final class NativeFixtureLifetime {
    private final LongSupplier clock;
    private final long started;
    private final long limit;
    private volatile boolean closed;

    NativeFixtureLifetime(LongSupplier clock, long limit) {
        if (limit <= 0) { throw new IllegalArgumentException("Positive fixture duration required"); }
        this.clock = clock;
        this.started = clock.getAsLong();
        this.limit = limit;
    }

    boolean isClosed() { return closed; }
    boolean expired() { return clock.getAsLong() - started >= limit; }

    synchronized <T> T enqueue(Supplier<T> action) {
        if (closed) { throw new IllegalStateException("Owned requester already retired"); }
        // Native off-thread cache submission only queues work and returns a future; never waits here.
        return action.get();
    }

    synchronized boolean close() {
        if (closed) { return false; }
        closed = true;
        return true;
    }

    void install(Runnable registration, Consumer<Throwable> terminal) {
        try { registration.run(); } catch (RuntimeException | Error primary) { terminal.accept(primary); }
    }

    static Throwable cleanup(Throwable primary, Runnable action) {
        try { action.run(); } catch (RuntimeException | Error failure) {
            if (primary == null) { return failure; }
            if (primary != failure) { primary.addSuppressed(failure); }
        }
        return primary;
    }
}
