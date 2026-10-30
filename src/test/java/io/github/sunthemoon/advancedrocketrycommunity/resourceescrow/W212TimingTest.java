package io.github.sunthemoon.advancedrocketrycommunity.resourceescrow;

import static org.junit.jupiter.api.Assertions.*;
import com.google.gson.JsonParser;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class W212TimingTest {
    @TempDir Path directory;

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
