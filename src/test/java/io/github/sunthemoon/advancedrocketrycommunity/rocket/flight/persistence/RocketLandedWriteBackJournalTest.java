package io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.persistence;

import static org.junit.jupiter.api.Assertions.*;

import io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.CheckedSavedDataFile;
import io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.CheckedSavedDataPolicy;
import io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.ManagedSavedDataType;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFlightLimits;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketTransferRecord;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketPosition;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

/** W85 section 7 A0, the journal: the write-back of the identical landed record T (Design M, M3). */
@Timeout(30)
class RocketLandedWriteBackJournalTest {
    private static final ManagedSavedDataType TYPE = ManagedSavedDataType.ROCKET_TRANSFERS;
    @TempDir Path directory;
    @BeforeAll static void bootstrap() { MinecraftBootstrap.initialize(); }
    private Path target() { return directory.resolve(TYPE.fileName()); }
    private static CheckedSavedDataPolicy atomic() { return CheckedSavedDataPolicy.atomic(TYPE); }

    @Test void writeBackKeepsTheInsertChecksButNotTheStopRefusal() {
        var landed = RocketTransferSavedDataTest.committed(RocketTransferSavedDataTest.record());
        var data = new RocketTransferSavedData(); data.put(landed); data.remove(landed.transferId());
        data.validateWriteBack(landed);
        data.put(landed);
        assertThrows(IllegalArgumentException.class, () -> data.validateWriteBack(landed), "Same transfer ID");
        data.remove(landed.transferId());
        var overlapping = RocketTransferSavedDataTest.record(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                landed.destinationSnapshot().sourceOrigin());
        data.put(overlapping);
        assertThrows(IllegalArgumentException.class, () -> data.validateWriteBack(landed), "Overlapping reservation");
        data.remove(overlapping.transferId());
        for (int i = 0; i < RocketFlightLimits.MAX_ACTIVE_TRANSFERS; i++) {
            data.put(RocketTransferSavedDataTest.record(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                    new RocketPosition(1_000 + i * 64, 80, 1_000)));
        }
        assertThrows(IllegalArgumentException.class, () -> data.validateWriteBack(landed), "Full journal");
        var stopping = new RocketTransferSavedData(); stopping.stop();
        assertThrows(IllegalArgumentException.class, () -> stopping.validateNewRecord(landed));
        stopping.validateWriteBack(landed);
        var future = new RocketTransferSavedData().save(new CompoundTag());
        future.putInt("schema_version", RocketTransferSavedData.ROOT_SCHEMA_VERSION + 1);
        var blocked = RocketTransferSavedData.load(future);
        assertFalse(blocked.operational());
        assertThrows(IllegalArgumentException.class, () -> blocked.validateWriteBack(landed), "Blocked journal");
    }

    @Test void removalThenWriteBackEncodesTheIdenticalRecordAndVersion() throws IOException {
        var landed = RocketTransferSavedDataTest.committed(RocketTransferSavedDataTest.record());
        var data = new RocketTransferSavedData(); data.put(landed);
        try {
            data.saveChecked(target(), atomic());
            CompoundTag before = onlyRecord(target());
            data.remove(landed.transferId());
            data.requestWrite(target(), atomic()); data.drainForTests(); data.pollWriter(1);
            assertTrue(CheckedSavedDataFile.readPayload(target(), TYPE).orElseThrow().getList("transfers", 10).isEmpty());
            data.put(landed);
            assertEquals(RocketJournalCheckpoint.RecordVersion.of(before), RocketJournalCheckpoint.RecordVersion.of(landed));
            assertTrue(data.resourceMutationUnproved(landed.logicalRocketId()), "A written-back T is proved only by its own write");
            data.requestWrite(target(), atomic()); data.drainForTests(); data.pollWriter(2);
            assertTrue(data.acknowledgedCurrent());
            assertEquals(before, onlyRecord(target()));
            assertFalse(data.resourceMutationUnproved(landed.logicalRocketId()));
        } finally { data.stop(); }
    }

    @Test void writtenBackRecordStaysUnprovedAcrossFailedAndUnknownWritesAndKeepsTheOutbox() throws IOException {
        for (boolean unknown : List.of(false, true)) {
            var landed = RocketTransferSavedDataTest.committed(RocketTransferSavedDataTest.record());
            var other = RocketTransferSavedDataTest.record(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                    new RocketPosition(3_000, 80, 3_000));
            var data = new RocketTransferSavedData(); data.put(landed); data.put(other);
            assertTrue(data.electFirstMoonLanding(io.github.sunthemoon.advancedrocketrycommunity.progression.classic
                    .firstevent.FirstEvent.MoonLanding.create(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                            UUID.randomUUID(), UUID.randomUUID(), "0".repeat(64), 0, 0, 0, 0L, List.of())));
            try {
                data.saveChecked(target(), atomic());
                var outbox = data.firstMoonLandingRam();
                assertFalse(outbox.isEmpty());
                data.remove(landed.transferId());
                data.requestWrite(target(), atomic()); data.drainForTests(); data.pollWriter(1);
                assertFalse(data.resourceMutationUnproved(landed.logicalRocketId()), "The acknowledged removal proves L's absence");
                data.put(landed);
                var calls = new AtomicInteger();
                var failing = new CheckedSavedDataPolicy(TYPE, (staged, target) -> {
                    calls.incrementAndGet(); if (unknown) { Files.write(target, new byte[] {1, 2, 3}); }
                    throw new IOException("Injected write-back failure");
                });
                data.requestWrite(target(), failing); data.drainForTests(); data.pollWriter(100_000);
                assertEquals(unknown ? RocketTransferSavedData.WriteOutcome.UNKNOWN
                        : RocketTransferSavedData.WriteOutcome.NOT_COMMITTED, data.checkedWriteResult().outcome());
                assertTrue(data.resourceMutationUnproved(landed.logicalRocketId()));
                assertSame(landed, data.find(landed.transferId()).orElseThrow());
                assertSame(other, data.find(other.transferId()).orElseThrow());
                assertEquals(outbox, data.firstMoonLandingRam());
                data.requestWrite(target(), atomic()); data.drainForTests(); data.pollWriter(200_000);
                assertTrue(data.acknowledgedCurrent());
                assertFalse(data.resourceMutationUnproved(landed.logicalRocketId()));
                var disk = RocketTransferSavedData.load(CheckedSavedDataFile.readPayload(target(), TYPE).orElseThrow());
                assertEquals(List.of(landed.transferId(), other.transferId()).stream().sorted().toList(),
                        disk.entries().stream().map(RocketTransferRecord::transferId).toList());
                assertEquals(outbox, disk.firstMoonLandingRam());
            } finally { data.stop(); }
            Files.deleteIfExists(target());
        }
    }

    @Test void stopCarriesTheWrittenBackRecordIntoItsFinalCaptureUnlessTheDrainGivesUp() throws IOException {
        for (boolean givesUp : List.of(false, true)) {
            var landed = RocketTransferSavedDataTest.committed(RocketTransferSavedDataTest.record());
            var data = new RocketTransferSavedData(); data.put(landed);
            data.saveChecked(target(), atomic());
            data.remove(landed.transferId());
            var discards = new AtomicInteger();
            // M3 under a stop: the discarded removal continuation only puts T back; no continuation can be queued.
            assertTrue(data.queueEffect(landed.logicalRocketId(), () -> fail("Stopped gameplay ran"), () -> {
                discards.incrementAndGet(); data.validateWriteBack(landed); data.put(landed);
            }));
            var policy = givesUp ? new CheckedSavedDataPolicy(TYPE, (staged, target) -> {
                throw new IOException("Injected removal failure");
            }) : atomic();
            data.requestWrite(target(), policy);
            data.stop();
            assertEquals(1, discards.get());
            assertSame(landed, data.find(landed.transferId()).orElseThrow());
            var disk = RocketTransferSavedData.load(CheckedSavedDataFile.readPayload(target(), TYPE).orElseThrow());
            // Committed removal then final write with T; or the drain gave up and disk kept the generation with T.
            assertEquals(List.of(landed.transferId()), disk.entries().stream().map(RocketTransferRecord::transferId).toList());
            assertEquals(!givesUp, data.acknowledgedCurrent());
            Files.deleteIfExists(target());
        }
    }

    private static CompoundTag onlyRecord(Path file) throws IOException {
        var payload = CheckedSavedDataFile.readPayload(file, TYPE).orElseThrow();
        var records = payload.getList("transfers", CompoundTag.TAG_COMPOUND);
        assertEquals(1, records.size());
        return records.getCompound(0);
    }
}
