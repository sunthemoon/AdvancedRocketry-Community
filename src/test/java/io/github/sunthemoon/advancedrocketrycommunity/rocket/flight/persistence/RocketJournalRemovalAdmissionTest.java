package io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.persistence;

import static org.junit.jupiter.api.Assertions.*;

import io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.CheckedSavedDataFile;
import io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.CheckedSavedDataPolicy;
import io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.ManagedSavedDataType;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import java.io.IOException;
import java.nio.file.Path;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

/** N2's capacity, failed-write and lifecycle guarantees on an explicit two-slot scheduler fixture. */
@Timeout(15)
class RocketJournalRemovalAdmissionTest {
    @TempDir Path directory;
    @BeforeAll static void bootstrap() { MinecraftBootstrap.initialize(); }

    @Test void fullQueueRefusesBeforeRemovalAndReloadRetainsTheRecord() {
        var data = new RocketTransferSavedData(2);
        var record = RocketTransferSavedDataTest.record();
        data.put(record);
        var file = directory.resolve(ManagedSavedDataType.ROCKET_TRANSFERS.fileName());
        data.saveChecked(file, atomic());
        var before = data.save(new net.minecraft.nbt.CompoundTag());
        try {
            assertTrue(data.queueEffect(RocketJournalScheduleFixtures.pendingAbsence(data, 1), () -> { }));
            assertTrue(data.queueEffect(RocketJournalScheduleFixtures.pendingAbsence(data, 2), () -> { }));
            assertTrue(data.reserve(record.logicalRocketId()).isEmpty());
            assertSame(record, data.find(record.transferId()).orElseThrow());
            assertEquals(before, data.save(new net.minecraft.nbt.CompoundTag()));
            assertEquals(before, RocketTransferSavedData.load(CheckedSavedDataFile.readPayload(file,
                    ManagedSavedDataType.ROCKET_TRANSFERS).orElseThrow()).save(new net.minecraft.nbt.CompoundTag()));
        } finally { data.abort(); }
    }

    @Test void unknownRemovalWaitsAndCompletesOnceAfterRetry() {
        var data = new RocketTransferSavedData();
        var record = RocketTransferSavedDataTest.record();
        var file = directory.resolve(ManagedSavedDataType.ROCKET_TRANSFERS.fileName());
        var calls = new AtomicInteger();
        data.put(record); data.saveChecked(file, atomic());
        try {
            var reservation = data.reserve(record.logicalRocketId()).orElseThrow();
            data.remove(record.transferId());
            assertTrue(data.queueEffect(reservation, false, false, () -> { calls.incrementAndGet(); return true; }, () -> { }));
            reservation.close();
            data.saveChecked(file, new CheckedSavedDataPolicy(ManagedSavedDataType.ROCKET_TRANSFERS, (staged, target) -> {
                CheckedSavedDataFile.atomicMove(staged, target);
                throw new IOException("Injected N2 uncertain removal");
            }, (target, type) -> { throw new IllegalStateException("Injected N2 unreadable readback"); }));
            data.pollWriter(1);
            assertEquals(RocketTransferSavedData.WriteOutcome.UNKNOWN, data.checkedWriteResult().outcome());
            assertEquals(0, calls.get());
            try (var unrelated = data.reserve(UUID.randomUUID()).orElse(null)) {
                assertNotNull(unrelated, "Failure must not tighten the removal preflight");
            }
            assertTrue(data.canQueueEffect(UUID.randomUUID()).isEmpty(), "Ordinary effect admission still rejects a failed write");
            data.saveChecked(file, atomic()); data.pollWriter(2); data.pollWriter(3);
            assertEquals(1, calls.get());
            assertTrue(RocketTransferSavedData.load(CheckedSavedDataFile.readPayload(file,
                    ManagedSavedDataType.ROCKET_TRANSFERS).orElseThrow()).entries().isEmpty());
        } finally { data.stop(); }
    }

    @Test void stopAndAbortRefuseNewRemovalAndDiscardPendingRemovalOnce() throws IOException {
        for (boolean abort : new boolean[] { false, true }) {
            var data = new RocketTransferSavedData();
            var record = RocketTransferSavedDataTest.record();
            var file = java.nio.file.Files.createDirectory(directory.resolve(abort ? "abort" : "stop"))
                    .resolve(ManagedSavedDataType.ROCKET_TRANSFERS.fileName());
            var discarded = new AtomicInteger();
            data.put(record); data.saveChecked(file, atomic());
            var reservation = data.reserve(record.logicalRocketId()).orElseThrow();
            data.remove(record.transferId());
            assertTrue(data.queueEffect(reservation, false, false, () -> fail("Stopped removal ran"),
                    discarded::incrementAndGet));
            reservation.close();
            if (abort) { data.abort(); } else { data.stop(); }
            assertTrue(data.reserve(record.logicalRocketId()).isEmpty());
            assertEquals(1, discarded.get());
            data.stop(); assertEquals(1, discarded.get());
            var reloaded = RocketTransferSavedData.load(CheckedSavedDataFile.readPayload(file,
                    ManagedSavedDataType.ROCKET_TRANSFERS).orElseThrow());
            assertEquals(abort ? 1 : 0, reloaded.entries().size());
        }
    }

    private static CheckedSavedDataPolicy atomic() {
        return CheckedSavedDataPolicy.atomic(ManagedSavedDataType.ROCKET_TRANSFERS);
    }
}
