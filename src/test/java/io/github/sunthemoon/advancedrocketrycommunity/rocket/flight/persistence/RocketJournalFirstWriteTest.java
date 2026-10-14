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

    @Test void anUnknownMovedCandidateDoesNotOwnADifferentValidJournal() throws Exception {
        var data = RocketTransferSavedData.created(ManagedRootPresence.PROVEN_ABSENT);
        var attempts = new AtomicInteger();
        var policy = new CheckedSavedDataPolicy(ManagedSavedDataType.ROCKET_TRANSFERS, (staged, file) -> {
            if (attempts.incrementAndGet() == 1) {
                CheckedSavedDataFile.atomicMove(staged, file);
                throw new AssertionError("injected after first move");
            }
            CheckedSavedDataFile.atomicMove(staged, file);
        });
        try {
            data.requestWrite(target(), policy);
            await(data, RocketTransferSavedData.WriteOutcome.UNKNOWN);
            var foreign = new RocketTransferSavedData();
            foreign.put(RocketTransferSavedDataTest.committed(RocketTransferSavedDataTest.record()));
            CheckedSavedDataFile.replace(target(), ManagedSavedDataType.ROCKET_TRANSFERS,
                    () -> foreign.save(new net.minecraft.nbt.CompoundTag()));
            byte[] original = Files.readAllBytes(target());
            assertTrue(RocketTransferSavedData.load(CheckedSavedDataFile.readPayload(target(),
                    ManagedSavedDataType.ROCKET_TRANSFERS).orElseThrow()).operational());
            data.pollWriter(200);
            data.drainForTests();
            assertEquals(1, attempts.get(), "a readable but different payload must not authorize replacement");
            assertArrayEquals(original, Files.readAllBytes(target()));
            assertEquals(ManagedRootPresence.UNREADABLE, data.presence());
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

    @Test void unreadableTransitionCancelsAllWorkEvenIfTheAppearedFileIsRemoved() throws Exception {
        var data = RocketTransferSavedData.created(ManagedRootPresence.PROVEN_ABSENT);
        var replacements = new AtomicInteger();
        var discarded = new AtomicInteger();
        try {
            data.put(RocketTransferSavedDataTest.record());
            assertTrue(data.queueEffect(data.entries().get(0).logicalRocketId(), () -> fail("unreadable effect ran"),
                    discarded::incrementAndGet));
            Files.writeString(target(), "appeared journal");
            data.requestWrite(target(), new CheckedSavedDataPolicy(ManagedSavedDataType.ROCKET_TRANSFERS,
                    (staged, file) -> { replacements.incrementAndGet(); CheckedSavedDataFile.atomicMove(staged, file); }));
            long deadline = System.nanoTime() + 5_000_000_000L;
            while (data.operational() && System.nanoTime() < deadline) {
                data.pollWriter(0);
                Thread.sleep(1);
            }
            assertEquals(ManagedRootPresence.UNREADABLE, data.presence());
            assertEquals(1, discarded.get());
            Path pending = target().resolveSibling(target().getFileName() + ".arce-pending");
            for (int tick = 1; tick <= 1_000; tick++) {
                if (tick == 300) { Files.delete(target()); }
                data.pollWriter(tick);
                if (tick % 20 == 0) { data.save(target().toFile()); }
                assertFalse(data.isDirty(), "UNREADABLE must stay non-dirty at tick " + tick);
                assertFalse(Files.exists(pending), "no retry may stage at tick " + tick);
                if (tick < 300) { assertEquals("appeared journal", Files.readString(target())); }
                else { assertFalse(Files.exists(target()), "no retry may recreate the removed target"); }
                Thread.sleep(1);
            }
            assertEquals(0, replacements.get());
            assertFalse(data.requestWrite(target(), CheckedSavedDataPolicy.atomic(ManagedSavedDataType.ROCKET_TRANSFERS)));
            assertFalse(data.operational(), "removing the file cannot recover this cached session root");
        } finally { data.abort(); }
    }

    private Path target() { return root.resolve(ManagedSavedDataType.ROCKET_TRANSFERS.fileName()); }
}
