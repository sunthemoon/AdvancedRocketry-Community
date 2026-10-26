package io.github.sunthemoon.advancedrocketrycommunity.rocket.server;

import static org.junit.jupiter.api.Assertions.*;

import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.*;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.persistence.*;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.stats.RocketStats;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.transaction.*;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SealedJournalEntryTest {
    @TempDir Path directory;
    @BeforeAll static void bootstrap() { MinecraftBootstrap.initialize(); }

    @Test void exactVersionsAreIdempotentAndDoNotGrantAuthorityBeforeCheckedSaving() {
        var data = new RocketTransactionSavedData(); var initial = entry(); var entry = new SealedJournalEntry(data, initial);
        entry.retain(); entry.retain(); assertEquals(1, data.entries().size()); assertTrue(entry.owned());
        assertFalse(entry.retainedAcknowledged()); save(data); assertTrue(entry.retainedAcknowledged());
        entry.elect(); entry.elect(); assertTrue(entry.elected()); assertFalse(entry.retainedAcknowledged());
        assertFalse(entry.electedAcknowledged()); save(data); assertTrue(entry.electedAcknowledged());
        entry.remove(); entry.remove(); assertFalse(entry.absentAcknowledged()); save(data); assertTrue(entry.absentAcknowledged());
        assertThrows(IllegalStateException.class, entry::retain); assertThrows(IllegalStateException.class, entry::elect);
    }
    @Test void foreignOwnerAndPhaseCannotBeOverwrittenOrRemoved() {
        var data = new RocketTransactionSavedData(); var initial = entry(); var entry = new SealedJournalEntry(data, initial);
        data.journalFor(initial.snapshot(), UUID.randomUUID()).write(initial.record());
        assertFalse(entry.compatible()); assertThrows(IllegalStateException.class, entry::retain);
        assertThrows(IllegalStateException.class, entry::elect); assertThrows(IllegalStateException.class, entry::remove);
        assertEquals(1, data.entries().size());
    }
    @Test void committedRecoveryNeverRegressesToRestored() {
        var data = new RocketTransactionSavedData(); var initial = entry(); var record = initial.record();
        var committed = new RocketPersistedTransaction(new RocketTransactionRecord(record.transactionId(), record.type(),
                RocketTransactionPhase.COMMITTED, record.snapshotId(), record.contentHash(), record.region(), 1, record.rocketEntityId()),
                initial.snapshot(), initial.ownerId());
        data.journalFor(committed.snapshot(), committed.ownerId()).write(committed.record());
        var entry = new SealedJournalEntry(data, committed); entry.elect(); save(data);
        assertEquals(RocketTransactionPhase.COMMITTED, data.entries().get(0).record().phase()); assertTrue(entry.electedAcknowledged());
    }
    @Test void anUnexpectedMissingRecordCannotBeRecreatedByIdempotentRetention() {
        var data = new RocketTransactionSavedData(); var initial = entry(); var entry = new SealedJournalEntry(data, initial);
        entry.retain(); data.journalFor(initial.snapshot(), initial.ownerId()).remove(initial.record().transactionId());
        assertThrows(IllegalStateException.class, entry::retain); assertTrue(data.entries().isEmpty());
    }
    private void save(RocketTransactionSavedData data) {
        data.save(directory.resolve(io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.ManagedSavedDataType
                .ROCKET_TRANSACTIONS.fileName()).toFile());
        // W129aM transition (ADR-074 item 9): save(File) only requests the write, so this detached journal pumps the
        // publication on its owner thread before the caller's next assertion, as W129a's autosave case does.
        long deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(30);
        while (data.writerBusy()) {
            assertTrue(System.nanoTime() < deadline, "The requested journal write never published");
            data.pollWriter(); java.util.concurrent.locks.LockSupport.parkNanos(1_000_000);
        }
    }
    private static RocketPersistedTransaction entry() {
        var snapshot = RocketStructureSnapshot.create(UUID.randomUUID(), ResourceLocation.tryParse("minecraft:overworld"),
                new RocketPosition(8, 64, -2), List.of(new RocketBlock(new RocketPosition(0, 0, 0),
                        new RocketBlockState(ResourceLocation.tryParse("minecraft:iron_block"), Map.of()))), List.of(),
                new RocketStats(1, 10, 0, 0, 0, 0, 0, 0), 42);
        return new RocketPersistedTransaction(new RocketTransactionRecord(UUID.randomUUID(), RocketTransactionType.SEALED_DISASSEMBLY,
                RocketTransactionPhase.LOCKED, snapshot.snapshotId(), snapshot.contentHash(), RocketRegion.fromSnapshot(snapshot),
                0, UUID.randomUUID()), snapshot, UUID.randomUUID());
    }
}
