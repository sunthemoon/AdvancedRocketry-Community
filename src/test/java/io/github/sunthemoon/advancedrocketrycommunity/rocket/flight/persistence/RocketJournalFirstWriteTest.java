package io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.persistence;

import static org.junit.jupiter.api.Assertions.*;

import io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.CheckedSavedDataFile;
import io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.CheckedSavedDataPolicy;
import io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.ManagedRootPresence;
import io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.ManagedSavedDataType;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class RocketJournalFirstWriteTest {
    @TempDir Path root;
    @BeforeAll static void bootstrap() { MinecraftBootstrap.initialize(); }

    @Test void aFailedFirstMoveRechecksAbsenceBeforeRetrying() throws Exception {
        assertForeignFileKept(false, false);
    }

    @Test void anUnknownBeforeMoveDoesNotOwnAForeignFile() throws Exception {
        assertForeignFileKept(true, false);
    }

    @Test void anUnknownMovedCandidateDoesNotOwnItsForeignReplacement() throws Exception {
        assertForeignFileKept(true, true);
    }

    private void assertForeignFileKept(boolean unknown, boolean moved) throws Exception {
        var data = RocketTransferSavedData.created(ManagedRootPresence.PROVEN_ABSENT);
        var attempts = new AtomicInteger();
        var policy = new CheckedSavedDataPolicy(ManagedSavedDataType.ROCKET_TRANSFERS, (staged, target) -> {
            if (attempts.incrementAndGet() == 1) {
                if (moved) { CheckedSavedDataFile.atomicMove(staged, target); }
                if (unknown) { throw new AssertionError("injected UNKNOWN"); }
                throw new IOException("injected NOT_COMMITTED");
            }
            CheckedSavedDataFile.atomicMove(staged, target);
        });
        try {
            assertTrue(data.requestWrite(target(), policy));
            await(data, unknown ? RocketTransferSavedData.WriteOutcome.UNKNOWN
                    : RocketTransferSavedData.WriteOutcome.NOT_COMMITTED);
            assertTrue(data.operational());
            Files.writeString(target(), "foreign journal after the failed first move");
            data.pollWriter(200);
            data.drainForTests();
            assertEquals("foreign journal after the failed first move", Files.readString(target()));
            assertEquals(1, attempts.get(), "the retry must not invoke replacement over a foreign file");
            assertEquals(ManagedRootPresence.UNREADABLE, data.presence());
        } finally { data.abort(); }
    }

    @Test void anUnknownExactMovedCandidateCanBeConfirmedByItsRetry() throws Exception {
        var data = RocketTransferSavedData.created(ManagedRootPresence.PROVEN_ABSENT);
        var attempts = new AtomicInteger();
        var policy = new CheckedSavedDataPolicy(ManagedSavedDataType.ROCKET_TRANSFERS, (staged, target) -> {
            CheckedSavedDataFile.atomicMove(staged, target);
            if (attempts.incrementAndGet() == 1) { throw new AssertionError("injected after move"); }
        });
        try {
            data.requestWrite(target(), policy);
            await(data, RocketTransferSavedData.WriteOutcome.UNKNOWN);
            assertFalse(data.acknowledgedCurrent());
            data.pollWriter(200);
            data.drainForTests();
            assertEquals(2, attempts.get());
            assertTrue(data.acknowledgedCurrent());
            assertTrue(data.operational());
        } finally { data.abort(); }
    }

    private static void await(RocketTransferSavedData data, RocketTransferSavedData.WriteOutcome expected)
            throws InterruptedException {
        long deadline = System.nanoTime() + 5_000_000_000L;
        while (data.checkedWriteResult().outcome() == RocketTransferSavedData.WriteOutcome.UNACKNOWLEDGED
                && System.nanoTime() < deadline) {
            data.pollWriter(0);
            Thread.sleep(1);
        }
        assertEquals(expected, data.checkedWriteResult().outcome());
    }

    private Path target() { return root.resolve(ManagedSavedDataType.ROCKET_TRANSFERS.fileName()); }
}
