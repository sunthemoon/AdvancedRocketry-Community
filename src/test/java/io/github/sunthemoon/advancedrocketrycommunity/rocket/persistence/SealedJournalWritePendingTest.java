package io.github.sunthemoon.advancedrocketrycommunity.rocket.persistence;

import static org.junit.jupiter.api.Assertions.*;

import io.github.sunthemoon.advancedrocketrycommunity.persistence.CheckedJournalCheckpoint.Outcome;
import io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.CheckedSavedDataFile;
import io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.CheckedSavedDataPolicy;
import io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.ManagedSavedDataType;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.*;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.persistence.RocketTransactionSavedData.AbsenceStatus;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.stats.RocketStats;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.transaction.*;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** W129aM: N3-B2's exact U predicates on W129a's off-thread writer. WRITE_PENDING proves neither presence nor absence. */
class SealedJournalWritePendingTest {
    private static final ManagedSavedDataType TYPE = ManagedSavedDataType.ROCKET_TRANSACTIONS;
    private static final CheckedSavedDataPolicy ATOMIC = CheckedSavedDataPolicy.atomic(TYPE);
    @TempDir Path directory;
    @BeforeAll static void bootstrap() { MinecraftBootstrap.initialize(); }

    @Test void presenceAndAbsenceStayUnprovedWhileTheirWriteIsPendingOrInFlight() throws Exception {
        var data = new RocketTransactionSavedData(); var entry = entry(); var record = entry.record();
        var expected = RocketTransactionSavedData.expectedEntry(entry);
        var journal = data.journalFor(entry.snapshot(), entry.ownerId()); journal.write(record);
        var write = new PausedWrite();
        assertTrue(data.requestWrite(file(), write.policy));
        write.enter(data, () -> {
            assertFalse(data.acknowledgedEntry(expected), "WRITE_PENDING proved the retained U");
            assertEquals(AbsenceStatus.ASSEMBLY_JOURNAL_PENDING, data.absenceStatus(record.transactionId(), record.snapshotId(), record.rocketEntityId()));
        });
        write.release(); data.awaitWriter();
        assertEquals(Outcome.COMMITTED, data.writeOutcome()); assertTrue(data.acknowledgedEntry(expected));

        journal.remove(record.transactionId());
        var removal = new PausedWrite();
        assertTrue(data.requestWrite(file(), removal.policy));
        removal.enter(data, () -> {
            assertEquals(AbsenceStatus.WRITE_PENDING, data.absenceStatus(record.transactionId(), record.snapshotId(), record.rocketEntityId()));
            assertFalse(data.acknowledgedAbsent(record.transactionId(), record.snapshotId(), record.rocketEntityId()),
                    "WRITE_PENDING proved U's absence");
            assertFalse(data.acknowledgedEntry(expected), "WRITE_PENDING kept the removed U's presence proof");
        });
        removal.release(); data.awaitWriter();
        assertTrue(data.acknowledgedAbsent(record.transactionId(), record.snapshotId(), record.rocketEntityId()));
    }

    @Test void anotherPendingChangeWithholdsBothProofsTheOlderAcknowledgementWouldGive() throws Exception {
        var data = new RocketTransactionSavedData(); var retained = entry(); var absent = entry().record();
        var expected = RocketTransactionSavedData.expectedEntry(retained);
        data.journalFor(retained.snapshot(), retained.ownerId()).write(retained.record());
        assertEquals(Outcome.COMMITTED, data.saveChecked(file(), ATOMIC));
        assertTrue(data.acknowledgedEntry(expected));
        assertTrue(data.acknowledgedAbsent(absent.transactionId(), absent.snapshotId(), absent.rocketEntityId()));
        var other = entry(); data.journalFor(other.snapshot(), other.ownerId()).write(other.record());
        var write = new PausedWrite();
        assertTrue(data.requestWrite(file(), write.policy));
        write.enter(data, () -> {
            assertEquals(AbsenceStatus.WRITE_PENDING, data.absenceStatus(absent.transactionId(), absent.snapshotId(), absent.rocketEntityId()));
            assertFalse(data.acknowledgedEntry(expected), "A pending whole-root write kept an older presence proof");
            assertFalse(data.acknowledgedAbsent(absent.transactionId(), absent.snapshotId(), absent.rocketEntityId()),
                    "A pending whole-root write kept an older absence proof");
        });
        write.release(); data.awaitWriter();
        assertTrue(data.acknowledgedEntry(expected));
        assertTrue(data.acknowledgedAbsent(absent.transactionId(), absent.snapshotId(), absent.rocketEntityId()));
    }

    @Test void aCommittedOlderCaptureNeverProvesAChangeMadeDuringItsFlight() throws Exception {
        var data = new RocketTransactionSavedData(); var entry = entry(); var record = entry.record();
        var journal = data.journalFor(entry.snapshot(), entry.ownerId()); journal.write(record);
        var write = new PausedWrite();
        assertTrue(data.requestWrite(file(), write.policy));
        write.enter(data, () -> { });
        journal.remove(record.transactionId());
        write.release(); data.awaitWriter();
        assertEquals(Outcome.COMMITTED, data.writeOutcome());
        assertFalse(data.acknowledgedAbsent(record.transactionId(), record.snapshotId(), record.rocketEntityId()),
                "A capture taken with U present proved its later removal");
        assertFalse(data.acknowledgedEntry(RocketTransactionSavedData.expectedEntry(entry)), "A stale capture proved RAM-absent U");
        assertEquals(AbsenceStatus.WRITE_REQUIRED, data.absenceStatus(record.transactionId(), record.snapshotId(), record.rocketEntityId()));
        assertEquals(Outcome.COMMITTED, data.saveChecked(file(), ATOMIC));
        assertTrue(data.acknowledgedAbsent(record.transactionId(), record.snapshotId(), record.rocketEntityId()));
    }

    @Test void failedAndUnknownPublicationsAfterAPendingWriteNeverProveAbsence() throws Exception {
        var data = new RocketTransactionSavedData(); var entry = entry(); var record = entry.record();
        var journal = data.journalFor(entry.snapshot(), entry.ownerId()); journal.write(record);
        assertEquals(Outcome.COMMITTED, data.saveChecked(file(), ATOMIC));
        journal.remove(record.transactionId());
        for (boolean unknown : new boolean[] {false, true}) {
            var write = new PausedWrite(unknown);
            assertTrue(data.requestWrite(file(), write.policy));
            write.enter(data, () -> assertFalse(data.acknowledgedAbsent(record.transactionId(), record.snapshotId(), record.rocketEntityId())));
            write.release(); data.awaitWriter();
            assertEquals(unknown ? Outcome.UNKNOWN : Outcome.NOT_COMMITTED, data.writeOutcome());
            assertEquals(AbsenceStatus.ASSEMBLY_JOURNAL_UNPROVED, data.absenceStatus(record.transactionId(), record.snapshotId(), record.rocketEntityId()));
            assertFalse(data.acknowledgedAbsent(record.transactionId(), record.snapshotId(), record.rocketEntityId()));
        }
        assertEquals(Outcome.COMMITTED, data.saveChecked(file(), ATOMIC));
        assertTrue(data.acknowledgedAbsent(record.transactionId(), record.snapshotId(), record.rocketEntityId()));
    }

    private enum Mode { COMMIT, FAIL_BEFORE_MOVE, UNKNOWN }

    /** A committer held on the IO thread: the capture is in flight until {@link #release}. */
    private static final class PausedWrite {
        final CountDownLatch entered = new CountDownLatch(1), released = new CountDownLatch(1);
        final CheckedSavedDataPolicy policy;
        PausedWrite() { this(Mode.COMMIT); }
        PausedWrite(boolean unknown) { this(unknown ? Mode.UNKNOWN : Mode.FAIL_BEFORE_MOVE); }
        PausedWrite(Mode mode) {
            policy = new CheckedSavedDataPolicy(TYPE, (staged, target) -> {
                entered.countDown();
                try {
                    if (!released.await(30, TimeUnit.SECONDS)) { throw new java.io.IOException("W129aM test release timed out"); }
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt(); throw new java.io.IOException("W129aM test interrupted", interrupted);
                }
                if (mode == Mode.FAIL_BEFORE_MOVE) { throw new java.io.IOException("W129aM before move"); }
                CheckedSavedDataFile.atomicMove(staged, target);
                if (mode == Mode.UNKNOWN) { throw new java.io.IOException("W129aM after move"); }
            }, mode == Mode.UNKNOWN ? (target, type) -> { throw new IllegalStateException("W129aM unreadable readback"); } : ATOMIC.reader());
        }
        /** Pumps the detached journal's writer until the IO thread holds the capture, checking while it is pending. */
        void enter(RocketTransactionSavedData data, Runnable whilePending) throws InterruptedException {
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(30);
            while (true) {
                assertTrue(data.writerBusy(), "The request left no pending or active write");
                whilePending.run();
                if (entered.await(1, TimeUnit.MILLISECONDS)) { break; }
                assertTrue(System.nanoTime() < deadline, "The IO thread never started the capture");
                data.pollWriter();
            }
            whilePending.run();
        }
        void release() { released.countDown(); }
    }

    private Path file() { return directory.resolve(TYPE.fileName()); }
    private static RocketPersistedTransaction entry() {
        var snapshot = RocketStructureSnapshot.create(UUID.randomUUID(), ResourceLocation.tryParse("minecraft:overworld"),
                new RocketPosition(8, 64, -2), List.of(new RocketBlock(new RocketPosition(0, 0, 0),
                        new RocketBlockState(ResourceLocation.tryParse("minecraft:iron_block"), Map.of()))), List.of(),
                new RocketStats(1, 10, 0, 0, 0, 0, 0, 0), 42L);
        return new RocketPersistedTransaction(new RocketTransactionRecord(UUID.randomUUID(), RocketTransactionType.SEALED_DISASSEMBLY,
                RocketTransactionPhase.LOCKED, snapshot.snapshotId(), snapshot.contentHash(), RocketRegion.fromSnapshot(snapshot),
                0, UUID.randomUUID()), snapshot, UUID.randomUUID());
    }
}
