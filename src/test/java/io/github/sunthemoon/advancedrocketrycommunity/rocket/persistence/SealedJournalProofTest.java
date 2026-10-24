package io.github.sunthemoon.advancedrocketrycommunity.rocket.persistence;

import static org.junit.jupiter.api.Assertions.*;

import io.github.sunthemoon.advancedrocketrycommunity.persistence.CheckedJournalCheckpoint.Outcome;
import io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.CheckedSavedDataFile;
import io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.CheckedSavedDataPolicy;
import io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.ManagedSavedDataType;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.*;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.stats.RocketStats;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.transaction.*;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SealedJournalProofTest {
    private static final ManagedSavedDataType TYPE = ManagedSavedDataType.ROCKET_TRANSACTIONS;
    private static final CheckedSavedDataPolicy ATOMIC = CheckedSavedDataPolicy.atomic(TYPE);
    @TempDir Path directory;
    @BeforeAll static void bootstrap() { MinecraftBootstrap.initialize(); }

    @Test void presenceRequiresTheExactEntryInTheCurrentWholeRootAcknowledgement() throws Exception {
        var data = new RocketTransactionSavedData(); var entry = entry(); var expected = expected(entry);
        assertFalse(acknowledged(data, expected));
        data.journalFor(entry.snapshot(), entry.ownerId()).write(entry.record());
        assertFalse(acknowledged(data, expected));
        assertEquals(Outcome.COMMITTED, data.saveChecked(file(), ATOMIC));
        assertTrue(acknowledged(data, expected));
        var changedOwner = new RocketPersistedTransaction(entry.record(), entry.snapshot(), UUID.randomUUID());
        assertFalse(acknowledged(data, expected(changedOwner)));
        data.setDirty(); assertFalse(acknowledged(data, expected));
        assertEquals(Outcome.COMMITTED, data.saveChecked(file(), ATOMIC));
        assertTrue(acknowledged(data, expected));
    }

    @Test void phaseReplacementNeverReusesTheLockedEntryProof() throws Exception {
        var data = new RocketTransactionSavedData(); var entry = entry(); var locked = expected(entry);
        var journal = data.journalFor(entry.snapshot(), entry.ownerId()); journal.write(entry.record());
        data.saveChecked(file(), ATOMIC); assertTrue(acknowledged(data, locked));
        var before = entry.record();
        var restored = new RocketTransactionRecord(before.transactionId(), before.type(), RocketTransactionPhase.RESTORED,
                before.snapshotId(), before.contentHash(), before.region(), 1, before.rocketEntityId());
        var elected = expected(new RocketPersistedTransaction(restored, entry.snapshot(), entry.ownerId()));
        journal.write(restored);
        assertFalse(acknowledged(data, locked)); assertFalse(acknowledged(data, elected));
        data.saveChecked(file(), ATOMIC);
        assertFalse(acknowledged(data, locked)); assertTrue(acknowledged(data, elected));
    }

    @Test void removalNeedsAcknowledgedAbsenceAndConflictingSnapshotOrEntityHoldsIt() throws Exception {
        var data = new RocketTransactionSavedData(); var entry = entry();
        var journal = data.journalFor(entry.snapshot(), entry.ownerId()); journal.write(entry.record());
        data.saveChecked(file(), ATOMIC); assertFalse(absent(data, entry));
        journal.remove(entry.record().transactionId());
        assertFalse(absent(data, entry), "RAM absence ignored the acknowledged record map");
        data.saveChecked(file(), ATOMIC); assertTrue(absent(data, entry));
        var old = entry.record();
        journal.write(new RocketTransactionRecord(UUID.randomUUID(), old.type(), old.phase(), old.snapshotId(),
                old.contentHash(), old.region(), old.progress(), old.rocketEntityId()));
        data.saveChecked(file(), ATOMIC); assertFalse(absent(data, entry));
    }

    @Test void failedAndUnknownRemovalNeverGrantPresenceOrAbsence() throws Exception {
        var data = new RocketTransactionSavedData(); var entry = entry(); var expected = expected(entry);
        var journal = data.journalFor(entry.snapshot(), entry.ownerId()); journal.write(entry.record());
        data.saveChecked(file(), ATOMIC); journal.remove(entry.record().transactionId());
        var failed = new CheckedSavedDataPolicy(TYPE, (staged, target) -> { throw new IOException("before move"); });
        assertEquals(Outcome.NOT_COMMITTED, data.saveChecked(file(), failed));
        assertFalse(acknowledged(data, expected)); assertFalse(absent(data, entry));
        var unknown = new CheckedSavedDataPolicy(TYPE, (staged, target) -> {
            CheckedSavedDataFile.atomicMove(staged, target); throw new IOException("after move");
        }, (target, type) -> { throw new IllegalStateException("unreadable"); });
        assertEquals(Outcome.UNKNOWN, data.saveChecked(file(), unknown));
        assertFalse(acknowledged(data, expected)); assertFalse(absent(data, entry));
        assertEquals(Outcome.COMMITTED, data.saveChecked(file(), ATOMIC));
        assertTrue(absent(data, entry));
    }

    @Test void loadAndBlockedRootsCannotInventSessionProof() throws Exception {
        var data = new RocketTransactionSavedData(); var entry = entry(); var expected = expected(entry);
        data.journalFor(entry.snapshot(), entry.ownerId()).write(entry.record()); data.saveChecked(file(), ATOMIC);
        var loaded = RocketTransactionSavedData.load(data.save(new CompoundTag()));
        assertFalse(acknowledged(loaded, expected)); assertFalse(absent(loaded, entry));
        var future = data.save(new CompoundTag()); future.putInt("schema_version", 99);
        var blocked = RocketTransactionSavedData.load(future);
        assertFalse(acknowledged(blocked, expected)); assertFalse(absent(blocked, entry));
        assertEquals(future, blocked.save(new CompoundTag()));
    }

    @Test void appendCapacityPreflightDoesNotDirtyOrRewriteTheAcknowledgedRoot() throws Exception {
        var data = new RocketTransactionSavedData(); var entry = entry();
        data.saveChecked(file(), ATOMIC); var before = data.save(new CompoundTag());
        assertTrue(canAppend(data, entry)); assertTrue(data.acknowledgedCurrent());
        assertEquals(before, data.save(new CompoundTag()));
        data.journalFor(entry.snapshot(), entry.ownerId()).write(entry.record());
        assertFalse(canAppend(data, entry), "Append is not permission to replace a claimed transaction ID");
        var future = before.copy(); future.putInt("schema_version", 99);
        assertFalse(canAppend(RocketTransactionSavedData.load(future), entry));
    }

    @Test void appendRefusesTheExistingEntryAndWholeRootBoundsBeforeMutation() throws Exception {
        var data = new RocketTransactionSavedData();
        for (int index = 0; index < 256; index++) {
            var entry = entry(); data.journalFor(entry.snapshot(), entry.ownerId()).write(entry.record());
        }
        assertFalse(canAppend(data, entry())); assertEquals(256, data.entries().size());
        data = new RocketTransactionSavedData();
        var blocks = new java.util.ArrayList<RocketBlock>();
        for (int index = 0; index < 4; index++) {
            var payload = new CompoundTag(); payload.putByteArray("opaque", new byte[255_000]);
            blocks.add(new RocketBlock(new RocketPosition(index, 0, 0),
                    new RocketBlockState(ResourceLocation.tryParse("minecraft:chest"), Map.of()),
                    new RocketBlockEntityPayload(ResourceLocation.tryParse("w152:probe"), payload)));
        }
        var snapshot = RocketStructureSnapshot.create(UUID.randomUUID(), ResourceLocation.tryParse("minecraft:overworld"),
                new RocketPosition(0, 64, 0), blocks, List.of(), new RocketStats(4, 40, 0, 0, 0, 0, 0, 0), 1);
        RocketPersistedTransaction candidate = null;
        for (int index = 0; index < 66; index++) {
            candidate = new RocketPersistedTransaction(new RocketTransactionRecord(UUID.randomUUID(), RocketTransactionType.SEALED_DISASSEMBLY,
                    RocketTransactionPhase.LOCKED, snapshot.snapshotId(), snapshot.contentHash(), RocketRegion.fromSnapshot(snapshot),
                    0, UUID.randomUUID()), snapshot, UUID.randomUUID());
            if (index < 65) { data.journalFor(snapshot, candidate.ownerId()).write(candidate.record()); }
        }
        var before = data.entries(); assertEquals(65, before.size());
        assertFalse(canAppend(data, candidate), "Root capacity was replaced by only the per-snapshot limit");
        assertEquals(before, data.entries());
    }

    private static boolean canAppend(RocketTransactionSavedData data, RocketPersistedTransaction entry) throws Exception {
        return (boolean) RocketTransactionSavedData.class.getMethod("canAppend", RocketPersistedTransaction.class).invoke(data, entry);
    }

    private Path file() { return directory.resolve(TYPE.fileName()); }
    private static Object expected(RocketPersistedTransaction entry) throws Exception {
        return RocketTransactionSavedData.class.getMethod("expectedEntry", RocketPersistedTransaction.class).invoke(null, entry);
    }
    private static boolean acknowledged(RocketTransactionSavedData data, Object expected) throws Exception {
        return (boolean) RocketTransactionSavedData.class.getMethod("acknowledgedEntry", expected.getClass()).invoke(data, expected);
    }
    private static boolean absent(RocketTransactionSavedData data, RocketPersistedTransaction entry) throws Exception {
        var record = entry.record();
        return (boolean) RocketTransactionSavedData.class.getMethod("acknowledgedAbsent", UUID.class, UUID.class, UUID.class)
                .invoke(data, record.transactionId(), record.snapshotId(), record.rocketEntityId());
    }
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
