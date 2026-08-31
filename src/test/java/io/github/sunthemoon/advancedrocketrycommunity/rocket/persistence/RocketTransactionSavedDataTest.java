package io.github.sunthemoon.advancedrocketrycommunity.rocket.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketBlock;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketBlockState;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketPosition;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketStructureSnapshot;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.stats.RocketStats;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.transaction.RocketRegion;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.transaction.RocketTransactionJournal;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.transaction.RocketTransactionPhase;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.transaction.RocketTransactionRecord;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.transaction.RocketTransactionType;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

final class RocketTransactionSavedDataTest {
    private static final UUID TRANSACTION_ID = UUID.fromString("11111111-2222-3333-4444-555555555555");
    private static final UUID SNAPSHOT_ID = UUID.fromString("aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee");
    private static final UUID OWNER_ID = UUID.fromString("12345678-1234-5678-1234-567812345678");

    @Test
    void currentSchemaRoundTripsCompleteAuthorityDataAndMarksMutationsDirty() {
        RocketStructureSnapshot snapshot = snapshot();
        RocketTransactionRecord record = record(snapshot, RocketTransactionPhase.EXTRACTING, 1);
        RocketTransactionSavedData source = new RocketTransactionSavedData();
        RocketTransactionJournal journal = source.journalFor(snapshot, OWNER_ID);

        assertFalse(source.isDirty());
        journal.write(record);
        assertTrue(source.isDirty());
        assertEquals(1, source.entries().size());

        CompoundTag encoded = source.save(new CompoundTag());
        RocketTransactionSavedData restored = RocketTransactionSavedData.load(encoded);
        assertTrue(restored.operational());
        RocketPersistedTransaction restoredEntry = restored.entries().get(0);
        assertEquals(record, restoredEntry.record());
        assertEquals(snapshot.contentHash(), restoredEntry.snapshot().contentHash());
        assertEquals(snapshot.blocks(), restoredEntry.snapshot().blocks());
        assertEquals(OWNER_ID, restoredEntry.ownerId());
        assertEquals(encoded, restored.save(new CompoundTag()));

        journal.remove(TRANSACTION_ID);
        assertTrue(source.entries().isEmpty());
    }

    @Test
    void futureAndMalformedWholePayloadsArePreservedAndFailClosed() {
        CompoundTag future = validEncoded();
        future.putInt("schema_version", RocketTransactionSavedData.SCHEMA_VERSION + 1);
        future.putString("opaque_future_field", "keep-me");
        RocketTransactionSavedData futureData = RocketTransactionSavedData.load(future);

        assertFalse(futureData.operational());
        assertEquals(future, futureData.preservedBlockedData().orElseThrow());
        assertEquals(future, futureData.save(new CompoundTag()));
        assertThrows(
                IllegalStateException.class,
                () -> futureData.journalFor(snapshot(), OWNER_ID)
        );

        CompoundTag duplicate = validEncoded();
        ListTag entries = duplicate.getList("transactions", CompoundTag.TAG_COMPOUND);
        entries.add(entries.getCompound(0).copy());
        RocketTransactionSavedData malformedData = RocketTransactionSavedData.load(duplicate);
        assertFalse(malformedData.operational());
        assertTrue(malformedData.entries().isEmpty());
        assertEquals(duplicate, malformedData.save(new CompoundTag()));
    }

    @Test
    void tamperedRecordBindingsAndNegativeProgressAreRejectedOnLoad() {
        CompoundTag wrongRegion = validEncoded();
        wrongRegion.getList("transactions", CompoundTag.TAG_COMPOUND)
                .getCompound(0)
                .putIntArray("maximum", new int[]{99, 99, 99});
        assertFalse(RocketTransactionSavedData.load(wrongRegion).operational());

        CompoundTag wrongHash = validEncoded();
        wrongHash.getList("transactions", CompoundTag.TAG_COMPOUND)
                .getCompound(0)
                .putString("content_hash", "0".repeat(64));
        assertFalse(RocketTransactionSavedData.load(wrongHash).operational());

        CompoundTag negativeProgress = validEncoded();
        negativeProgress.getList("transactions", CompoundTag.TAG_COMPOUND)
                .getCompound(0)
                .putInt("progress", -1);
        assertFalse(RocketTransactionSavedData.load(negativeProgress).operational());
    }

    private static CompoundTag validEncoded() {
        RocketStructureSnapshot snapshot = snapshot();
        RocketTransactionSavedData data = new RocketTransactionSavedData();
        data.journalFor(snapshot, OWNER_ID).write(
                record(snapshot, RocketTransactionPhase.SPAWNED, snapshot.blocks().size())
        );
        return data.save(new CompoundTag());
    }

    private static RocketTransactionRecord record(
            RocketStructureSnapshot snapshot,
            RocketTransactionPhase phase,
            int progress
    ) {
        return new RocketTransactionRecord(
                TRANSACTION_ID,
                RocketTransactionType.ASSEMBLY,
                phase,
                snapshot.snapshotId(),
                snapshot.contentHash(),
                RocketRegion.fromSnapshot(snapshot),
                progress,
                phase.ordinal() >= RocketTransactionPhase.SPAWNED.ordinal()
                        ? UUID.fromString("99999999-8888-7777-6666-555555555555")
                        : null
        );
    }

    private static RocketStructureSnapshot snapshot() {
        return RocketStructureSnapshot.create(
                SNAPSHOT_ID,
                ResourceLocation.tryParse("minecraft:overworld"),
                new RocketPosition(8, 64, -2),
                List.of(new RocketBlock(
                        new RocketPosition(0, 0, 0),
                        new RocketBlockState(ResourceLocation.tryParse("minecraft:iron_block"), Map.of())
                )),
                List.of(),
                new RocketStats(1, 10, 0, 0, 0, 0, 0, 0),
                42L
        );
    }
}
