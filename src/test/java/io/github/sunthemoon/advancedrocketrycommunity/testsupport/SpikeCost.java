package io.github.sunthemoon.advancedrocketrycommunity.testsupport;

import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.lang.management.ThreadMXBean;
import java.util.Arrays;
import java.util.function.IntConsumer;

/**
 * The server-thread cost of one call, for the docs/17 single-spike budget (W55). A unit test cannot reproduce the
 * reference node, and wall time on a shared machine also counts the time other processes hold the CPU: under a
 * 12-core load, one ground-survey START measured 484 to 1,780 ms of wall time while its own work stayed at 312 to
 * 500 ms of CPU time. So the cost of a call is the calling thread's CPU time plus the stop-the-world GC pause time
 * during the call, which its own allocation causes. Warm-up calls first bring the JIT to its steady state, because
 * the budget is for non-startup spikes. Wall time is still recorded for the report.
 */
public final class SpikeCost {
    private SpikeCost() {
    }

    /** Runs {@code call} with indices 0 to {@code warmups - 1} untimed, then times the next {@code samples} calls. */
    public static Samples measure(int warmups, int samples, IntConsumer call) {
        ThreadMXBean threads = ManagementFactory.getThreadMXBean();
        if (!threads.isCurrentThreadCpuTimeSupported() || !threads.isThreadCpuTimeEnabled()) {
            throw new IllegalStateException("This JVM cannot measure the current thread's CPU time");
        }
        for (int index = 0; index < warmups; index++) {
            call.accept(index);
        }
        long[] cost = new long[samples];
        long[] wall = new long[samples];
        for (int sample = 0; sample < samples; sample++) {
            long pauses = gcMillis();
            long cpu = threads.getCurrentThreadCpuTime();
            long begin = System.nanoTime();
            call.accept(warmups + sample);
            wall[sample] = System.nanoTime() - begin;
            cost[sample] = threads.getCurrentThreadCpuTime() - cpu + (gcMillis() - pauses) * 1_000_000L;
        }
        return new Samples(cost, wall);
    }

    private static long gcMillis() {
        long total = 0L;
        for (GarbageCollectorMXBean collector : ManagementFactory.getGarbageCollectorMXBeans()) {
            total += Math.max(0L, collector.getCollectionTime());
        }
        return total;
    }

    /** Per-sample cost and wall time, in nanoseconds. */
    public record Samples(long[] cost, long[] wall) {
        public long medianCost() {
            return median(cost);
        }

        public long maxCost() {
            return Arrays.stream(cost).max().orElseThrow();
        }

        public long medianWall() {
            return median(wall);
        }

        public long maxWall() {
            return Arrays.stream(wall).max().orElseThrow();
        }

        private static long median(long[] values) {
            long[] sorted = values.clone();
            Arrays.sort(sorted);
            return sorted[sorted.length / 2];
        }
    }
}
