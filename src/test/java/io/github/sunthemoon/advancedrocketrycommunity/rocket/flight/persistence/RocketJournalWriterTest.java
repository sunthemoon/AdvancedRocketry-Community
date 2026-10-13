package io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.persistence;

import static org.junit.jupiter.api.Assertions.*;
import io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.CheckedSavedDataFile;
import io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.CheckedSavedDataPolicy;
import io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.ManagedSavedDataType;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFlightLimits;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

@Timeout(15)
class RocketJournalWriterTest {
    @TempDir Path directory;
    @BeforeAll static void bootstrap() { MinecraftBootstrap.initialize(); }
    private static final ManagedSavedDataType TYPE = ManagedSavedDataType.ROCKET_TRANSFERS;
    private Path target() { return directory.resolve(TYPE.fileName()); }
    private static CheckedSavedDataPolicy atomic() { return CheckedSavedDataPolicy.atomic(TYPE); }

    @Test void submissionReturnsBeforeForceAndOnlyOwnerPollingPublishesAnExactAcknowledgement() throws Exception {
        Thread owner = Thread.currentThread(); var entered = new CountDownLatch(1); var release = new CountDownLatch(1);
        var worker = new AtomicReference<Thread>(); var effect = new AtomicReference<Thread>();
        var data = new RocketTransferSavedData(); var record = RocketTransferSavedDataTest.record(); data.put(record);
        assertTrue(data.queueEffect(record.logicalRocketId(), () -> effect.set(Thread.currentThread())));
        var policy = new CheckedSavedDataPolicy(TYPE, (staged, target) -> {
            worker.set(Thread.currentThread()); entered.countDown();
            try { if (!release.await(10, TimeUnit.SECONDS)) { throw new IOException("Test release timed out"); } }
            catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); throw new IOException(interrupted); }
            CheckedSavedDataFile.atomicMove(staged, target);
        });
        try {
            assertTrue(data.requestWrite(target(), policy)); assertTrue(entered.await(10, TimeUnit.SECONDS));
            assertNotEquals(owner, worker.get()); assertFalse(data.acknowledgedCurrent()); assertNull(effect.get());
            assertFalse(Files.exists(target())); assertTrue(data.resourceMutationUnproved(record.logicalRocketId()));
            release.countDown(); data.drainForTests();
            assertTrue(data.acknowledgedCurrent()); assertNull(effect.get());
            data.pollWriter(1); assertSame(owner, effect.get());
            assertEquals(record.transferId(), data.acknowledgedGuard(record.logicalRocketId()).orElseThrow().transfer());
        } finally { release.countDown(); data.stop(); }
    }

    @Test void oneSuccessorIsTheHardLimitAndCapturedDomainListsCannotBeMutated() throws Exception {
        var entered = new CountDownLatch(1); var release = new CountDownLatch(1);
        var completed = new ArrayList<RocketJournalWriter.Completion>();
        var input = new ArrayList<>(List.of(RocketTransferSavedDataTest.record()));
        var captured = new RocketJournalWriter.Snapshot(1, input); input.clear();
        assertEquals(1, captured.records().size()); assertThrows(UnsupportedOperationException.class, () -> captured.records().clear());
        var policy = new CheckedSavedDataPolicy(TYPE, (staged, target) -> {
            entered.countDown();
            try { if (!release.await(10, TimeUnit.SECONDS)) { throw new IOException("Test release timed out"); } }
            catch (InterruptedException interrupted) { throw new IOException(interrupted); }
            CheckedSavedDataFile.atomicMove(staged, target);
        });
        try (var writer = new RocketJournalWriter(target())) {
            assertTrue(writer.request(captured, policy)); assertTrue(entered.await(10, TimeUnit.SECONDS));
            assertTrue(writer.request(new RocketJournalWriter.Snapshot(2, List.of()), atomic()));
            assertFalse(writer.request(new RocketJournalWriter.Snapshot(3, List.of()), atomic()));
            release.countDown(); writer.drain(completed::add);
            assertEquals(List.of(1L, 2L), completed.stream().map(RocketJournalWriter.Completion::sequence).toList());
            assertTrue(completed.stream().allMatch(c -> c.outcome() == RocketJournalCheckpoint.Outcome.COMMITTED));
        } finally { release.countDown(); }
        assertThrows(IllegalArgumentException.class, () -> new RocketJournalWriter.Snapshot(1,
                Collections.nCopies(RocketFlightLimits.MAX_ACTIVE_TRANSFERS + 1, RocketTransferSavedDataTest.record())));
        assertEquals(RocketFlightLimits.MAX_ACTIVE_TRANSFERS, new RocketJournalWriter.Snapshot(1,
                Collections.nCopies(RocketFlightLimits.MAX_ACTIVE_TRANSFERS, RocketTransferSavedDataTest.record())).records().size());
    }

    @Test void anOlderCompletionCannotClearANewerDirtyGenerationOrRunItsEffect() {
        var data = new RocketTransferSavedData(); var record = RocketTransferSavedDataTest.record(); data.put(record);
        var count = new AtomicInteger(); assertTrue(data.queueEffect(record.logicalRocketId(), count::incrementAndGet));
        try {
            data.requestWrite(target(), atomic());
            data.put(record.destinationSpawned(UUID.randomUUID()));
            data.drainForTests(); data.pollWriter(1);
            assertTrue(data.isDirty()); assertFalse(data.acknowledgedCurrent()); assertEquals(0, count.get());
            assertTrue(data.checkedWriteResult().acknowledgedSequence() < data.mutationSequence());
            data.requestWrite(target(), atomic()); data.drainForTests(); data.pollWriter(2);
            assertTrue(data.acknowledgedCurrent()); assertEquals(0, count.get());
        } finally { data.stop(); }
    }

    @Test void disjointExactGenerationsBothPublishAfterTheirSharedCurrentSnapshotIsAcknowledged() {
        var data = new RocketTransferSavedData(); var record = RocketTransferSavedDataTest.record();
        var effects = new AtomicInteger();
        assertTrue(data.queueEffect(UUID.randomUUID(), effects::incrementAndGet));
        try {
            data.requestWrite(target(), atomic());
            data.put(record);
            assertTrue(data.queueEffect(record.logicalRocketId(), effects::incrementAndGet));
            data.requestWrite(target(), atomic());
            assertEquals(0, effects.get());
            data.drainForTests(); assertEquals(0, effects.get());
            data.pollWriter(1); assertEquals(2, effects.get());
            data.pollWriter(2); assertEquals(2, effects.get());
            assertTrue(data.acknowledgedCurrent());
        } finally { data.stop(); }
    }

    @Test void definiteFailureAndUnknownOutcomesRetainEffectsAndUseBoundedShutdownRetries() {
        for (boolean unknown : List.of(false, true)) {
            var data = new RocketTransferSavedData(); var record = RocketTransferSavedDataTest.record();
            var calls = new AtomicInteger(); var effects = new AtomicInteger();
            data.saveChecked(target(), atomic()); data.put(record); data.queueEffect(record.logicalRocketId(), effects::incrementAndGet);
            var policy = new CheckedSavedDataPolicy(TYPE, (staged, target) -> {
                calls.incrementAndGet(); if (unknown) { Files.write(target, new byte[]{1, 2, 3}); }
                throw new IOException("Injected journal failure");
            });
            try {
                data.requestWrite(target(), policy); data.drainForTests(); data.pollWriter(100_000);
                assertEquals(RocketJournalWriter.DRAIN_UNKNOWN_ATTEMPTS, calls.get());
                assertFalse(data.acknowledgedCurrent()); assertTrue(data.isDirty()); assertEquals(0, effects.get());
                assertEquals(unknown ? RocketTransferSavedData.WriteOutcome.UNKNOWN : RocketTransferSavedData.WriteOutcome.NOT_COMMITTED,
                        data.checkedWriteResult().outcome());
                data.requestWrite(target(), atomic()); data.drainForTests(); data.pollWriter(100_001);
                assertTrue(data.acknowledgedCurrent()); assertEquals(1, effects.get());
            } finally { data.stop(); }
        }
    }

    @Test void shutdownUsesTheRealClockAndDrainsTheLatestAuthorityWithoutGameplay() {
        var data = new RocketTransferSavedData(); var record = RocketTransferSavedDataTest.record(); data.put(record);
        var effect = new AtomicInteger(); data.queueEffect(record.logicalRocketId(), effect::incrementAndGet);
        data.requestWrite(target(), atomic()); data.pollWriter(1_000_000);
        data.remove(record.transferId()); // A newer dirty mutation has not yet requested IO.
        data.stop(); assertEquals(0, effect.get()); assertTrue(data.acknowledgedCurrent());
        assertTrue(RocketTransferSavedData.load(CheckedSavedDataFile.readPayload(target(), TYPE).orElseThrow()).entries().isEmpty());
        data.setDirty(); data.save(target().toFile()); assertTrue(data.isDirty());
        assertFalse(data.queueEffect(record.logicalRocketId(), effect::incrementAndGet));
    }

    @Test void abortJoinsIOWithoutPublishingOrExecutingAContinuation() {
        var data = new RocketTransferSavedData(); var record = RocketTransferSavedDataTest.record(); data.put(record);
        var effect = new AtomicInteger(); data.queueEffect(record.logicalRocketId(), effect::incrementAndGet);
        data.requestWrite(target(), atomic()); data.abort();
        assertEquals(0, effect.get()); assertFalse(data.acknowledgedCurrent()); assertTrue(data.isDirty());
        assertEquals(record.transferId(), RocketTransferSavedData.load(CheckedSavedDataFile.readPayload(target(), TYPE).orElseThrow())
                .entries().get(0).transferId());
    }

    @Test void callbackAndTargetBoundsDoNotReplaceAnExistingContinuation() {
        var data = new RocketTransferSavedData(); var record = RocketTransferSavedDataTest.record(); var called = new AtomicInteger();
        data.put(record); assertTrue(data.queueEffect(record.logicalRocketId(), called::incrementAndGet));
        assertFalse(data.queueEffect(record.logicalRocketId(), () -> fail("Replaced exact continuation")));
        data.remove(record.transferId()); assertTrue(data.queueEffect(record.logicalRocketId(), called::incrementAndGet));
        data.put(record); assertFalse(data.queueEffect(record.logicalRocketId(), called::incrementAndGet));
        assertFalse(data.canQueueEffect()); data.requestWrite(target(), atomic());
        assertThrows(IllegalArgumentException.class, () -> data.requestWrite(directory.resolve("other.dat"), atomic()));
        data.stop(); assertEquals(0, called.get());
    }
}
