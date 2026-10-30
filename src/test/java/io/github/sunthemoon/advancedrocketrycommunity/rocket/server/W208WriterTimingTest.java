package io.github.sunthemoon.advancedrocketrycommunity.rocket.server;

import static org.junit.jupiter.api.Assertions.*;
import java.util.Map;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;

class W208WriterTimingTest {
    @Test void joinObserverDoesNotChangeExecutorOrInterruption() throws Exception {
        var events = new ConcurrentLinkedQueue<Map<String, Object>>();
        var real = Executors.newSingleThreadExecutor();
        var observed = W208WriterTiming.observeJoin(real, events, () -> 7);
        var release = new CountDownLatch(1);
        observed.execute(() -> { try { release.await(); } catch (InterruptedException error) { Thread.currentThread().interrupt(); } });
        try {
            observed.shutdown(); assertFalse(observed.awaitTermination(1, TimeUnit.MILLISECONDS)); assertTrue(events.isEmpty());
            Thread.currentThread().interrupt();
            assertThrows(InterruptedException.class, () -> observed.awaitTermination(1, TimeUnit.SECONDS));
            assertTrue(events.isEmpty()); release.countDown(); assertTrue(observed.awaitTermination(30, TimeUnit.SECONDS));
            assertEquals(1, events.size()); assertEquals("IO_JOIN", events.peek().get("label"));
            assertEquals(7, events.peek().get("tick")); assertTrue(real.isTerminated());
        } finally { Thread.interrupted(); release.countDown(); real.shutdown(); assertTrue(real.awaitTermination(30, TimeUnit.SECONDS)); }
    }
}
