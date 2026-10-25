package io.github.sunthemoon.advancedrocketrycommunity.rocket.rejoin;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class RocketRejoinReaderTest {
    @TempDir Path directory;
    @Test void oneDaemonScanAndOwnerPolledResult() throws Exception {
        try (var reader = new RocketRejoinReader()) {
            var identity = new Object(); reader.start(directory, identity, RocketRejoinPlayerFileTest.expected());
            assertTrue(reader.busy());
            assertThrows(IllegalStateException.class, () -> reader.start(directory, new Object(), RocketRejoinPlayerFileTest.expected()));
            assertTrue(Thread.getAllStackTraces().keySet().stream().filter(t -> t.getName().equals("arce-passenger-readback")).allMatch(Thread::isDaemon));
            RocketRejoinReader.Completion result = null; long until = System.nanoTime() + 10_000_000_000L;
            while (result == null && System.nanoTime() < until) { result = reader.poll(); if (result == null) { Thread.sleep(2); } }
            assertNotNull(result); assertSame(identity, result.identity()); assertEquals(RocketRejoinPlayerFile.Status.IO, result.result().status());
            assertFalse(reader.busy()); assertNull(reader.poll());
        }
    }
}
