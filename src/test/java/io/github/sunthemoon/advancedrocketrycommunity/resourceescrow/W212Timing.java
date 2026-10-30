package io.github.sunthemoon.advancedrocketrycommunity.resourceescrow;

import com.google.gson.GsonBuilder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Diagnostic-JAR-only nested elapsed spans. Owner-thread scope; no authority or world references. */
public final class W212Timing {
    private static final int LIMIT = 262_144;
    private static final ThreadLocal<Recorder> ACTIVE = new ThreadLocal<>();
    private static final Span NONE = new Span(null, "", 0, 0);
    private W212Timing() { }

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
