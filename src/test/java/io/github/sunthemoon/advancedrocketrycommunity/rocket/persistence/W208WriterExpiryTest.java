package io.github.sunthemoon.advancedrocketrycommunity.rocket.persistence;

import static org.junit.jupiter.api.Assertions.*;

import io.github.sunthemoon.advancedrocketrycommunity.persistence.CheckedJournalCheckpoint.Outcome;
import io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.ManagedSavedDataType;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import java.nio.file.Path;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;
import java.util.function.IntSupplier;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** R181: expiry controls hold real asynchronous writes for exactly 120 observation ticks, once. */
class W208WriterExpiryTest {
    @TempDir Path directory;
    @BeforeAll static void bootstrap() { MinecraftBootstrap.initialize(); }

    @Test void c1ExpiryReleasesAt120TicksAndOnlyInjectsOnce() throws Exception {
        expiry("C1", "near-c1-expiry");
    }

    @Test void c3ExpiryReleasesAt120TicksAndOnlyInjectsOnce() throws Exception {
        expiry("C3", "near-c3-expiry");
    }

    private void expiry(String phase, String scenario) throws Exception {
        var data = new RocketTransactionSavedData();
        var control = new W208WriterControl(scenario);
        var tick = new AtomicInteger(37);
        var injected = new AtomicInteger();
        IntSupplier clock = () -> { injected.incrementAndGet(); return tick.get(); };
        var policy = control.policy(phase, clock);
        var committed = new CountDownLatch(1);
        var file = directory.resolve(ManagedSavedDataType.ROCKET_TRANSACTIONS.fileName());
        try {
            insert(data, 0);
            assertTrue(data.requestWrite(file, policy.withCommitter((staged, target) -> {
                policy.committer().commit(staged, target);
                committed.countDown();
            })));
            await(control::held, "The expiry committer never entered its hold");
            int entered = tick.get();
            assertEquals(1, injected.get());
            for (int elapsed = 0; elapsed <= 119; elapsed++) {
                tick.set(entered + elapsed);
                control.tick(tick.get());
                data.pollWriter();
                assertTrue(control.held(), "Released at elapsed tick " + elapsed);
                assertTrue(data.writerInFlight());
                assertFalse(data.writerCompletionReady());
                assertTrue(data.acknowledgement().isEmpty());
                assertFalse(data.acknowledgedCurrent());
                assertEquals(0, data.completedWriteCount());
            }
            // Allow the IO thread to run without advancing the controlled clock past +119.
            assertFalse(committed.await(100, TimeUnit.MILLISECONDS), "Committed before entered + 120");
            assertTrue(control.held());
            tick.set(entered + 120);
            control.tick(tick.get());
            assertTrue(committed.await(10, TimeUnit.SECONDS), "No release at entered + 120");
            await(data::writerCompletionReady, "The released write did not finish");
            assertFalse(control.held());
            assertFalse(data.acknowledgedCurrent(), "IO completion is not owner-thread publication");
            data.pollWriter();
            assertEquals(Outcome.COMMITTED, data.writeOutcome());
            assertTrue(data.acknowledgedCurrent());
            assertEquals(1, data.completedWriteCount());

            insert(data, 1);
            assertFalse(data.acknowledgedCurrent());
            assertTrue(data.requestWrite(file, control.policy(phase, clock)));
            await(() -> { data.pollWriter(); return data.acknowledgedCurrent(); },
                    "The next matching write did not commit without more observation ticks");
            assertEquals(entered + 120, tick.get());
            assertEquals(1, injected.get(), "The next matching write re-entered the injection");
            assertFalse(control.held());
            assertEquals(Outcome.COMMITTED, data.writeOutcome());
            assertEquals(2, data.completedWriteCount());
        } finally {
            control.release();
            data.close();
        }
    }

    private static void insert(RocketTransactionSavedData data, int id) {
        var entry = W208JournalFixture.ballast(id, 1);
        data.journalFor(entry.snapshot(), entry.ownerId()).write(entry.record());
    }

    private static void await(BooleanSupplier ready, String message) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (!ready.getAsBoolean()) {
            assertTrue(System.nanoTime() < deadline, message);
            Thread.sleep(1);
        }
    }
}
