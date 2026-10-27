package io.github.sunthemoon.advancedrocketrycommunity.rocket.server;

import static io.github.sunthemoon.advancedrocketrycommunity.rocket.server.BayConsumptionFixtures.*;
import static org.junit.jupiter.api.Assertions.*;

import io.github.sunthemoon.advancedrocketrycommunity.rocket.cargo.RocketCargoWorkBudget;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketTransferRecord;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.persistence.RocketTransferConsumption;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.persistence.RocketTransferSavedData;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketPosition;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import java.util.LinkedHashMap;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** W170 A0: D5's journal mutation and T''s record method, with every refusal; a real detached transfer journal. */
final class BayJournalConsumptionTest {
    @BeforeAll static void bootstrap() { MinecraftBootstrap.initialize(); }

    private static RocketTransferRecord consumed(RocketTransferRecord record, int... bays) {
        var map = new LinkedHashMap<RocketPosition, io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.BayReceipt>();
        for (int i : bays) { map.put(bay(i), intent(record, i)); }
        var job = BayConsumedSnapshots.prepare(record.sourceSnapshot(), record.destinationSnapshot(), map).orElseThrow();
        var budget = new RocketCargoWorkBudget(); long tick = 1;
        while (!job.step(budget, tick)) { tick++; }
        var result = job.result().orElseThrow();
        return record.withConsumedSnapshots(result.source(), result.destination());
    }
    private static RocketTransferConsumption.Result apply(RocketTransferSavedData journal, RocketTransferRecord before, RocketTransferRecord after) {
        return RocketTransferConsumption.apply(journal, before, after, before.sourceSnapshot().contentHash(),
                before.destinationSnapshot().contentHash(), after.sourceSnapshot().contentHash(), after.destinationSnapshot().contentHash());
    }

    @Test void theRewriteKeepsTheAuthorityAndChangesOnlyTheSnapshotsAndChecksum() {
        var record = record(8);
        var journal = new RocketTransferSavedData(); journal.put(record);
        long sequence = journal.mutationSequence();
        var after = consumed(record, 0, 3, 7);
        assertEquals(RocketTransferConsumption.Result.CONSUMED, apply(journal, record, after));
        var live = journal.find(TRANSFER).orElseThrow();
        assertSame(after, live);
        assertEquals(sequence + 1, journal.mutationSequence(), "one sequenced mutation");
        assertEquals(record.sourceSnapshot().snapshotId(), live.sourceSnapshot().snapshotId());
        assertEquals(record.sourceFlightData(), live.sourceFlightData());
        assertEquals(record.phase(), live.phase());
        assertEquals(RocketTransferConsumption.Result.ALREADY_CONSUMED, apply(journal, record, after), "the (S,T') restart case changes nothing");
        assertEquals(sequence + 1, journal.mutationSequence());
        var reloaded = RocketTransferSavedData.load(journal.save(new CompoundTag()));
        assertEquals(after.checksum(), reloaded.find(TRANSFER).orElseThrow().checksum(), "T' round-trips with its existing fields");
    }

    @Test void everyRefusalChangesNothing() {
        var record = record(2);
        var after = consumed(record, 0);
        var journal = new RocketTransferSavedData(); journal.put(record);
        long sequence = journal.mutationSequence();
        var copy = RocketTransferSavedData.load(journal.save(new CompoundTag())).find(TRANSFER).orElseThrow();
        assertEquals(RocketTransferConsumption.Result.NOT_LIVE, apply(journal, copy, after), "not the live object");
        assertEquals(RocketTransferConsumption.Result.AUTHORITY_CHANGED, apply(journal, record, record), "an unchanged record");
        assertEquals(RocketTransferConsumption.Result.HASH_OUTSIDE_GROUP, RocketTransferConsumption.apply(journal, record, after,
                record.sourceSnapshot().contentHash(), record.destinationSnapshot().contentHash(), record.sourceSnapshot().contentHash(),
                after.destinationSnapshot().contentHash()), "a T' outside the group's after hashes");
        assertEquals(RocketTransferConsumption.Result.HASH_OUTSIDE_GROUP, RocketTransferConsumption.apply(journal, record, after,
                after.sourceSnapshot().contentHash(), record.destinationSnapshot().contentHash(), after.sourceSnapshot().contentHash(),
                after.destinationSnapshot().contentHash()), "a T outside the group's before hashes");
        assertEquals(sequence, journal.mutationSequence(), "the refusals changed nothing");
        assertSame(record, journal.find(TRANSFER).orElseThrow());
        var spawned = record.destinationSpawned(UUID.randomUUID());
        journal.put(spawned);
        long spawnedSequence = journal.mutationSequence();
        assertEquals(RocketTransferConsumption.Result.NOT_PREPARED, apply(journal, spawned, after), "only PREPARED");
        assertEquals(spawnedSequence, journal.mutationSequence());
        var blocked = RocketTransferSavedData.load(new CompoundTag());
        assertEquals(RocketTransferConsumption.Result.UNAVAILABLE, apply(blocked, record, after), "a blocked journal");
    }

    @Test void theRecordMethodRefusesAnythingButAPayloadOnlyPreparedRewrite() {
        var record = record(2);
        var after = consumed(record, 1);
        assertThrows(IllegalStateException.class, () -> record.destinationSpawned(UUID.randomUUID())
                .withConsumedSnapshots(after.sourceSnapshot(), after.destinationSnapshot()), "only PREPARED");
        assertThrows(IllegalArgumentException.class, () -> record.withConsumedSnapshots(record.destinationSnapshot(), after.destinationSnapshot()),
                "another snapshot ID, dimension and origin");
        var other = record(3);
        assertThrows(IllegalArgumentException.class, () -> record.withConsumedSnapshots(other.sourceSnapshot(), other.destinationSnapshot()),
                "another block set");
        var unchanged = record.withConsumedSnapshots(record.sourceSnapshot(), record.destinationSnapshot());
        assertEquals(record.checksum(), unchanged.checksum(), "the same snapshots give the same checksum");
    }
}
