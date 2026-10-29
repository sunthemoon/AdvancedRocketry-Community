package io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.persistence;

import static org.junit.jupiter.api.Assertions.*;

import io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.CheckedSavedDataFile;
import io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.CheckedSavedDataPolicy;
import io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.ManagedRootPresence;
import io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.ManagedSavedDataType;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketPosition;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import java.io.IOException;
import java.nio.file.Path;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class RocketTransferAbsenceTest {
    private static final ManagedSavedDataType TYPE = ManagedSavedDataType.ROCKET_TRANSFERS;
    @BeforeAll static void bootstrap() { MinecraftBootstrap.initialize(); }
    @TempDir Path directory;
    private Path file() { return directory.resolve(TYPE.fileName()); }
    private void commit(RocketTransferSavedData journal) { journal.saveChecked(file(), CheckedSavedDataPolicy.atomic(TYPE)); }
    private RocketTransferSavedData loadEmpty() {
        return RocketTransferSavedData.load(new RocketTransferSavedData().save(new CompoundTag()));
    }

    @Test void onlyValidatedLoadsProveInitialAbsenceWithoutGrantingWholeRootAuthority() {
        UUID transfer = UUID.randomUUID();
        assertFalse(new RocketTransferSavedData().transferAbsenceAcknowledged(transfer));
        assertFalse(RocketTransferSavedData.created(ManagedRootPresence.PROVEN_ABSENT).transferAbsenceAcknowledged(transfer));
        assertFalse(RocketTransferSavedData.created(ManagedRootPresence.UNREADABLE).transferAbsenceAcknowledged(transfer));
        var loaded = loadEmpty();
        assertTrue(loaded.transferAbsenceAcknowledged(transfer));
        assertFalse(loaded.acknowledgedCurrent());
        assertFalse(loaded.acknowledgedLease().getAsBoolean());
        assertFalse(loaded.logicalAcknowledged(UUID.randomUUID()));
        var invalid = new CompoundTag(); invalid.putInt("schema_version", 999);
        assertFalse(RocketTransferSavedData.load(invalid).transferAbsenceAcknowledged(transfer));
    }

    @Test void loadedRecordCannotBecomeAbsentProofByRamRemoval() {
        var original = new RocketTransferSavedData(); var record = RocketTransferSavedDataTest.record();
        original.put(record);
        var loaded = RocketTransferSavedData.load(original.save(new CompoundTag()));
        assertFalse(loaded.transferAbsenceAcknowledged(record.transferId()));
        loaded.remove(record.transferId());
        assertFalse(loaded.transferAbsenceAcknowledged(record.transferId()));
    }

    @Test void oldEmptyAcknowledgementCannotProveLaterInsertThenRemove() {
        var journal = new RocketTransferSavedData(); var record = RocketTransferSavedDataTest.record();
        try {
            commit(journal); assertTrue(journal.transferAbsenceAcknowledged(record.transferId()));
            journal.put(record); journal.remove(record.transferId());
            assertFalse(journal.transferAbsenceAcknowledged(record.transferId()));
            commit(journal); assertTrue(journal.transferAbsenceAcknowledged(record.transferId()));
        } finally { journal.stop(); }
    }

    @Test void failedRemovalKeepsDiskRecordAndRefusesReuseAcrossTwoLoads() {
        var journal = new RocketTransferSavedData(); var record = RocketTransferSavedDataTest.record();
        try {
            journal.put(record); commit(journal); journal.remove(record.transferId());
            journal.saveChecked(file(), new CheckedSavedDataPolicy(TYPE, (staged, target) -> { throw new IOException("Before move"); }));
            assertEquals(RocketTransferSavedData.WriteOutcome.NOT_COMMITTED, journal.checkedWriteResult().outcome());
            assertFalse(journal.transferAbsenceAcknowledged(record.transferId()));
            for (int restart = 0; restart < 2; restart++) {
                var loaded = RocketTransferSavedData.load(CheckedSavedDataFile.readPayload(file(), TYPE).orElseThrow());
                assertFalse(loaded.transferAbsenceAcknowledged(record.transferId()));
            }
            commit(journal); assertTrue(journal.transferAbsenceAcknowledged(record.transferId()));
            for (int restart = 0; restart < 2; restart++) {
                var loaded = RocketTransferSavedData.load(CheckedSavedDataFile.readPayload(file(), TYPE).orElseThrow());
                assertTrue(loaded.transferAbsenceAcknowledged(record.transferId()));
            }
        } finally { journal.stop(); }
    }

    @Test void unknownCannotFallBackToTheLoadedAbsence() {
        var journal = loadEmpty(); UUID transfer = UUID.randomUUID();
        try {
            assertTrue(journal.transferAbsenceAcknowledged(transfer));
            // Leave the valid candidate on disk but fail after replacement with unknowable completion.
            journal.saveChecked(file(), new CheckedSavedDataPolicy(TYPE, (staged, target) -> {
                CheckedSavedDataFile.atomicMove(staged, target); throw new AssertionError("Unknown completion");
            }));
            assertEquals(RocketTransferSavedData.WriteOutcome.UNKNOWN, journal.checkedWriteResult().outcome());
            assertFalse(journal.transferAbsenceAcknowledged(transfer));
            assertTrue(RocketTransferSavedData.load(CheckedSavedDataFile.readPayload(file(), TYPE).orElseThrow())
                    .transferAbsenceAcknowledged(transfer));
            commit(journal); assertTrue(journal.transferAbsenceAcknowledged(transfer));
        } finally { journal.stop(); }
    }

    @Test void unrelatedPendingRecordDoesNotRevokeAcknowledgedAbsence() {
        var journal = loadEmpty(); UUID absent = UUID.randomUUID();
        try {
            journal.put(RocketTransferSavedDataTest.record());
            assertTrue(journal.transferAbsenceAcknowledged(absent));
            commit(journal); journal.put(RocketTransferSavedDataTest.record(UUID.randomUUID(), UUID.randomUUID(),
                    UUID.randomUUID(), new RocketPosition(200, 100, 200)));
            assertFalse(journal.acknowledgedCurrent());
            assertTrue(journal.transferAbsenceAcknowledged(absent));
        } finally { journal.stop(); }
    }

    @Test void historyOverflowIsConservativeUntilANewAcknowledgement() {
        var journal = loadEmpty(); var removed = RocketTransferSavedDataTest.record();
        try {
            journal.put(removed); journal.remove(removed.transferId());
            for (int i = 0; i <= RocketJournalLeases.MAX_HISTORY; i++) {
                var other = RocketTransferSavedDataTest.record(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                        new RocketPosition(200, 100, 200));
                journal.put(other); journal.remove(other.transferId());
            }
            assertFalse(journal.transferAbsenceAcknowledged(removed.transferId()));
            assertFalse(journal.transferAbsenceAcknowledged(UUID.randomUUID()));
            commit(journal); assertTrue(journal.transferAbsenceAcknowledged(removed.transferId()));
        } finally { journal.stop(); }
    }

    @Test void stoppedOrAbortedLoadedRootsCannotReconcile() {
        UUID transfer = UUID.randomUUID();
        var stopped = loadEmpty(); stopped.stop();
        var aborted = loadEmpty(); aborted.abort();
        assertFalse(stopped.transferAbsenceAcknowledged(transfer));
        assertFalse(aborted.transferAbsenceAcknowledged(transfer));
    }
}
