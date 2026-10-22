package io.github.sunthemoon.advancedrocketrycommunity.rocket.persistence;

import static io.github.sunthemoon.advancedrocketrycommunity.persistence.CheckedJournalCheckpoint.Outcome.*;
import static io.github.sunthemoon.advancedrocketrycommunity.rocket.persistence.RocketTransactionSavedData.AbsenceStatus.*;
import static org.junit.jupiter.api.Assertions.*;

import io.github.sunthemoon.advancedrocketrycommunity.persistence.CanonicalNbtDigest;
import io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.CheckedSavedDataFile;
import io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.CheckedSavedDataPolicy;
import io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.ManagedSavedDataType;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.*;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.stats.RocketStats;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.transaction.*;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class RocketTransactionProofTest {
    private static final ManagedSavedDataType TYPE = ManagedSavedDataType.ROCKET_TRANSACTIONS;
    private static final CheckedSavedDataPolicy ATOMIC = CheckedSavedDataPolicy.atomic(TYPE);
    private static final UUID LOGICAL = UUID.randomUUID(), SNAPSHOT = UUID.randomUUID(), ENTITY = UUID.randomUUID();
    private static final UUID OWNER = UUID.randomUUID();
    @TempDir Path directory;
    @BeforeAll static void bootstrap() { MinecraftBootstrap.initialize(); }

    @Test void cleanBaselineNeedsOneWriteAndEncodingOrLoadingNeverProvesAbsence() {
        var data = new RocketTransactionSavedData();
        assertFalse(data.isDirty());
        assertEquals(WRITE_REQUIRED, status(data));
        var encoded = data.save(new CompoundTag());
        assertEquals(WRITE_REQUIRED, status(data));
        assertEquals(COMMITTED, data.saveChecked(target(), ATOMIC));
        assertEquals(PROVED, status(data));
        var ack = data.acknowledgement().orElseThrow();
        assertEquals(data.mutationSequence(), ack.sequence());
        assertEquals(digest(encoded), ack.digest());
        assertTrue(ack.records().isEmpty());
        assertEquals(encoded, read());
        var loaded = RocketTransactionSavedData.load(read());
        assertFalse(loaded.isDirty());
        assertFalse(loaded.acknowledgedCurrent());
        assertEquals(WRITE_REQUIRED, status(loaded));
        assertEquals(COMMITTED, loaded.saveChecked(target(), ATOMIC));
        assertEquals(PROVED, status(loaded));
    }

    @Test void staleAcknowledgementRequestsExactlyOneCheckedWriteInCallerPolling() {
        var data = new RocketTransactionSavedData();
        data.saveChecked(target(), ATOMIC);
        long before = data.mutationSequence();
        data.setDirty(true);
        assertEquals(before + 1, data.mutationSequence());
        var writes = new AtomicInteger();
        var policy = ATOMIC.withCommitter((staged, file) -> {
            writes.incrementAndGet(); CheckedSavedDataFile.atomicMove(staged, file);
        });
        int waiting = 0;
        for (int poll = 0; poll < 4; poll++) {
            if (status(data) == WRITE_REQUIRED) {
                data.saveChecked(target(), policy);
                waiting++; // D0 must return waiting after requesting, not continue in that call.
            } else { assertEquals(PROVED, status(data)); }
        }
        assertEquals(1, writes.get()); assertEquals(1, waiting);
        assertFalse(data.isDirty());
    }

    @Test void remainingAssemblyRecordIsPendingEvenWhenItsExactBytesAreAcknowledged() {
        var data = new RocketTransactionSavedData();
        var journal = insert(data, LOGICAL, SNAPSHOT, ENTITY, RocketTransactionType.ASSEMBLY);
        assertEquals(ASSEMBLY_JOURNAL_PENDING, status(data));
        data.saveChecked(target(), ATOMIC);
        var root = read();
        var record = root.getList("transactions", Tag.TAG_COMPOUND).getCompound(0);
        var ack = data.acknowledgement().orElseThrow();
        assertEquals(digest(root), ack.digest()); assertEquals(Map.of(LOGICAL, digest(record)), ack.records());
        assertThrows(UnsupportedOperationException.class, () -> ack.records().clear());
        assertTrue(data.acknowledgedCurrent());
        assertEquals(ASSEMBLY_JOURNAL_PENDING, status(data));
        // Logical identity must still fence if the live snapshot and entity have changed during travel.
        assertEquals(ASSEMBLY_JOURNAL_PENDING, data.absenceStatus(LOGICAL, UUID.randomUUID(), UUID.randomUUID()));
        journal.remove(LOGICAL);
        assertEquals(WRITE_REQUIRED, status(data));
        data.saveChecked(target(), ATOMIC);
        assertEquals(PROVED, status(data)); assertTrue(read().getList("transactions", Tag.TAG_COMPOUND).isEmpty());
    }

    @Test void disassemblyMatchesSnapshotOrEntityWithoutConfusingUnrelatedRecords() {
        var data = new RocketTransactionSavedData();
        insert(data, UUID.randomUUID(), SNAPSHOT, ENTITY, RocketTransactionType.DISASSEMBLY);
        data.saveChecked(target(), ATOMIC);
        assertEquals(ASSEMBLY_JOURNAL_PENDING, data.absenceStatus(LOGICAL, SNAPSHOT, UUID.randomUUID()));
        assertEquals(ASSEMBLY_JOURNAL_PENDING, data.absenceStatus(LOGICAL, UUID.randomUUID(), ENTITY));
        assertEquals(PROVED, data.absenceStatus(LOGICAL, UUID.randomUUID(), UUID.randomUUID()));
    }

    @Test void failedRemovalRetainsOldFileAndRefusesUntilLegitimateRemovalIsDurable() throws Exception {
        var data = new RocketTransactionSavedData();
        var journal = insert(data, LOGICAL, SNAPSHOT, ENTITY, RocketTransactionType.ASSEMBLY);
        data.saveChecked(target(), ATOMIC);
        var previous = data.acknowledgement().orElseThrow();
        byte[] bytes = Files.readAllBytes(target());
        journal.remove(LOGICAL);
        long sequence = data.mutationSequence();
        assertEquals(NOT_COMMITTED, data.saveChecked(target(), failing()));
        assertEquals(previous, data.acknowledgement().orElseThrow());
        assertArrayEquals(bytes, Files.readAllBytes(target()));
        assertEquals(sequence, data.mutationSequence()); assertTrue(data.isDirty());
        assertEquals(ASSEMBLY_JOURNAL_UNPROVED, status(data));
        assertEquals(ASSEMBLY_JOURNAL_PENDING, status(RocketTransactionSavedData.load(read())));
        data.saveChecked(target(), ATOMIC);
        assertEquals(PROVED, status(data));
    }

    @Test void firstDefiniteFailureRefusesWithoutInventingAnAcknowledgement() {
        var data = new RocketTransactionSavedData();
        assertEquals(NOT_COMMITTED, data.saveChecked(target(), failing()));
        assertEquals(ASSEMBLY_JOURNAL_UNPROVED, status(data));
        assertTrue(data.acknowledgement().isEmpty()); assertTrue(data.isDirty());
        assertFalse(Files.exists(target()));
        assertFalse(Files.exists(directory.resolve(TYPE.fileName() + ".arce-pending")));
    }

    @Test void replacementThenExceptionUsesExactReadbackAndForcesDirectory() {
        var forces = new AtomicInteger();
        var data = new RocketTransactionSavedData(parent -> {
            assertEquals(directory.toAbsolutePath(), parent); assertNotNull(read()); forces.incrementAndGet();
        });
        assertEquals(COMMITTED, data.saveChecked(target(), ATOMIC.withCommitter((staged, file) -> {
            CheckedSavedDataFile.atomicMove(staged, file); throw new IOException("after replacement");
        })));
        assertEquals(1, forces.get()); assertEquals(PROVED, status(data));
    }

    @Test void ambiguousReadbackRevokesProofAndStaysUnknownThroughPreReplacementFailure() {
        var data = new RocketTransactionSavedData(); data.saveChecked(target(), ATOMIC);
        var journal = insert(data, LOGICAL, SNAPSHOT, ENTITY, RocketTransactionType.ASSEMBLY);
        var unreadable = new CheckedSavedDataPolicy(TYPE, (staged, file) -> {
            CheckedSavedDataFile.atomicMove(staged, file); throw new IOException("after replacement");
        }, (file, type) -> { throw new IllegalStateException("unreadable"); });
        assertEquals(UNKNOWN, data.saveChecked(target(), unreadable));
        assertTrue(data.acknowledgement().isEmpty()); assertTrue(data.isDirty());
        journal.remove(LOGICAL);
        assertEquals(ASSEMBLY_JOURNAL_UNPROVED, status(data));
        Path pending = directory.resolve(TYPE.fileName() + ".arce-pending");
        assertDoesNotThrow(() -> Files.createDirectory(pending));
        assertEquals(UNKNOWN, data.saveChecked(target(), ATOMIC));
        assertTrue(data.acknowledgement().isEmpty());
        assertDoesNotThrow(() -> Files.delete(pending));
        assertEquals(COMMITTED, data.saveChecked(target(), ATOMIC));
        assertEquals(PROVED, status(data));
    }

    @Test void differentReadableRootAndDirectoryForceFailureAreUnknown() {
        var data = new RocketTransactionSavedData(); data.saveChecked(target(), ATOMIC);
        insert(data, LOGICAL, SNAPSHOT, ENTITY, RocketTransactionType.ASSEMBLY);
        var foreign = new RocketTransactionSavedData().save(new CompoundTag()); foreign.putString("foreign", "root");
        assertEquals(UNKNOWN, data.saveChecked(target(), new CheckedSavedDataPolicy(TYPE,
                failing().committer(), (file, type) -> Optional.of(foreign))));
        assertTrue(data.acknowledgement().isEmpty());
        var forceFailed = new RocketTransactionSavedData(parent -> { throw new IOException("force failure"); });
        assertEquals(UNKNOWN, forceFailed.saveChecked(target(), ATOMIC));
        assertEquals(ASSEMBLY_JOURNAL_UNPROVED, status(forceFailed));
        assertTrue(forceFailed.isDirty());
    }

    @Test void mutationDuringReplacementCannotAcknowledgeNewerRamOrClearDirty() throws Exception {
        var data = new RocketTransactionSavedData();
        long captured = data.mutationSequence();
        // ADR-074: IO never touches live RAM, so the owner mutates while the replacement is paused.
        var entered = new CountDownLatch(1); var release = new CountDownLatch(1);
        assertTrue(data.requestWrite(target(), ATOMIC.withCommitter((staged, file) -> {
            entered.countDown(); await(release); CheckedSavedDataFile.atomicMove(staged, file);
        })));
        assertTrue(entered.await(10, TimeUnit.SECONDS));
        data.setDirty(); release.countDown(); data.awaitWriter();
        assertEquals(COMMITTED, data.writeOutcome());
        assertEquals(captured, data.acknowledgement().orElseThrow().sequence());
        assertEquals(captured + 1, data.mutationSequence());
        assertTrue(data.isDirty()); assertEquals(WRITE_REQUIRED, status(data));
    }

    @Test void blockedRootsPreserveDiskAndNeverAcknowledgeOrExposeEmptyAsAbsent() throws Exception {
        for (boolean future : List.of(true, false)) {
            CompoundTag payload = new RocketTransactionSavedData().save(new CompoundTag());
            if (future) { payload.putInt("schema_version", 999); } else { payload.putString("transactions", "invalid"); }
            CheckedSavedDataFile.replace(target(), TYPE, () -> payload);
            byte[] before = Files.readAllBytes(target());
            var data = RocketTransactionSavedData.load(payload);
            data.setDirty(); data.save(target().toFile());
            assertFalse(data.operational()); assertEquals(ASSEMBLY_JOURNAL_UNPROVED, status(data));
            assertTrue(data.isDirty()); assertFalse(data.acknowledgedCurrent());
            assertEquals(payload, data.save(new CompoundTag())); assertArrayEquals(before, Files.readAllBytes(target()));
        }
    }

    @Test void autosaveUsesCheckedPathAndNeitherCleanSaveNorManualDirtyClearFakesProof() {
        var data = new RocketTransactionSavedData(); data.save(target().toFile());
        assertFalse(Files.exists(target())); assertEquals(WRITE_REQUIRED, status(data));
        data.setDirty(); data.save(target().toFile());
        data.awaitWriter(); // ADR-074 item 9: autosave only requests; publication follows on the owner thread.
        assertEquals(PROVED, status(data));
        data.setDirty(); data.setDirty(false);
        assertEquals(WRITE_REQUIRED, status(data));
        assertThrows(IllegalArgumentException.class, () -> data.saveChecked(target(),
                CheckedSavedDataPolicy.atomic(ManagedSavedDataType.ROCKET_TRANSFERS)));
        assertThrows(IllegalArgumentException.class, () -> data.saveChecked(directory.resolve("another.dat"), ATOMIC));
    }

    @Test void phaseMutationChangesRecordDigestAndNoOpRemovalDoesNotChangeSequence() {
        var data = new RocketTransactionSavedData();
        var journal = insert(data, LOGICAL, SNAPSHOT, ENTITY, RocketTransactionType.ASSEMBLY);
        data.saveChecked(target(), ATOMIC);
        String digest = data.acknowledgement().orElseThrow().records().get(LOGICAL);
        var before = data.entries().get(0).record();
        journal.write(new RocketTransactionRecord(LOGICAL, before.type(), RocketTransactionPhase.COMMITTED,
                SNAPSHOT, before.contentHash(), before.region(), 1, ENTITY));
        assertFalse(data.acknowledgedCurrent());
        data.saveChecked(target(), ATOMIC);
        assertNotEquals(digest, data.acknowledgement().orElseThrow().records().get(LOGICAL));
        long sequence = data.mutationSequence(); journal.remove(UUID.randomUUID());
        assertEquals(sequence, data.mutationSequence()); assertTrue(data.acknowledgedCurrent());
    }

    @Test void unreadableExistingRootCannotBecomeAnEmptyCheckedAuthority() throws Exception {
        Files.write(target(), new byte[]{1, 2, 3});
        var data = RocketTransactionSavedData.created(
                io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.ManagedRootPresence.UNREADABLE);
        assertEquals(NOT_COMMITTED, data.saveChecked(target(), ATOMIC));
        assertFalse(data.operational()); assertEquals(ASSEMBLY_JOURNAL_UNPROVED, status(data));
        assertTrue(data.claims(new ResourceLocation("minecraft:overworld"), new RocketPosition(0, 0, 0)));
        assertArrayEquals(new byte[]{1, 2, 3}, Files.readAllBytes(target()));
    }

    private Path target() { return directory.resolve(TYPE.fileName()); }
    static void await(CountDownLatch latch) throws IOException {
        try { if (!latch.await(10, TimeUnit.SECONDS)) { throw new IOException("Test release timed out"); } }
        catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); throw new IOException(interrupted); }
    }
    private CompoundTag read() { return CheckedSavedDataFile.readPayload(target(), TYPE).orElseThrow(); }
    private static String digest(CompoundTag tag) {
        return CanonicalNbtDigest.of(tag, Math.toIntExact(TYPE.maxUncompressedBytes()), 35);
    }
    private static RocketTransactionSavedData.AbsenceStatus status(RocketTransactionSavedData data) {
        return data.absenceStatus(LOGICAL, SNAPSHOT, ENTITY);
    }
    private static CheckedSavedDataPolicy failing() {
        return ATOMIC.withCommitter((staged, file) -> { throw new IOException("before replacement"); });
    }
    private static RocketTransactionJournal insert(RocketTransactionSavedData data, UUID transaction, UUID snapshotId,
                                                  UUID entity, RocketTransactionType type) {
        var snapshot = RocketStructureSnapshot.create(snapshotId, new ResourceLocation("minecraft:overworld"),
                new RocketPosition(8, 64, -2), List.of(new RocketBlock(new RocketPosition(0, 0, 0),
                        new RocketBlockState(new ResourceLocation("minecraft:iron_block"), Map.of()))), List.of(),
                new RocketStats(1, 10, 0, 0, 0, 0, 0, 0), 42L);
        var journal = data.journalFor(snapshot, OWNER);
        journal.write(new RocketTransactionRecord(transaction, type, RocketTransactionPhase.SPAWNED, snapshotId,
                snapshot.contentHash(), RocketRegion.fromSnapshot(snapshot), 1, entity));
        return journal;
    }
}
