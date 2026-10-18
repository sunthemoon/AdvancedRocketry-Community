package io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.persistence;

import static org.junit.jupiter.api.Assertions.*;

import io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.CheckedSavedDataFile;
import io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.CheckedSavedDataPolicy;
import io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.ManagedSavedDataType;
import io.github.sunthemoon.advancedrocketrycommunity.progression.classic.firstevent.FirstEvent;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

@Timeout(15)
class RocketJournalScheduleTest {
    @TempDir Path directory;
    @BeforeAll static void bootstrap() { MinecraftBootstrap.initialize(); }
    private Path file() { return directory.resolve(ManagedSavedDataType.ROCKET_TRANSFERS.fileName()); }
    private static CheckedSavedDataPolicy atomic() { return CheckedSavedDataPolicy.atomic(ManagedSavedDataType.ROCKET_TRANSFERS); }
    private void commit(RocketTransferSavedData data) { data.saveChecked(file(), atomic()); }

    @Test void baselineAndTouchedAbsencesHaveDistinctInterlocks() {
        var data = new RocketTransferSavedData(); var untouched = UUID.randomUUID();
        assertFalse(data.actionable(untouched)); assertFalse(data.resourceMutationUnproved(untouched));
        var touched = RocketJournalScheduleFixtures.pendingAbsence(data, 1);
        assertTrue(data.resourceMutationUnproved(touched)); assertFalse(data.actionable(touched));
        try {
            commit(data); assertTrue(data.actionable(touched)); assertTrue(data.actionable(untouched));
            var lease = data.actionableLease(touched);
            try (var token = data.reserve(touched).orElseThrow()) {
                assertFalse(data.actionable(touched)); assertTrue(data.actionable(touched, token));
                assertTrue(data.resourceMutationUnproved(touched)); assertFalse(lease.getAsBoolean()); assertFalse(data.clean());
            }
            assertTrue(lease.getAsBoolean()); assertTrue(data.clean());
            data.stop(); assertFalse(data.actionable(untouched)); assertFalse(data.reserve(untouched).isPresent());
        } finally { data.stop(); }
    }

    @Test void identicalPutAndRemoveReaddCannotBorrowAnOlderAcknowledgement() {
        var data = new RocketTransferSavedData(); var record = RocketJournalScheduleFixtures.record(1);
        data.put(record);
        try {
            commit(data); var version = data.actionableLease(record.logicalRocketId());
            data.remove(record.transferId()); data.put(record);
            assertFalse(version.getAsBoolean()); assertFalse(data.actionable(record.logicalRocketId()));
            assertTrue(data.resourceMutationUnproved(record.logicalRocketId()));
            commit(data); assertTrue(data.actionable(record.logicalRocketId())); assertFalse(version.getAsBoolean());
            data.remove(record.transferId()); commit(data);
            var absence = data.actionableLease(record.logicalRocketId()); assertTrue(absence.getAsBoolean());
            data.put(record); data.remove(record.transferId()); assertFalse(absence.getAsBoolean());
            assertTrue(data.resourceMutationUnproved(record.logicalRocketId()));
            commit(data); assertFalse(absence.getAsBoolean()); assertTrue(data.actionable(record.logicalRocketId()));
        } finally { data.stop(); }
    }

    @Test void coalescedThirdChangeRunsAfterTheSnapshotThatActuallyCoversIt() throws Exception {
        var data = new RocketTransferSavedData(); var entered = new CountDownLatch(1); var release = new CountDownLatch(1);
        var ran = new ArrayList<Integer>(); var discarded = new AtomicInteger();
        try {
            for (int i = 1; i <= 3; i++) {
                int index = i; var logical = RocketJournalScheduleFixtures.pendingAbsence(data, i);
                assertTrue(data.queueEffect(logical, () -> { ran.add(index); return true; }, discarded::incrementAndGet));
                if (i == 1) { assertTrue(data.requestWrite(file(), held(entered, release))); assertTrue(entered.await(10, TimeUnit.SECONDS)); }
                else { assertEquals(i == 2, data.requestWrite(file(), atomic())); }
            }
            release.countDown(); data.drainForTests(); data.pollWriter(1);
            assertEquals(List.of(1, 2), ran); assertEquals(0, discarded.get());
            assertFalse(data.actionable(RocketJournalScheduleFixtures.record(3).logicalRocketId()));
            commit(data); data.pollWriter(2);
            assertEquals(List.of(1, 2, 3), ran); assertEquals(0, discarded.get()); assertTrue(data.clean());
        } finally { release.countDown(); data.stop(); }
    }

    @Test void committedRecordRunsWhileTheDisjointSuccessorIsHeld() throws Exception {
        var data = new RocketTransferSavedData(); var entered = new CountDownLatch(1); var release = new CountDownLatch(1);
        var first = RocketJournalScheduleFixtures.record(1); var second = RocketJournalScheduleFixtures.record(2);
        var ran = new ArrayList<Integer>();
        try {
            data.put(first); assertTrue(data.queueEffect(first.logicalRocketId(), () -> ran.add(1)));
            data.requestWrite(file(), atomic());
            data.put(second); assertTrue(data.queueEffect(second.logicalRocketId(), () -> ran.add(2)));
            data.requestWrite(file(), held(entered, release));
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
            while (entered.getCount() != 0 && System.nanoTime() < deadline) { data.pollWriter(1); Thread.sleep(1); }
            assertEquals(0, entered.getCount());
            data.pollWriter(1);
            assertEquals(List.of(1), ran); assertTrue(data.actionable(first.logicalRocketId()));
            assertFalse(data.actionable(second.logicalRocketId())); assertFalse(data.clean());
            release.countDown(); data.drainForTests(); data.pollWriter(2);
            assertEquals(List.of(1, 2), ran); assertTrue(data.clean());
        } finally { release.countDown(); data.stop(); }
    }

    @Test void fifoVisitStopsAtTheFirstUnacknowledgedDependency() {
        var data = new RocketTransferSavedData(); var ran = new ArrayList<Integer>();
        try {
            var second = RocketJournalScheduleFixtures.pendingAbsence(data, 2);
            assertTrue(data.requestWrite(file(), atomic()));
            var first = RocketJournalScheduleFixtures.pendingAbsence(data, 1);
            assertTrue(data.queueEffect(first, () -> ran.add(1)));
            assertTrue(data.queueEffect(second, () -> ran.add(2)));
            data.drainForTests(); data.pollWriter(1);
            assertTrue(data.logicalAcknowledged(second)); assertFalse(data.logicalAcknowledged(first));
            assertEquals(List.of(), ran); assertEquals(2, data.outstandingEffectCount());
            commit(data); data.pollWriter(2);
            assertEquals(List.of(1, 2), ran); assertTrue(data.clean());
        } finally { data.stop(); }
    }

    @Test void readyAndDiscardedContinuationsShareOneBudgetAcrossReentrantPolls() {
        for (boolean change : List.of(false, true)) {
            var data = new RocketTransferSavedData(); var ran = new AtomicInteger(); var discarded = new AtomicInteger();
            var now = new java.util.concurrent.atomic.AtomicLong(1);
            var logicals = new ArrayList<UUID>();
            try {
                for (int index = 1; index <= 65; index++) {
                    var logical = RocketJournalScheduleFixtures.pendingAbsence(data, index); logicals.add(logical);
                    assertTrue(data.queueEffect(logical, () -> { ran.incrementAndGet(); data.pollWriter(now.get()); return true; },
                            () -> { discarded.incrementAndGet(); data.pollWriter(now.get()); }));
                    if (change) { RocketJournalScheduleFixtures.pendingAbsence(data, index); }
                }
                commit(data); data.pollWriter(1); data.pollWriter(1);
                assertEquals(64, ran.get() + discarded.get()); assertEquals(change ? 64 : 0, discarded.get());
                assertFalse(data.actionable(logicals.get(64))); assertTrue(data.resourceMutationUnproved(logicals.get(64)));
                assertTrue(data.reserve(logicals.get(64)).isEmpty()); assertFalse(data.clean());
                now.set(2); data.pollWriter(2); assertEquals(65, ran.get() + discarded.get()); assertTrue(data.clean());
            } finally { data.stop(); }
        }
    }

    @Test void reservationIsSingleUseOwnedAndReleasedOnThrow() {
        var data = new RocketTransferSavedData(2); var first = RocketJournalScheduleFixtures.record(1);
        data.put(first);
        try {
            commit(data); var original = data.save(new net.minecraft.nbt.CompoundTag());
            assertThrows(IllegalArgumentException.class, () -> {
                try (var token = data.reserve(first.logicalRocketId()).orElseThrow()) {
                    data.put(RocketTransferSavedDataTest.record(UUID.randomUUID(), first.logicalRocketId(), UUID.randomUUID(),
                            new io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketPosition(20_000, 80, 20_000)));
                }
            });
            assertTrue(data.actionable(first.logicalRocketId())); assertEquals(original, data.save(new net.minecraft.nbt.CompoundTag()));
            var token = data.reserve(first.logicalRocketId()).orElseThrow(); var second = data.reserve(UUID.randomUUID()).orElseThrow();
            assertEquals(2, data.outstandingEffectCount()); assertTrue(data.reserve(UUID.randomUUID()).isEmpty()); second.close();
            assertTrue(data.queueEffect(token, false, false, () -> {
                try (var again = data.reserve(first.logicalRocketId()).orElseThrow()) { assertFalse(data.actionable(first.logicalRocketId())); }
                return true;
            }, () -> fail("Immediate effect discarded")));
            var next = data.reserve(first.logicalRocketId()).orElseThrow(); token.close();
            assertFalse(data.actionable(first.logicalRocketId())); assertFalse(data.queueEffect(token, false, false, () -> true, () -> fail()));
            var other = new RocketTransferSavedData();
            assertFalse(other.queueEffect(next, false, false, () -> fail(), () -> fail())); other.stop();
            next.close(); assertTrue(data.clean());
            var stopped = data.reserve(first.logicalRocketId()).orElseThrow(); data.stop();
            assertFalse(data.queueEffect(stopped, false, false, () -> fail(), () -> fail())); stopped.close();
        } finally { data.stop(); }
    }

    @Test void definiteFailureKeepsIndependentProgressButEveryOrdinaryEffectWaits() {
        var data = new RocketTransferSavedData(); var ran = new ArrayList<Integer>(); var logical = UUID.randomUUID();
        try {
            commit(data); data.put(RocketJournalScheduleFixtures.record(1));
            data.saveChecked(file(), new CheckedSavedDataPolicy(ManagedSavedDataType.ROCKET_TRANSFERS,
                    (staged, target) -> { throw new IOException("Injected definite failure"); }));
            assertEquals(RocketTransferSavedData.WriteOutcome.NOT_COMMITTED, data.checkedWriteResult().outcome());
            assertTrue(data.actionable(logical)); assertTrue(data.canQueueEffect(logical).isEmpty());
            assertTrue(data.queueEffect(logical, () -> ran.add(1))); assertEquals(List.of(), ran);
            try (var optIn = data.reserve(UUID.randomUUID()).orElseThrow()) {
                assertTrue(data.queueEffect(optIn, false, true, () -> { ran.add(2); return true; }, () -> fail()));
            }
            data.pollWriter(1); assertEquals(List.of(2), ran);
            commit(data); data.pollWriter(2); assertEquals(List.of(2, 1), ran); assertTrue(data.clean());
        } finally { data.stop(); }
    }

    @Test void unknownRetainsUnchangedEffectsAndRevokesRootAndRecordLeases() {
        var data = new RocketTransferSavedData(); var ran = new AtomicInteger(); var dropped = new AtomicInteger();
        try {
            commit(data); var logical = UUID.randomUUID(); var lease = data.actionableLease(logical); var root = data.acknowledgedLease();
            data.put(RocketJournalScheduleFixtures.record(1));
            assertTrue(data.queueEffect(RocketJournalScheduleFixtures.record(1).logicalRocketId(), () -> { ran.incrementAndGet(); return true; }, dropped::incrementAndGet));
            data.saveChecked(file(), new CheckedSavedDataPolicy(ManagedSavedDataType.ROCKET_TRANSFERS, (staged, target) -> {
                CheckedSavedDataFile.atomicMove(staged, target); throw new IOException("Injected uncertain write");
            }, (target, type) -> { throw new IllegalStateException("Injected unreadable readback"); }));
            data.pollWriter(1); assertFalse(lease.getAsBoolean()); assertFalse(root.getAsBoolean());
            assertFalse(data.actionable(logical)); assertTrue(data.resourceMutationUnproved(logical));
            assertEquals(0, ran.get()); assertEquals(0, dropped.get());
            commit(data); data.pollWriter(2); assertEquals(1, ran.get()); assertEquals(0, dropped.get());
        } finally { data.stop(); }
    }

    @Test void moonPublicationDependsOnTheOutboxEvenWhenItsRocketSlotIsAcknowledged() {
        var data = new RocketTransferSavedData(); var logical = UUID.randomUUID(); var ran = new AtomicInteger();
        try {
            commit(data);
            var event = FirstEvent.MoonLanding.create(UUID.randomUUID(), UUID.randomUUID(), logical, UUID.randomUUID(),
                    UUID.randomUUID(), "1".repeat(64), 0, 80, 0, 0, List.of());
            assertTrue(data.electFirstMoonLanding(event)); assertTrue(data.logicalAcknowledged(logical));
            try (var token = data.canQueueEffect(logical).orElseThrow()) {
                assertTrue(data.queueEffect(token, true, false, () -> { ran.incrementAndGet(); return true; }, () -> fail()));
            }
            data.pollWriter(1); assertEquals(0, ran.get()); assertFalse(data.actionable(logical));
            commit(data); data.pollWriter(2); assertEquals(1, ran.get()); assertTrue(data.actionable(logical));
        } finally { data.stop(); }
    }

    @Test void generatedLegalMutationsStayWithinThe128SlotBound() {
        var data = new RocketTransferSavedData(); var random = new java.util.Random(91);
        try {
            commit(data);
            for (int step = 0; step < 2_048; step++) {
                var record = RocketJournalScheduleFixtures.record(1 + random.nextInt(128));
                if (data.actionable(record.logicalRocketId()) && (data.find(record.transferId()).isPresent() || data.entries().size() < 64)) {
                    try (var token = data.reserve(record.logicalRocketId()).orElseThrow()) {
                        if (data.find(record.transferId()).isPresent()) { data.remove(record.transferId()); }
                        else { data.put(record); }
                        assertTrue(data.queueEffect(token, false, false, () -> true, () -> fail("Generated effect discarded")));
                    }
                }
                assertTrue(data.entries().size() <= 64); assertTrue(data.outstandingEffectCount() <= 128);
                if (step % 64 == 63) { commit(data); data.pollWriter(step); }
            }
            commit(data); data.pollWriter(2_048); data.pollWriter(2_049); assertTrue(data.clean());
        } finally { data.stop(); }
    }

    private static CheckedSavedDataPolicy held(CountDownLatch entered, CountDownLatch release) {
        return new CheckedSavedDataPolicy(ManagedSavedDataType.ROCKET_TRANSFERS, (staged, target) -> {
            entered.countDown();
            try { if (!release.await(10, TimeUnit.SECONDS)) { throw new IOException("Held writer release timed out"); } }
            catch (InterruptedException failure) { Thread.currentThread().interrupt(); throw new IOException(failure); }
            CheckedSavedDataFile.atomicMove(staged, target);
        });
    }
}
