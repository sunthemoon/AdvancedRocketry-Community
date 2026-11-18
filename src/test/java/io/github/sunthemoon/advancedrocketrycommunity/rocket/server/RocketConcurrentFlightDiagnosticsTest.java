package io.github.sunthemoon.advancedrocketrycommunity.rocket.server;

import static org.junit.jupiter.api.Assertions.*;

import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class RocketConcurrentFlightDiagnosticsTest {
    @BeforeAll static void bootstrap() { MinecraftBootstrap.initialize(); }

    @Test void passingTicksDoNotCollectDiagnostics() {
        for (int waits = 1; waits < 550; waits++) {
            RocketConcurrentFlightGameTests.checkWait(waits,
                    () -> { throw new AssertionError("Unexpected world observation on a passing tick"); },
                    message -> fail(message));
        }
    }

    @Test void originalBoundStillFailsAndIncludesTheObservedState() {
        var observations = new AtomicInteger();
        for (int waits : new int[] {550, 551}) {
            var failure = assertThrows(AssertionError.class, () -> RocketConcurrentFlightGameTests.checkWait(waits,
                    () -> { observations.incrementAndGet(); return "stage=2 destinationPresent=false"; },
                    message -> { throw new AssertionError(message); }));
            assertEquals("Concurrent-flight fixture exceeded its bounded wait; stage=2 destinationPresent=false", failure.getMessage());
        }
        assertEquals(2, observations.get());
    }
}
