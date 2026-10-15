package io.github.sunthemoon.advancedrocketrycommunity.progression.classic.firstevent;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

class FirstEventCutLatchTest {
    @Test void releaseAllowsTheMoveAndTheSameLatchCannotBlockAgain() throws Exception {
        var latch = new FirstEventCutLatch(10, 1_000);
        var writer = Executors.newSingleThreadExecutor();
        try {
            var result = writer.submit(() -> { latch.awaitMove(); return true; });
            latch.release();
            assertTrue(result.get(1, TimeUnit.SECONDS));
            assertDoesNotThrow(latch::awaitMove);
        } finally { writer.shutdownNow(); }
    }

    @Test void aWriterWithoutReleaseRefusesAtTheWallClockBound() {
        var latch = new FirstEventCutLatch(10, 1);
        assertThrows(IOException.class, latch::awaitMove);
    }

    @Test void neitherBudgetCanBeEnlarged() {
        assertThrows(IllegalArgumentException.class, () -> new FirstEventCutLatch(1_201, 1));
        assertThrows(IllegalArgumentException.class, () -> new FirstEventCutLatch(1, 60_001));
    }
}
