package io.github.sunthemoon.advancedrocketrycommunity.resourceescrow;

import static org.junit.jupiter.api.Assertions.*;
import com.google.gson.JsonParser;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class W212TimingTest {
    @TempDir Path directory;

    @Test void combinedMetaspaceDoesNotCountItsCompressedClassSubsetTwice() {
        assertEquals(1_000, W212Timing.metadataUsed(java.util.List.of(
                pool("Metaspace", 1_000), pool("Compressed Class Space", 200), pool("CodeHeap", 500))));
    }

    private static java.lang.management.MemoryPoolMXBean pool(String name, long used) {
        return (java.lang.management.MemoryPoolMXBean) java.lang.reflect.Proxy.newProxyInstance(
                W212TimingTest.class.getClassLoader(), new Class<?>[]{java.lang.management.MemoryPoolMXBean.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getName" -> name;
                    case "getUsage" -> new java.lang.management.MemoryUsage(0, used, used, -1);
                    default -> throw new AssertionError("Unexpected pool query: " + method);
                });
    }

    @Test void startupMetricsAreSeparateFromTheTickRecorder() {
        var bytes = new java.io.ByteArrayOutputStream();
        var original = System.out;
        try (var capture = new java.io.PrintStream(bytes, true, java.nio.charset.StandardCharsets.UTF_8)) {
            System.setOut(capture);
            try (var startup = W212Timing.startup()) { assertNotNull(startup); }
        } finally { System.setOut(original); }
        String output = bytes.toString(java.nio.charset.StandardCharsets.UTF_8).trim();
        assertTrue(output.startsWith("W215_STARTUP "));
        var row = JsonParser.parseString(output.substring("W215_STARTUP ".length())).getAsJsonObject();
        assertTrue(row.get("elapsed_ns").getAsLong() >= 0);
        assertEquals(Thread.currentThread().getName(), row.get("thread").getAsString());
        assertTrue(row.has("loaded_classes_delta"));
        assertTrue(row.has("thread_allocated_bytes"));
        assertTrue(row.has("heap_used_delta_bytes"));
        assertTrue(row.has("metaspace_used_delta_bytes"));
        assertTrue(row.has("compressed_class_space_used_delta_bytes"));
        assertFalse(row.has("metadata_used_delta_bytes"));
    }

    @Test void nestedElapsedSpansRetainTheirDepthAndContainment() throws Exception {
        W212Timing.start();
        try (var outer = W212Timing.open("outer")) {
            try (var inner = W212Timing.open("inner")) { assertNotNull(inner); }
        }
        var output = directory.resolve("spans.json"); W212Timing.write(output);
        var root = JsonParser.parseString(Files.readString(output)).getAsJsonObject();
        assertFalse(root.get("capped").getAsBoolean());
        assertEquals(0, root.get("depth").getAsInt());
        var rows = root.getAsJsonArray("rows"); assertEquals(2, rows.size());
        var inner = rows.get(0).getAsJsonObject(); var outer = rows.get(1).getAsJsonObject();
        assertEquals(1, inner.get("depth").getAsInt()); assertEquals(0, outer.get("depth").getAsInt());
        assertTrue(outer.get("start").getAsLong() <= inner.get("start").getAsLong());
        assertTrue(outer.get("end").getAsLong() >= inner.get("end").getAsLong());
    }

    @Test void inactiveOtherThreadAndExceptionExitDoNotLeakScopes() throws Exception {
        try (var ignored = W212Timing.open("inactive")) { assertNotNull(ignored); }
        W212Timing.start();
        var thread = new Thread(() -> { try (var ignored = W212Timing.open("other")) { } });
        thread.start(); thread.join();
        assertThrows(IllegalArgumentException.class, () -> {
            try (var ignored = W212Timing.open("failure")) { throw new IllegalArgumentException(); }
        });
        var output = directory.resolve("spans.json"); W212Timing.write(output);
        var rows = JsonParser.parseString(Files.readString(output)).getAsJsonObject().getAsJsonArray("rows");
        assertEquals(1, rows.size()); assertEquals("failure", rows.get(0).getAsJsonObject().get("label").getAsString());
        W212Timing.start(); W212Timing.write(directory.resolve("second.json"));
    }
}
