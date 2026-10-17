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

/** N2's first commit keeps the two-generation queue, including its failed-write admission rule. */
@Timeout(15)
class RocketJournalRemovalAdmissionTest {
    @TempDir Path directory;
    @BeforeAll static void bootstrap() { MinecraftBootstrap.initialize(); }

    @Test void fullQueueRefusesBeforeRemovalAndReloadRetainsTheRecord() {
        var data = new RocketTransferSavedData();
        var record = RocketTransferSavedDataTest.record();
        data.put(record);
        var file = directory.resolve(ManagedSavedDataType.ROCKET_TRANSFERS.fileName());
        data.saveChecked(file, atomic());
        var before = data.save(new net.minecraft.nbt.CompoundTag());
        try {
            // Two distinct generations, both belonging to other logical identities.
            data.setDirty();
            assertTrue(data.queueEffect(UUID.randomUUID(), () -> { }));
            data.remove(record.transferId());
            data.put(record);
            assertTrue(data.queueEffect(UUID.randomUUID(), () -> { }));
            assertFalse(data.canReserveRemovalEffect());
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
            assertTrue(data.canReserveRemovalEffect());
            data.remove(record.transferId());
            assertTrue(data.queueEffect(record.logicalRocketId(), calls::incrementAndGet));
            data.saveChecked(file, new CheckedSavedDataPolicy(ManagedSavedDataType.ROCKET_TRANSFERS, (staged, target) -> {
                CheckedSavedDataFile.atomicMove(staged, target);
                throw new IOException("Injected N2 uncertain removal");
            }, (target, type) -> { throw new IllegalStateException("Injected N2 unreadable readback"); }));
            data.pollWriter(1);
            assertEquals(RocketTransferSavedData.WriteOutcome.UNKNOWN, data.checkedWriteResult().outcome());
            assertEquals(0, calls.get());
            assertTrue(data.canReserveRemovalEffect(), "Failure must not tighten the removal preflight");
            assertFalse(data.canQueueEffect(), "Ordinary effect admission still rejects a failed write");
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
            assertTrue(data.canReserveRemovalEffect());
            data.remove(record.transferId());
            assertTrue(data.queueEffect(record.logicalRocketId(), () -> fail("Stopped removal ran"),
                    discarded::incrementAndGet));
            if (abort) { data.abort(); } else { data.stop(); }
            assertFalse(data.canReserveRemovalEffect());
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
