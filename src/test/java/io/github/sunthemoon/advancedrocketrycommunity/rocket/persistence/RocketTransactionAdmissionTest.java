package io.github.sunthemoon.advancedrocketrycommunity.rocket.persistence;

import static org.junit.jupiter.api.Assertions.*;

import io.github.sunthemoon.advancedrocketrycommunity.persistence.BoundedNbt;
import io.github.sunthemoon.advancedrocketrycommunity.persistence.CheckedJournalCheckpoint.Outcome;
import io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.CheckedSavedDataFile;
import io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.CheckedSavedDataPolicy;
import io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.ManagedRootPresence;
import io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.ManagedSavedDataType;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.RocketLimits;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.*;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.stats.RocketStats;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.transaction.*;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.nio.file.Path;
import java.util.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class RocketTransactionAdmissionTest {
    private static final ManagedSavedDataType TYPE = ManagedSavedDataType.ROCKET_TRANSACTIONS;
    private static final int MAXIMUM = Math.toIntExact(TYPE.maxUncompressedBytes());
    private static final UUID OWNER = new UUID(0, 1);
    private static final ResourceLocation DIMENSION = ResourceLocation.tryParse("minecraft:overworld");
    @TempDir Path directory;
    @BeforeAll static void bootstrap() { MinecraftBootstrap.initialize(); }

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void firstAndRepeatedAdmissionDoNotReencodeResidents(boolean reload) {
        var data = new RocketTransactionSavedData();
        var snapshot = snapshot(256_000);
        for (int i = 0; i < 8; i++) { write(data, entry(snapshot)); }
        if (reload) { data = RocketTransactionSavedData.load(data.save(new CompoundTag())); }
        var candidate = entry(snapshot(2_000));
        assertTrue(legacyAdmission(data, candidate));
        var bean = (com.sun.management.ThreadMXBean) ManagementFactory.getThreadMXBean();
        assertTrue(bean.isThreadAllocatedMemorySupported());
        bean.setThreadAllocatedMemoryEnabled(true);
        long id = Thread.currentThread().getId();
        for (int i = 0; i < 3; i++) {
            long before = bean.getThreadAllocatedBytes(id);
            boolean result = data.canAppend(candidate);
            long allocated = bean.getThreadAllocatedBytes(id) - before;
            assertTrue(result);
            assertTrue(allocated < 256 * 1024,
                    "Admission rebuilt resident NBT: allocated " + allocated + " bytes (reload=" + reload + ")");
        }
    }

    @Test void sameSnapshotReplacementDoesNotReencodeTheUnchangedPayload() {
        var data = new RocketTransactionSavedData();
        var resident = entry(snapshot(RocketLimits.MAX_TOTAL_NBT_BYTES));
        write(data, resident);
        var bean = (com.sun.management.ThreadMXBean) ManagementFactory.getThreadMXBean();
        assertTrue(bean.isThreadAllocatedMemorySupported()); bean.setThreadAllocatedMemoryEnabled(true);
        long id = Thread.currentThread().getId();
        for (var phase : RocketTransactionPhase.values()) {
            var replacement = changed(resident, resident.snapshot(), phase, 7, null);
            long before = bean.getThreadAllocatedBytes(id);
            write(data, replacement);
            long allocated = bean.getThreadAllocatedBytes(id) - before;
            assertTrue(allocated < 256 * 1024, "Progress/phase write reencoded the snapshot: " + allocated);
            assertAdmission(data, entry(snapshot(2_000)), true);
        }
    }

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void exactRootByteBoundaryRejectedRetryAndRemovalMatchFullEncoding(boolean reload) {
        var at = entry(snapshot(4_096));
        var data = nearRoot(at);
        if (reload) { data = RocketTransactionSavedData.load(data.save(new CompoundTag())); }
        var under = entry(snapshot(4_095));
        var over = entry(snapshot(4_097));
        assertEquals(MAXIMUM - 1, fullBytes(data, under));
        assertEquals(MAXIMUM, fullBytes(data, at));
        assertEquals(MAXIMUM + 1, fullBytes(data, over));
        long sequence = data.mutationSequence();
        var before = data.entries();
        assertAdmission(data, under, true);
        assertAdmission(data, at, true);
        assertAdmission(data, over, false);
        assertAdmission(data, over, false);
        assertAdmission(data, at, true);
        assertEquals(sequence, data.mutationSequence());
        assertEquals(before, data.entries());
        write(data, at);
        assertAdmission(data, at, false);
        assertAdmission(data, under, false);
        data.journalFor(at.snapshot(), at.ownerId()).remove(at.record().transactionId());
        assertAdmission(data, at, true);
        assertAdmission(data, over, false);
    }

    @Test void replacementsChangeTheByteBoundaryWithoutReusingAnOldVersion() {
        var candidate = entry(snapshot(4_096));
        var data = nearRoot(candidate);
        var resident = data.entries().get(0);
        assertAdmission(data, candidate, true);
        var longer = changed(resident, resident.snapshot(), RocketTransactionPhase.COMMITTED, 7, UUID.randomUUID());
        write(data, longer);
        assertEquals(MAXIMUM + 3, fullBytes(data, candidate));
        assertAdmission(data, candidate, false);
        write(data, resident);
        assertAdmission(data, candidate, true);
        // A legal replacement may keep the snapshot ID while changing its immutable payload/content hash.
        int oldBytes = RocketNbtSize.uncompressedBytes(RocketSnapshotNbtCodec.encode(resident.snapshot()));
        var smaller = snapshot(oldBytes - 1, resident.snapshot().snapshotId());
        var replaced = changed(resident, smaller, resident.record().phase(), 8, resident.record().rocketEntityId());
        write(data, replaced);
        assertEquals(MAXIMUM - 1, fullBytes(data, candidate));
        assertAdmission(data, entry(snapshot(4_097)), true);
        write(data, resident);
        assertAdmission(data, entry(snapshot(4_097)), false);
    }

    @Test void countLimitDuplicateRejectionAndRemoveReinsertMatchTheOracle() {
        var data = new RocketTransactionSavedData();
        var snapshot = snapshot(2_000);
        for (int i = 0; i < RocketLimits.MAX_ACTIVE_TRANSACTIONS - 1; i++) { write(data, entry(snapshot)); }
        var last = entry(snapshot);
        assertAdmission(data, last, true);
        write(data, last);
        assertAdmission(data, entry(snapshot), false);
        assertAdmission(data, last, false);
        var replacement = changed(last, snapshot, RocketTransactionPhase.COMMITTED, 13, null);
        write(data, replacement);
        assertAdmission(data, entry(snapshot), false);
        data.journalFor(snapshot, OWNER).remove(last.record().transactionId());
        assertAdmission(data, last, true);
        write(data, last);
        assertAdmission(RocketTransactionSavedData.load(data.save(new CompoundTag())), entry(snapshot), false);
    }

    @Test void phaseProgressEntityAndDirtyPathsKeepCurrentRamSizing() {
        var data = new RocketTransactionSavedData();
        var resident = entry(snapshot(2_000));
        var candidate = entry(snapshot(2_001));
        write(data, resident);
        for (var phase : RocketTransactionPhase.values()) {
            write(data, changed(resident, resident.snapshot(), phase, Integer.MAX_VALUE, null));
            assertAdmission(data, candidate, true);
            write(data, changed(resident, resident.snapshot(), phase, 0, UUID.randomUUID()));
            assertAdmission(data, candidate, true);
        }
        data.setDirty(); data.setDirty(false);
        assertAdmission(data, candidate, true);
        var existing = data.entries(); long sequence = data.mutationSequence();
        data.journalFor(resident.snapshot(), OWNER).remove(UUID.randomUUID());
        assertEquals(sequence, data.mutationSequence()); assertEquals(existing, data.entries());
        assertAdmission(data, candidate, true);
        data.journalFor(resident.snapshot(), OWNER).remove(resident.record().transactionId());
        assertAdmission(data, candidate, true);
        write(data, resident);
        assertAdmission(data, candidate, true);
    }

    @Test void unsuccessfulRebindsLeaveTheOriginalSizeAndRecordIntact() {
        var data = new RocketTransactionSavedData();
        var resident = entry(snapshot(2_000)); write(data, resident);
        long sequence = data.mutationSequence(); var before = data.save(new CompoundTag());
        var wrongOwner = new RocketPersistedTransaction(resident.record(), resident.snapshot(), UUID.randomUUID());
        assertThrows(IllegalArgumentException.class, () -> write(data, wrongOwner));
        var different = snapshot(2_001);
        assertThrows(IllegalArgumentException.class, () -> write(data,
                changed(resident, different, resident.record().phase(), 0, null)));
        assertThrows(IllegalArgumentException.class, () -> data.journalFor(different, OWNER).write(resident.record()));
        var r = resident.record();
        assertThrows(IllegalArgumentException.class, () -> write(data, new RocketPersistedTransaction(
                new RocketTransactionRecord(r.transactionId(), RocketTransactionType.ASSEMBLY, r.phase(), r.snapshotId(),
                        r.contentHash(), r.region(), r.progress(), r.rocketEntityId()), resident.snapshot(), OWNER)));
        assertEquals(sequence, data.mutationSequence()); assertEquals(before, data.save(new CompoundTag()));
        assertAdmission(data, entry(snapshot(2_000)), true);
    }

    @Test void encodingFailureIsStillAnAdmissionRefusalNotANewWriteOrLoadRefusal() {
        var tooLarge = entry(snapshot(RocketLimits.MAX_TOTAL_NBT_BYTES + 1));
        var data = new RocketTransactionSavedData();
        var valid = entry(snapshot(2_000));
        assertAdmission(data, tooLarge, false);
        assertAdmission(data, tooLarge, false);
        assertAdmission(data, valid, true);
        assertDoesNotThrow(() -> write(data, tooLarge));
        assertAdmission(data, valid, false);
        data.journalFor(tooLarge.snapshot(), OWNER).remove(tooLarge.record().transactionId());
        assertAdmission(data, valid, true);
        // Duplicate decode fails after an earlier resident was decoded: no partial table may survive the block.
        write(data, valid); var malformed = data.save(new CompoundTag());
        var list = malformed.getList("transactions", Tag.TAG_COMPOUND); list.add(list.get(0).copy());
        var blocked = RocketTransactionSavedData.load(malformed);
        assertFalse(blocked.operational()); assertTrue(blocked.entries().isEmpty());
        assertAdmission(blocked, entry(snapshot(2_000)), false);
        assertEquals(malformed, blocked.save(new CompoundTag()));
    }

    @Test void maximumCandidateIsFullyValidatedAndDoesNotDependOnAdmissionHistory() {
        var data = new RocketTransactionSavedData();
        var maximum = entry(snapshot(RocketLimits.MAX_TOTAL_NBT_BYTES));
        assertEquals(RocketLimits.MAX_TOTAL_NBT_BYTES, RocketNbtSize.uncompressedBytes(RocketSnapshotNbtCodec.encode(maximum.snapshot())));
        assertAdmission(data, maximum, true);
        assertAdmission(data, maximum, true);
        write(data, maximum);
        assertAdmission(data, maximum, false);
        var loaded = RocketTransactionSavedData.load(data.save(new CompoundTag()));
        assertTrue(loaded.operational());
        assertAdmission(loaded, entry(maximum.snapshot()), true);
    }

    @Test void failedUnknownAndCommittedWritesNeverTurnSizingIntoDiskAuthority() {
        var data = new RocketTransactionSavedData();
        var resident = entry(snapshot(2_000)); write(data, resident);
        var candidate = entry(snapshot(2_001));
        var file = directory.resolve(TYPE.fileName());
        var atomic = CheckedSavedDataPolicy.atomic(TYPE);
        try {
            assertEquals(Outcome.COMMITTED, data.saveChecked(file, atomic));
            assertAdmission(data, candidate, true); assertTrue(data.acknowledgedCurrent());
            write(data, changed(resident, resident.snapshot(), RocketTransactionPhase.COMMITTED, 17, null));
            var fail = new CheckedSavedDataPolicy(TYPE, (staged, target) -> { throw new IOException("before move"); });
            assertEquals(Outcome.NOT_COMMITTED, data.saveChecked(file, fail));
            assertAdmission(data, candidate, true); assertFalse(data.acknowledgedCurrent());
            var unknown = new CheckedSavedDataPolicy(TYPE, (staged, target) -> {
                CheckedSavedDataFile.atomicMove(staged, target); throw new IOException("after move");
            }, (target, type) -> { throw new IllegalStateException("unreadable"); });
            assertEquals(Outcome.UNKNOWN, data.saveChecked(file, unknown));
            assertAdmission(data, candidate, true); assertFalse(data.acknowledgedCurrent());
            assertTrue(data.acknowledgement().isEmpty());
            data.journalFor(resident.snapshot(), OWNER).remove(resident.record().transactionId());
            assertAdmission(data, candidate, true);
            assertEquals(Outcome.COMMITTED, data.saveChecked(file, atomic));
            assertAdmission(data, candidate, true); assertTrue(data.acknowledgedCurrent());
            var loaded = RocketTransactionSavedData.load(data.save(new CompoundTag()));
            assertAdmission(loaded, candidate, true); assertFalse(loaded.acknowledgedCurrent());
        } finally { data.abort(); }
    }

    @Test void blockedUnreadableAndMigratedRootsUseTheSameCanonicalAdmissionRules() {
        var data = new RocketTransactionSavedData(); var resident = entry(snapshot(2_000)); write(data, resident);
        var root = data.save(new CompoundTag()); root.putInt("schema_version", 99);
        assertAdmission(RocketTransactionSavedData.load(root), entry(snapshot(2_000)), false);
        assertAdmission(RocketTransactionSavedData.created(ManagedRootPresence.UNREADABLE), entry(snapshot(2_000)), false);
        root = data.save(new CompoundTag()); root.putInt("schema_version", 1);
        root.putByteArray("ignored", new byte[20_000]);
        var loaded = RocketTransactionSavedData.load(root);
        assertTrue(loaded.operational()); assertAdmission(loaded, entry(snapshot(2_000)), true);
        assertEquals(data.save(new CompoundTag()), loaded.save(new CompoundTag()));
    }

    private static void assertAdmission(RocketTransactionSavedData data, RocketPersistedTransaction candidate, boolean expected) {
        assertEquals(expected, legacyAdmission(data, candidate), "Full re-encode oracle");
        assertEquals(expected, data.canAppend(candidate));
    }

    /** Original bounded admission, deliberately independent of all production cached counts. */
    private static boolean legacyAdmission(RocketTransactionSavedData data, RocketPersistedTransaction candidate) {
        var entries = data.entries();
        if (!data.operational() || entries.size() >= RocketLimits.MAX_ACTIVE_TRANSACTIONS
                || entries.stream().anyMatch(e -> e.record().transactionId().equals(candidate.record().transactionId()))) { return false; }
        long bytes = RocketNbtSize.uncompressedBytes(new RocketTransactionSavedData().save(new CompoundTag()));
        for (var entry : java.util.stream.Stream.concat(entries.stream(), java.util.stream.Stream.of(candidate)).toList()) {
            try {
                var size = BoundedNbt.measure(RocketTransactionCodec.encodeEntry(entry), MAXIMUM,
                        RocketTransactionWriter.DIGEST_DEPTH - 2, MAXIMUM);
                if (size == null) { return false; }
                bytes += size.bytes() - 3;
                if (bytes > MAXIMUM) { return false; }
            } catch (IllegalArgumentException invalid) { return false; }
        }
        return true;
    }

    private static int fullBytes(RocketTransactionSavedData data, RocketPersistedTransaction candidate) {
        var root = data.save(new CompoundTag());
        root.getList("transactions", Tag.TAG_COMPOUND).add(RocketTransactionCodec.encodeEntry(candidate));
        return RocketNbtSize.uncompressedBytes(root);
    }

    private static RocketTransactionSavedData nearRoot(RocketPersistedTransaction candidate) {
        var data = new RocketTransactionSavedData();
        var maximum = snapshot(RocketLimits.MAX_TOTAL_NBT_BYTES);
        for (int i = 0; i < 63; i++) { write(data, entry(maximum)); }
        var small = entry(snapshot(4_096));
        int missing = MAXIMUM - fullBytes(data, candidate);
        int snapshotBytes = 4_096 + missing - (RocketNbtSize.uncompressedBytes(RocketTransactionCodec.encodeEntry(small)) - 3);
        assertTrue(snapshotBytes > 4_096 && snapshotBytes <= RocketLimits.MAX_TOTAL_NBT_BYTES);
        write(data, entry(snapshot(snapshotBytes)));
        assertEquals(MAXIMUM, fullBytes(data, candidate));
        return data;
    }

    private static void write(RocketTransactionSavedData data, RocketPersistedTransaction entry) {
        data.journalFor(entry.snapshot(), entry.ownerId()).write(entry.record());
    }

    private static RocketPersistedTransaction entry(RocketStructureSnapshot snapshot) {
        return new RocketPersistedTransaction(new RocketTransactionRecord(UUID.randomUUID(), RocketTransactionType.SEALED_DISASSEMBLY,
                RocketTransactionPhase.LOCKED, snapshot.snapshotId(), snapshot.contentHash(), RocketRegion.fromSnapshot(snapshot),
                0, UUID.randomUUID()), snapshot, OWNER);
    }

    private static RocketPersistedTransaction changed(RocketPersistedTransaction entry, RocketStructureSnapshot snapshot,
            RocketTransactionPhase phase, int progress, UUID entity) {
        var r = entry.record();
        return new RocketPersistedTransaction(new RocketTransactionRecord(r.transactionId(), r.type(), phase,
                snapshot.snapshotId(), snapshot.contentHash(), RocketRegion.fromSnapshot(snapshot), progress, entity), snapshot, entry.ownerId());
    }

    private static RocketStructureSnapshot snapshot(int bytes) { return snapshot(bytes, UUID.randomUUID()); }
    private static RocketStructureSnapshot snapshot(int bytes, UUID id) {
        var base = snapshotWithPadding(id, 0);
        int header = RocketNbtSize.uncompressedBytes(RocketSnapshotNbtCodec.encode(base));
        assertTrue(bytes >= header);
        return snapshotWithPadding(id, bytes - header);
    }

    private static RocketStructureSnapshot snapshotWithPadding(UUID id, int padding) {
        var blocks = new ArrayList<RocketBlock>();
        for (int i = 0; i < 4; i++) {
            var payload = new CompoundTag();
            payload.putByteArray("payload", new byte[padding / 4 + (i < padding % 4 ? 1 : 0)]);
            payload.putString("utf\u0000", "\u0000\u0800\ud83d\ude80");
            blocks.add(new RocketBlock(new RocketPosition(i, 0, 0),
                    new RocketBlockState(ResourceLocation.tryParse("minecraft:chest"), Map.of()),
                    new RocketBlockEntityPayload(ResourceLocation.tryParse("w210:payload"), payload)));
        }
        return RocketStructureSnapshot.create(id, DIMENSION, new RocketPosition(0, 80, 0), blocks, List.of(),
                new RocketStats(4, 40, 0, 0, 0, 0, 0, 4), 0);
    }
}
