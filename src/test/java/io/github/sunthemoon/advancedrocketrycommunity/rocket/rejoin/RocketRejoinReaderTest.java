package io.github.sunthemoon.advancedrocketrycommunity.rocket.rejoin;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class RocketRejoinReaderTest {
    @TempDir Path directory;
    @Test void closeDuringOpenScanReturnsOnlyAfterReaderThreadAndHandleClose() throws Exception {
        var file = directory.resolve(RocketRejoinPlayerFileTest.expected().player() + ".dat");
        try (var out = new java.util.zip.GZIPOutputStream(java.nio.file.Files.newOutputStream(file))) { out.write(new byte[]{10, 0, 0, 0}); }
        var entered = new java.util.concurrent.CountDownLatch(1);
        var calls = new java.util.concurrent.atomic.AtomicInteger();
        var thread = new java.util.concurrent.atomic.AtomicReference<Thread>();
        try (var reader = new RocketRejoinReader(() -> {
            if (calls.incrementAndGet() == 2) {
                thread.set(Thread.currentThread()); entered.countDown();
                try { new java.util.concurrent.CountDownLatch(1).await(10, java.util.concurrent.TimeUnit.SECONDS); }
                catch (InterruptedException interrupted) {
                    // Make premature return deterministic, while retaining the scanner's real interrupt/close path.
                    try { Thread.sleep(100); } catch (InterruptedException again) { }
                    Thread.currentThread().interrupt();
                }
            }
            return System.nanoTime();
        })) {
            reader.start(directory, new Object(), RocketRejoinPlayerFileTest.expected());
            assertTrue(entered.await(10, java.util.concurrent.TimeUnit.SECONDS));
            reader.close();
            assertFalse(thread.get().isAlive(), "Close returned with an installed-file scan still running");
            assertFalse(reader.busy()); assertNull(reader.poll());
            java.nio.file.Files.delete(file);
        } finally { if (thread.get() != null) { thread.get().join(1000); } }
    }
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
