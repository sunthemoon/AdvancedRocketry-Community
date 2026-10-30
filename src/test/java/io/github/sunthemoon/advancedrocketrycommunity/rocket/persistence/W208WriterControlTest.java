package io.github.sunthemoon.advancedrocketrycommunity.rocket.persistence;

import static org.junit.jupiter.api.Assertions.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import io.github.sunthemoon.advancedrocketrycommunity.persistence.CheckedJournalCheckpoint.Outcome;
import io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.ManagedSavedDataType;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.Test;

class W208WriterControlTest {
    @TempDir Path directory;
    @BeforeAll static void bootstrap() { MinecraftBootstrap.initialize(); }

    @Test void cleanBaselineIsExplicitlyWrittenAndAcknowledged() {
        var data = new RocketTransactionSavedData();
        var file = directory.resolve(ManagedSavedDataType.ROCKET_TRANSACTIONS.fileName());
        assertFalse(data.isDirty());
        assertFalse(data.acknowledgedCurrent());
        try {
            W208JournalFixture.baseline(data, file);
            assertTrue(java.nio.file.Files.isRegularFile(file));
            assertTrue(data.acknowledgedCurrent());
            assertEquals(1, data.completedWriteCount());
        } finally { data.close(); }
    }

    @Test void cleanupReportsAnUnstartedWriterWithoutThrowing() {
        var result = W208JournalFixture.close(new RocketTransactionSavedData());
        assertEquals("NO_WRITER", result.get("reason"));
        assertEquals(false, result.get("success"));
    }

    @Test void oneShotUnknownClearsAuthorityThenRealAtomicRetryAcknowledges() {
        var data = data(); var control = new W208WriterControl("near-c1-unknown");
        var policy = control.policy("C1", () -> 0);
        var file = directory.resolve(ManagedSavedDataType.ROCKET_TRANSACTIONS.fileName());
        try {
            assertEquals(Outcome.UNKNOWN, data.saveChecked(file, policy));
            assertTrue(data.acknowledgement().isEmpty()); assertFalse(data.acknowledgedCurrent());
            assertEquals(Outcome.COMMITTED, data.saveChecked(file, policy));
            assertTrue(data.acknowledgedCurrent());
        } finally { control.release(); data.close(); }
    }

    @Test void interruptedCloseJoinsHeldWriterBeforeReturningAndPreservesFlag() throws Exception {
        var data = data(); var control = new W208WriterControl("near-interrupted");
        var file = directory.resolve(ManagedSavedDataType.ROCKET_TRANSACTIONS.fileName());
        try {
            assertTrue(data.requestWrite(file, control.policy("C1", () -> 1)));
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(30);
            while (!control.held() && System.nanoTime() < deadline) { Thread.sleep(1); }
            assertTrue(control.held()); assertFalse(data.acknowledgedCurrent());
            var released = new AtomicBoolean();
            Thread helper = new Thread(() -> {
                try { Thread.sleep(50); } catch (InterruptedException error) { Thread.currentThread().interrupt(); }
                released.set(true); control.release();
            });
            helper.setDaemon(true); helper.start(); Thread.currentThread().interrupt();
            var result = W208JournalFixture.close(data);
            assertTrue(Thread.currentThread().isInterrupted()); Thread.interrupted();
            assertTrue(released.get()); assertFalse(control.held());
            assertEquals("INTERRUPTED", result.get("reason")); assertEquals(false, result.get("success"));
            assertFalse(data.acknowledgedCurrent()); helper.join();
        } finally { Thread.interrupted(); control.release(); data.close(); }
    }

    private RocketTransactionSavedData data() {
        var data = new RocketTransactionSavedData(); var entry = W208JournalFixture.ballast(0, 1);
        data.journalFor(entry.snapshot(), entry.ownerId()).write(entry.record()); return data;
    }
    @Test void externalCeilingReleasesOnlyDiagnosticLatch() throws Exception {
        var control = new W208WriterControl("near-c1-expiry");
        assertFalse(control.held());
        control.release();
        assertFalse(control.held());
        assertFalse(control.claim("C3"));
        assertTrue(control.claim("C1"));
        assertFalse(control.claim("C1"));
    }
    @Test void controlCasesAreExplicitAndUnknownDoesNotHold() {
        assertThrows(IllegalArgumentException.class, () -> new W208WriterControl("unknown"));
        var control = new W208WriterControl("near-c3-unknown");
        assertTrue(control.claim("C3"));
        assertFalse(control.held());
    }
}
