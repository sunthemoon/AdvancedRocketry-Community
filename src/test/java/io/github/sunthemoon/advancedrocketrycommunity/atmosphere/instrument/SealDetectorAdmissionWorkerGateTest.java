package io.github.sunthemoon.advancedrocketrycommunity.atmosphere.instrument;

import static org.junit.jupiter.api.Assertions.*;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class SealDetectorAdmissionWorkerGateTest {
    @Test void completedHelperEndsItsOwnedWorkerBeforeCleanup() {
        var gate = new SealDetectorAdmissionGameTests.WorkerGate();
        var worker = new AtomicReference<Thread>();
        assertEquals("done", SealDetectorAdmissionGameTests.offThread(gate, () -> {
            worker.set(Thread.currentThread());
            return "done";
        }));
        assertEquals(Thread.State.TERMINATED, worker.get().getState());
        assertDoesNotThrow(gate::requireStopped);
    }

    @Test void failedCallablePreservesCauseAndEndsBeforeCleanup() {
        var gate = new SealDetectorAdmissionGameTests.WorkerGate();
        var worker = new AtomicReference<Thread>();
        var cause = new IllegalArgumentException("owned diagnostic failure");
        var failure = assertThrows(IllegalStateException.class, () -> SealDetectorAdmissionGameTests.offThread(gate, () -> {
            worker.set(Thread.currentThread());
            throw cause;
        }));
        assertInstanceOf(ExecutionException.class, failure.getCause());
        assertSame(cause, failure.getCause().getCause());
        assertEquals(Thread.State.TERMINATED, worker.get().getState());
        assertDoesNotThrow(gate::requireStopped);
    }

    @Test void unresolvedHelperRefusesResourceCleanupUntilDiagnosticWorkerEnds() throws InterruptedException {
        var gate = new SealDetectorAdmissionGameTests.WorkerGate();
        var release = new CountDownLatch(1);
        var worker = new AtomicReference<Thread>();
        var cleanups = new AtomicInteger();
        AutoCloseable fixture = () -> { gate.requireStopped(); cleanups.incrementAndGet(); };
        try {
            var failure = assertThrows(IllegalStateException.class, () -> {
                try (fixture) {
                    SealDetectorAdmissionGameTests.offThread(gate, () -> {
                        worker.set(Thread.currentThread());
                        awaitRelease(release);
                        return null;
                    });
                }
            });
            assertEquals("Guard worker outlived its fixture", failure.getMessage());
            assertEquals(1, failure.getSuppressed().length);
            assertEquals("Guard worker has not terminated", failure.getSuppressed()[0].getMessage());
            assertTrue(worker.get().isAlive());
            assertEquals(0, cleanups.get());
            assertThrows(IllegalStateException.class, gate::requireStopped);
        } finally {
            release.countDown();
            joinOwned(worker.get());
        }
        assertDoesNotThrow(fixture::close);
        assertEquals(1, cleanups.get());
    }

    @Test void liveOwnedWorkerCannotBeReplaced() throws InterruptedException {
        var gate = new SealDetectorAdmissionGameTests.WorkerGate();
        var release = new CountDownLatch(1);
        var started = new AtomicInteger();
        Thread first = new Thread(() -> awaitRelease(release), "arce-seal-owned-gate-diagnostic");
        Thread next = new Thread(started::incrementAndGet, "arce-seal-rejected-gate-diagnostic");
        try {
            gate.start(first);
            assertThrows(IllegalStateException.class, () -> gate.start(next));
            assertEquals(Thread.State.NEW, next.getState());
            assertEquals(0, started.get());
            assertThrows(IllegalStateException.class, gate::requireStopped);
        } finally {
            release.countDown();
            joinOwned(first);
        }
        assertDoesNotThrow(gate::requireStopped);
        gate.start(next);
        joinOwned(next);
        assertEquals(1, started.get());
        assertDoesNotThrow(gate::requireStopped);
    }

    private static void awaitRelease(CountDownLatch release) {
        boolean interrupted = false;
        while (release.getCount() != 0) {
            try { release.await(); }
            catch (InterruptedException ignored) { interrupted = true; }
        }
        if (interrupted) { Thread.currentThread().interrupt(); }
    }

    private static void joinOwned(Thread worker) throws InterruptedException {
        if (worker == null) { return; }
        worker.join(2_000);
        assertEquals(Thread.State.TERMINATED, worker.getState(), "Owned diagnostic worker must end before cleanup");
    }
}
