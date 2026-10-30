package io.github.sunthemoon.advancedrocketrycommunity.resourceescrow;

import com.google.gson.GsonBuilder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryPoolMXBean;

/** Diagnostic-JAR-only nested elapsed spans. Owner-thread scope; no authority or world references. */
public final class W212Timing {
    private static final int LIMIT = 262_144;
    private static final ThreadLocal<Recorder> ACTIVE = new ThreadLocal<>();
    private static final Span NONE = new Span(null, "", 0, 0);
    private W212Timing() { }

    /** Diagnostic export only; measurement queries and output are outside the timed initialization body. */
    public static Startup startup() { return new Startup(); }

    public static final class Startup implements AutoCloseable {
        private final com.sun.management.ThreadMXBean threads =
                (com.sun.management.ThreadMXBean) ManagementFactory.getThreadMXBean();
        private final java.lang.management.ClassLoadingMXBean classes = ManagementFactory.getClassLoadingMXBean();
        private final java.lang.management.MemoryMXBean memory = ManagementFactory.getMemoryMXBean();
        private final List<MemoryPoolMXBean> pools = ManagementFactory.getMemoryPoolMXBeans();
        private final long thread = Thread.currentThread().getId();
        private final int loadedBefore = classes.getLoadedClassCount();
        private final long heapBefore = memory.getHeapMemoryUsage().getUsed();
        private final long metadataBefore = metadata();
        private final long allocatedBefore = allocated();
        private final long start = System.nanoTime();

        private long metadata() {
            long bytes = 0;
            for (var pool : pools) {
                if (pool.getName().equals("Metaspace") || pool.getName().equals("Compressed Class Space")) {
                    bytes += pool.getUsage().getUsed();
                }
            }
            return bytes;
        }
        private long allocated() {
            return threads.isThreadAllocatedMemorySupported() && threads.isThreadAllocatedMemoryEnabled()
                    ? threads.getThreadAllocatedBytes(thread) : -1;
        }
        @Override public void close() {
            long end = System.nanoTime();
            long allocatedAfter = allocated();
            var result = new java.util.LinkedHashMap<String, Object>();
            result.put("elapsed_ns", end - start);
            result.put("thread", Thread.currentThread().getName());
            result.put("loaded_classes_delta", classes.getLoadedClassCount() - loadedBefore);
            result.put("thread_allocated_bytes", allocatedBefore < 0 || allocatedAfter < 0 ? -1 : allocatedAfter - allocatedBefore);
            result.put("heap_used_delta_bytes", memory.getHeapMemoryUsage().getUsed() - heapBefore);
            result.put("metadata_used_delta_bytes", metadata() - metadataBefore);
            System.out.println("W215_STARTUP " + new GsonBuilder().create().toJson(result));
        }
    }

    public static void start() {
        if (ACTIVE.get() != null) { throw new IllegalStateException("Recorder already active"); }
        ACTIVE.set(new Recorder());
    }

    public static Span open(String label) {
        var recorder = ACTIVE.get();
        return recorder == null ? NONE : new Span(recorder, label, System.nanoTime(), recorder.depth++);
    }

    public static void write(Path path) throws Exception {
        var recorder = ACTIVE.get();
        if (recorder == null || recorder.depth != 0) { throw new IllegalStateException("Unbalanced recorder"); }
        ACTIVE.remove();
        try (var writer = Files.newBufferedWriter(path)) { new GsonBuilder().create().toJson(recorder, writer); }
        if (recorder.capped) { throw new IllegalStateException("Span bound exceeded; attribution incomplete"); }
    }

    private static final class Recorder {
        final long thread = Thread.currentThread().getId();
        final long clockNanos = System.nanoTime();
        final long clockEpochMillis = System.currentTimeMillis();
        final List<Row> rows = new ArrayList<>();
        int depth;
        boolean capped;
    }
    private record Row(String label, long start, long end, int depth) { }

    public static final class Span implements AutoCloseable {
        private final Recorder recorder;
        private final String label;
        private final long start;
        private final int depth;
        private Span(Recorder recorder, String label, long start, int depth) {
            this.recorder = recorder; this.label = label; this.start = start; this.depth = depth;
        }
        @Override public void close() {
            if (recorder == null) { return; }
            long end = System.nanoTime();
            if (--recorder.depth != depth) { throw new IllegalStateException("Unbalanced span"); }
            if (recorder.rows.size() < LIMIT) { recorder.rows.add(new Row(label, start, end, depth)); }
            else { recorder.capped = true; }
        }
    }
}
