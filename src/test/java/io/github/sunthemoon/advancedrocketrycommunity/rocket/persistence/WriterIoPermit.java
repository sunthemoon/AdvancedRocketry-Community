package io.github.sunthemoon.advancedrocketrycommunity.rocket.persistence;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

/**
 * W129bc B3, design sections 5.4 and 8 (Q8, Root decisions R60 and R63): one fair, server-lifetime IO-side write permit
 * shared by the registry, transaction and transfer writers. Each writer's IO executor is wrapped so that its write task
 * acquires the permit on the IO thread, never on the owner thread, and releases it when the write ends; requests,
 * captures and publication are unchanged. Benchmark code only: the wrapper is installed by reflection into each
 * writer's existing {@code io} field, so production writers stay as they are. It records each task's wait for the
 * permit and how long it held it.
 */
final class WriterIoPermit {
    private final Semaphore permit = new Semaphore(1, true);
    private final Map<String, List<double[]>> samples = new ConcurrentHashMap<>();

    /** Replaces the executor at the end of {@code path} (field names from {@code holder}) with its permitted wrapper. */
    void install(String label, Object holder, String... path) throws ReflectiveOperationException {
        Object owner = holder;
        for (int i = 0; i < path.length - 1; i++) { owner = field(owner, path[i]).get(owner); }
        if (owner == null) { throw new IllegalStateException(label + ": the writer was not created yet"); }
        Field io = field(owner, path[path.length - 1]);
        var original = (ExecutorService) io.get(owner);
        if (original instanceof Permitted) { throw new IllegalStateException(label + ": already permitted"); }
        samples.put(label, new CopyOnWriteArrayList<>());
        io.set(owner, new Permitted(label, original));
    }

    Map<String, Object> results() {
        var result = new LinkedHashMap<String, Object>();
        result.put("permit", "java.util.concurrent.Semaphore(1, fair=true), acquired on each writer's IO thread");
        for (var entry : samples.entrySet()) {
            var waits = new ArrayList<Double>(); var holds = new ArrayList<Double>();
            entry.getValue().forEach(sample -> { waits.add(sample[0]); holds.add(sample[1]); });
            result.put(entry.getKey(), Map.of("tasks", waits.size(), "wait_for_permit_ms", RocketTransactionWriterBenchmark.plain(waits),
                    "held_ms", RocketTransactionWriterBenchmark.plain(holds)));
        }
        return result;
    }

    private static Field field(Object owner, String name) throws NoSuchFieldException {
        for (Class<?> type = owner.getClass(); type != null; type = type.getSuperclass()) {
            try {
                Field found = type.getDeclaredField(name);
                found.setAccessible(true);
                return found;
            } catch (NoSuchFieldException next) { /* the superclass may declare it */ }
        }
        throw new NoSuchFieldException(owner.getClass().getName() + "." + name);
    }

    private final class Permitted extends AbstractExecutorService {
        private final String label;
        private final ExecutorService delegate;

        Permitted(String label, ExecutorService delegate) { this.label = label; this.delegate = delegate; }

        @Override public void execute(Runnable task) {
            long submitted = System.nanoTime();
            delegate.execute(() -> {
                permit.acquireUninterruptibly();
                long acquired = System.nanoTime();
                try {
                    task.run();
                } finally {
                    long released = System.nanoTime();
                    permit.release();
                    samples.get(label).add(new double[] {(acquired - submitted) / 1e6, (released - acquired) / 1e6});
                }
            });
        }

        @Override public void shutdown() { delegate.shutdown(); }
        @Override public List<Runnable> shutdownNow() { return delegate.shutdownNow(); }
        @Override public boolean isShutdown() { return delegate.isShutdown(); }
        @Override public boolean isTerminated() { return delegate.isTerminated(); }
        @Override public boolean awaitTermination(long timeout, TimeUnit unit) throws InterruptedException {
            return delegate.awaitTermination(timeout, unit);
        }
    }
}
