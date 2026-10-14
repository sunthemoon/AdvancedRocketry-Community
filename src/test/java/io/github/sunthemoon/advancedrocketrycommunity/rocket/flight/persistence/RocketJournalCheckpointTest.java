package io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.persistence;

import static org.junit.jupiter.api.Assertions.*;
import io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.CheckedSavedDataFile;
import io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.CheckedSavedDataPolicy;
import io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.ManagedSavedDataType;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketNativeResources;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class RocketJournalCheckpointTest {
    @Test void directoryIsForcedAfterRenameAndAfterCandidateReadback() {
        var forced = new java.util.concurrent.atomic.AtomicInteger();
        var payload = empty();
        var checkpoint = new RocketJournalCheckpoint(parent -> {
            assertEquals(directory.toAbsolutePath(), parent);
            assertEquals(payload, CheckedSavedDataFile.readPayload(target(), TYPE).orElseThrow());
            forced.incrementAndGet();
        });
        assertEquals(RocketJournalCheckpoint.Outcome.COMMITTED,
                checkpoint.write(target(), CheckedSavedDataPolicy.atomic(TYPE), 1, payload, Map.of()));
        assertEquals(1, forced.get());
        var movedThenFailed = new CheckedSavedDataPolicy(TYPE, (staged, target) -> {
            CheckedSavedDataFile.atomicMove(staged, target); throw new IOException("After rename");
        });
        assertEquals(RocketJournalCheckpoint.Outcome.COMMITTED,
                checkpoint.write(target(), movedThenFailed, 2, payload, Map.of()));
        assertEquals(2, forced.get());
        var unavailable = new RocketJournalCheckpoint(parent -> { throw new IOException("Directory force failed"); });
        assertEquals(RocketJournalCheckpoint.Outcome.UNKNOWN,
                unavailable.write(target(), CheckedSavedDataPolicy.atomic(TYPE), 3, payload, Map.of()));
        assertTrue(unavailable.acknowledged().isEmpty());
    }
    @Test void unrelatedWritesDoNotInvalidateAnUnchangedAcknowledgedRecord() {
        var data = new RocketTransferSavedData(); var record = RocketTransferSavedDataTest.record();
        try {
            data.put(record); data.saveChecked(target(), CheckedSavedDataPolicy.atomic(TYPE));
            data.setDirty();
            assertFalse(data.acknowledgedCurrent());
            assertFalse(data.resourceMutationUnproved(record.logicalRocketId()));
            data.put(record.destinationSpawned(UUID.randomUUID()));
            assertTrue(data.resourceMutationUnproved(record.logicalRocketId()));
            data.saveChecked(target(), CheckedSavedDataPolicy.atomic(TYPE));
            assertFalse(data.resourceMutationUnproved(record.logicalRocketId()));
            var authoritative = data.find(record.transferId()).orElseThrow();
            var respawn = authoritative.rebindDestination(UUID.randomUUID());
            data.put(respawn);
            assertTrue(data.resourceMutationUnproved(record.logicalRocketId()));
        } finally { data.stop(); }
    }
    @TempDir Path directory;
    @BeforeAll static void bootstrap() { MinecraftBootstrap.initialize(); }
    private static final ManagedSavedDataType TYPE = ManagedSavedDataType.ROCKET_TRANSFERS;
    private Path target() { return directory.resolve(TYPE.fileName()); }
    private static CompoundTag empty() { return new RocketTransferSavedData().save(new CompoundTag()); }
    @Test void acknowledgedLeaseIsNotRamAbsenceAndIsRevokedByEveryAuthorityMutation() {
        var data = new RocketTransferSavedData(); assertFalse(data.acknowledgedLease().getAsBoolean());
        data.saveChecked(target(), CheckedSavedDataPolicy.atomic(TYPE)); var baseline = data.acknowledgedLease(); assertTrue(baseline.getAsBoolean());
        var record = RocketTransferSavedDataTest.record(); data.put(record);
        assertFalse(baseline.getAsBoolean()); assertFalse(data.acknowledgedLease().getAsBoolean());
        data.saveChecked(target(), CheckedSavedDataPolicy.atomic(TYPE)); var named = data.acknowledgedLease(); assertTrue(named.getAsBoolean());
        data.remove(record.transferId()); assertFalse(named.getAsBoolean()); assertFalse(data.acknowledgedLease().getAsBoolean());
        assertTrue(data.find(record.transferId()).isEmpty());
        data.saveChecked(target(), CheckedSavedDataPolicy.atomic(TYPE)); assertTrue(data.acknowledgedLease().getAsBoolean());
        assertFalse(baseline.getAsBoolean()); assertFalse(named.getAsBoolean());
    }
    @Test void checkedEmptyBaselineAndRepeatedSaveExposeExactAcknowledgement() {
        var data = new RocketTransferSavedData();
        assertFalse(data.acknowledgedCurrent());
        assertEquals(RocketTransferSavedData.WriteOutcome.UNACKNOWLEDGED, data.checkedWriteResult().outcome());
        assertNull(data.checkedWriteResult().acknowledgedSequence());
        UUID logical = UUID.randomUUID();
        assertTrue(data.acknowledgedGuard(logical).isEmpty());
        data.saveChecked(target(), CheckedSavedDataPolicy.atomic(TYPE));
        assertTrue(data.acknowledgedCurrent());
        assertEquals(RocketTransferSavedData.WriteOutcome.COMMITTED, data.checkedWriteResult().outcome());
        assertEquals(data.mutationSequence(), data.checkedWriteResult().acknowledgedSequence());
        var guard = data.acknowledgedGuard(logical).orElseThrow();
        assertNull(guard.transfer()); assertNull(guard.recordDigest()); assertEquals(data.mutationSequence(), guard.sequence());
        assertEquals(empty(), CheckedSavedDataFile.readPayload(target(), TYPE).orElseThrow());
        data.saveChecked(target(), CheckedSavedDataPolicy.atomic(TYPE));
        assertEquals(guard, data.acknowledgedGuard(logical).orElseThrow());
    }
    @Test void failedCommitRetainsOldFileDirtyStateAndCannotAuthorizeAnyGuard() throws Exception {
        var data = new RocketTransferSavedData();
        data.saveChecked(target(), CheckedSavedDataPolicy.atomic(TYPE));
        byte[] before = Files.readAllBytes(target());
        data.setDirty();
        data.saveChecked(target(), new CheckedSavedDataPolicy(TYPE, (staged, target) -> { throw new IOException("Injected pre-move failure"); }));
        // Readback of an identical candidate is legitimate success, even when the move throws.
        assertTrue(data.acknowledgedCurrent());
        assertArrayEquals(before, Files.readAllBytes(target()));
        var checkpoint = new RocketJournalCheckpoint();
        var original = empty();
        assertEquals(RocketJournalCheckpoint.Outcome.COMMITTED, checkpoint.write(target(), CheckedSavedDataPolicy.atomic(TYPE), 1, original, Map.of()));
        var changed = original.copy(); changed.putString("probe", "changed");
        var policy = new CheckedSavedDataPolicy(TYPE, (staged, target) -> { throw new IOException("Injected failed move"); });
        for (int tick = 0; tick < 400; tick += 200) {
            assertEquals(RocketJournalCheckpoint.Outcome.NOT_COMMITTED, checkpoint.write(target(), policy, 2, changed, Map.of()));
            assertFalse(checkpoint.current(2)); assertTrue(checkpoint.current(1));
            assertEquals(original, CheckedSavedDataFile.readPayload(target(), TYPE).orElseThrow());
        }
    }
    @Test void exceptionAfterReplaceIsResolvedButUnreadableAuthorityClearsAcknowledgement() {
        var checkpoint = new RocketJournalCheckpoint(); var payload = empty();
        var movedThenFailed = new CheckedSavedDataPolicy(TYPE, (staged, target) -> {
            CheckedSavedDataFile.atomicMove(staged, target); throw new IOException("Injected after replacement");
        });
        assertEquals(RocketJournalCheckpoint.Outcome.COMMITTED, checkpoint.write(target(), movedThenFailed, 1, payload, Map.of()));
        var changed = payload.copy(); changed.putString("probe", "new generation");
        var unreadable = new CheckedSavedDataPolicy(TYPE, (staged, target) -> {
            Files.write(target, new byte[]{1, 2, 3}); throw new IOException("Injected unreadable outcome");
        });
        assertEquals(RocketJournalCheckpoint.Outcome.UNKNOWN, checkpoint.write(target(), unreadable, 2, changed, Map.of()));
        assertTrue(checkpoint.acknowledged().isEmpty()); assertFalse(checkpoint.current(1)); assertFalse(checkpoint.current(2));
        assertEquals(RocketJournalCheckpoint.Outcome.COMMITTED, checkpoint.write(target(), CheckedSavedDataPolicy.atomic(TYPE), 2, changed, Map.of()));
        assertTrue(checkpoint.current(2));
    }
    @Test void futureJournalIsNeverRewrittenByItsCheckedAdapter() throws Exception {
        var payload = empty(); payload.putInt("schema_version", 3);
        CheckedSavedDataFile.replace(target(), TYPE, () -> payload);
        byte[] before = Files.readAllBytes(target());
        var blocked = RocketTransferSavedData.load(payload); blocked.setDirty(); blocked.save(target().toFile());
        assertFalse(blocked.operational()); assertFalse(blocked.acknowledgedCurrent());
        assertArrayEquals(before, Files.readAllBytes(target())); assertTrue(blocked.isDirty());
    }
    @Test void failedPreparedAndAuthoritySnapshotsRetainDiskAuthorityAcrossTwoDecodedReloads() {
        var policy = new CheckedSavedDataPolicy(TYPE, (staged, target) -> { throw new IOException("Injected failed journal boundary"); });
        var data = new RocketTransferSavedData(); data.saveChecked(target(), CheckedSavedDataPolicy.atomic(TYPE));
        var prepared = RocketTransferSavedDataTest.record(); data.put(prepared);
        long sequence = data.mutationSequence();
        assertTrue(data.resourceMutationUnproved(prepared.logicalRocketId()));
        assertFalse(data.resourceMutationUnproved(UUID.randomUUID()));
        assertFalse(data.checkedWriteResult().acknowledgedCurrent());
        for (int attempt = 0; attempt < 3; attempt++) {
            data.saveChecked(target(), policy);
            assertTrue(data.isDirty()); assertFalse(data.acknowledgedCurrent());
            assertEquals(RocketTransferSavedData.WriteOutcome.NOT_COMMITTED, data.checkedWriteResult().outcome());
            assertTrue(data.checkedWriteResult().acknowledgedSequence() < sequence);
            assertEquals(sequence, data.mutationSequence()); assertTrue(data.acknowledgedGuard(prepared.logicalRocketId()).isEmpty());
            assertTrue(RocketTransferSavedData.load(CheckedSavedDataFile.readPayload(target(), TYPE).orElseThrow()).entries().isEmpty());
        }
        data.saveChecked(target(), CheckedSavedDataPolicy.atomic(TYPE));
        assertFalse(data.resourceMutationUnproved(prepared.logicalRocketId()));
        var spawned = prepared.destinationSpawned(UUID.randomUUID()); data.put(spawned); data.saveChecked(target(), policy);
        assertFalse(data.acknowledgedCurrent()); assertTrue(data.isDirty());
        for (int restart = 0; restart < 2; restart++) {
            var loaded = RocketTransferSavedData.load(CheckedSavedDataFile.readPayload(target(), TYPE).orElseThrow());
            assertEquals(prepared.phase(), loaded.find(prepared.transferId()).orElseThrow().phase());
            assertEquals(prepared.checksum(), loaded.find(prepared.transferId()).orElseThrow().checksum());
            assertFalse(loaded.acknowledgedCurrent());
            assertTrue(loaded.resourceMutationUnproved(prepared.logicalRocketId()));
            assertFalse(loaded.resourceMutationUnproved(UUID.randomUUID()));
        }
    }
    @Test void failedRemovalCannotReturnAnAcknowledgedNoTransferGuard() {
        var data = new RocketTransferSavedData(); var record = RocketTransferSavedDataTest.record();
        data.put(record); data.saveChecked(target(), CheckedSavedDataPolicy.atomic(TYPE));
        var before = data.acknowledgedGuard(record.logicalRocketId()).orElseThrow();
        assertEquals(record.transferId(), before.transfer()); assertNotNull(before.recordDigest());
        data.remove(record.transferId());
        assertTrue(data.mutationSequence() > before.sequence());
        assertTrue(data.resourceMutationUnproved(record.logicalRocketId()));
        assertFalse(data.resourceMutationUnproved(UUID.randomUUID()));
        data.saveChecked(target(), new CheckedSavedDataPolicy(TYPE, (staged, target) -> { throw new IOException("Injected removal failure"); }));
        assertTrue(data.isDirty()); assertTrue(data.acknowledgedGuard(record.logicalRocketId()).isEmpty());
        assertTrue(data.resourceMutationUnproved(record.logicalRocketId()));
        assertFalse(data.resourceMutationUnproved(UUID.randomUUID()));
        assertEquals(record.transferId(), RocketTransferSavedData.load(CheckedSavedDataFile.readPayload(target(), TYPE).orElseThrow())
                .findByLogicalRocket(record.logicalRocketId()).orElseThrow().transferId());
        data.saveChecked(target(), CheckedSavedDataPolicy.atomic(TYPE));
        assertFalse(data.resourceMutationUnproved(record.logicalRocketId()));
        assertNull(data.acknowledgedGuard(record.logicalRocketId()).orElseThrow().transfer());
    }
    @Test void unknownOutcomeRetainsAConservativeResourceFenceUntilAnExactRetry() {
        var data = new RocketTransferSavedData(); var record = RocketTransferSavedDataTest.record();
        data.put(record); data.saveChecked(target(), CheckedSavedDataPolicy.atomic(TYPE));
        data.put(record.destinationSpawned(UUID.randomUUID()));
        data.saveChecked(target(), new CheckedSavedDataPolicy(TYPE, (staged, target) -> {
            Files.write(target, new byte[]{1, 2, 3}); throw new IOException("Injected ambiguous authority");
        }));
        assertEquals(RocketTransferSavedData.WriteOutcome.UNKNOWN, data.checkedWriteResult().outcome());
        assertNull(data.checkedWriteResult().acknowledgedSequence());
        assertTrue(data.resourceMutationUnproved(record.logicalRocketId()));
        assertTrue(data.resourceMutationUnproved(UUID.randomUUID()));
        data.saveChecked(target(), CheckedSavedDataPolicy.atomic(TYPE));
        assertEquals(RocketTransferSavedData.WriteOutcome.COMMITTED, data.checkedWriteResult().outcome());
        assertFalse(data.resourceMutationUnproved(record.logicalRocketId()));
        assertFalse(data.resourceMutationUnproved(UUID.randomUUID()));
    }
    @Test void streamedDigestMatchesNc1AndKeepsByteAndDepthLimits() {
        var payload = new CompoundTag(); payload.putString("z", "last"); payload.putString("a", "first");
        var nested = new CompoundTag(); nested.putString("\uD83D\uDE00", "utf"); nested.putString("\uE000", "bmp"); payload.put("nested", nested);
        assertEquals(RocketNativeResources.signature(payload), RocketJournalDigest.of(payload, 4096));
        var boundary = new CompoundTag(); boundary.putByteArray("bytes", new byte[256]);
        int size = io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketNbtSize.uncompressedBytes(boundary);
        assertDoesNotThrow(() -> RocketJournalDigest.of(boundary, size));
        assertThrows(IllegalArgumentException.class, () -> RocketJournalDigest.of(boundary, size - 1));
        var deep = new CompoundTag(); var cursor = deep;
        for (int depth = 1; depth < 35; depth++) { var child = new CompoundTag(); cursor.put("c", child); cursor = child; }
        assertDoesNotThrow(() -> RocketJournalDigest.of(deep, 4096));
        cursor.put("c", new CompoundTag());
        assertThrows(IllegalArgumentException.class, () -> RocketJournalDigest.of(deep, 4096));
    }
}
