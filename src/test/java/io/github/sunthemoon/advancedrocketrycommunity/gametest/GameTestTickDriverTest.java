package io.github.sunthemoon.advancedrocketrycommunity.gametest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;

class GameTestTickDriverTest {
    @Test
    void transitionsKeepRelativeDelaysAndRunEachStepExactlyOnce() {
        var clock = new AtomicLong(5);
        var driver = new GameTestTickDriver(clock::get);
        var calls = new ArrayList<Long>();
        driver.after(2, () -> {
            calls.add(clock.get());
            driver.after(3, () -> calls.add(clock.get()));
        });
        for (long tick = 5; tick <= 12; tick++) {
            clock.set(tick);
            driver.tick();
        }
        assertEquals(java.util.List.of(7L, 10L), calls);
    }

    @Test
    void aTransitionDueNowWaitsForTheNextTickCallback() {
        var clock = new AtomicLong();
        var driver = new GameTestTickDriver(clock::get);
        var calls = new ArrayList<Long>();
        driver.at(0, () -> {
            calls.add(clock.get());
            driver.after(0, () -> calls.add(clock.get()));
        });
        driver.tick();
        assertEquals(java.util.List.of(0L), calls);
        clock.incrementAndGet();
        driver.tick();
        assertEquals(java.util.List.of(0L, 1L), calls);
    }

    @Test
    void overlappingChainsCannotSilentlyDropAnAssertion() {
        var driver = new GameTestTickDriver(() -> 0L);
        driver.after(1, () -> {});
        assertThrows(IllegalStateException.class, () -> driver.after(2, () -> {}));
    }

    @Test
    void failuresPropagateAndAreNotRepeated() {
        var driver = new GameTestTickDriver(() -> 0L);
        var failure = new IllegalStateException("fixture failure");
        driver.at(0, () -> { throw failure; });
        assertEquals(failure, assertThrows(IllegalStateException.class, driver::tick));
        driver.tick();
    }
}
