package io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.persistence;

import static org.junit.jupiter.api.Assertions.*;

import io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.CheckedSavedDataFile;
import io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.CheckedSavedDataPolicy;
import io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.ManagedRootPresence;
import io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.ManagedSavedDataType;
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

/** The remaining A0 lifecycle combinations, independent of other test classes' bootstrap or writer state. */
@Timeout(30)
class RocketJournalScheduleMatrixTest {
    private static final ManagedSavedDataType TYPE = ManagedSavedDataType.ROCKET_TRANSFERS;
    @TempDir Path directory;
    @BeforeAll static void bootstrap() { MinecraftBootstrap.initialize(); }
    private Path file() { return directory.resolve(TYPE.fileName()); }
    private void commit(RocketTransferSavedData data) { data.saveChecked(file(), CheckedSavedDataPolicy.atomic(TYPE)); }

    @Test void recordTouchedAbsenceAndUntouchedAbsenceFollowTheAcknowledgementTable() {
        for (boolean baseline : List.of(false, true)) {
            for (boolean unknown : List.of(false, true)) {
                var data = new RocketTransferSavedData();
                var record = RocketJournalScheduleFixtures.record(1);
                var absent = RocketJournalScheduleFixtures.record(2);
                var changed = RocketJournalScheduleFixtures.record(3);
                var untouched = UUID.randomUUID();
                try {
                    data.put(record); data.put(absent); data.remove(absent.transferId());
                    if (baseline) { commit(data); }
                    data.put(changed);
                    assertEquals(baseline, data.actionable(record.logicalRocketId()));
                    assertEquals(baseline, data.actionable(absent.logicalRocketId()));
                    assertEquals(baseline, data.actionable(untouched));
                    assertFalse(data.resourceMutationUnproved(untouched));
                    assertFalse(data.actionable(changed.logicalRocketId()));
                    data.saveChecked(file(), new CheckedSavedDataPolicy(TYPE, (staged, target) -> {
                        if (unknown) { throw new AssertionError("Injected unknown outcome"); }
                        throw new IOException("Injected definite outcome");
                    }));
                    assertEquals(unknown ? RocketTransferSavedData.WriteOutcome.UNKNOWN
                            : RocketTransferSavedData.WriteOutcome.NOT_COMMITTED, data.checkedWriteResult().outcome());
                    for (var logical : List.of(record.logicalRocketId(), absent.logicalRocketId(), untouched)) {
                        assertEquals(baseline && !unknown, data.actionable(logical));
                        assertEquals(!baseline || unknown, data.resourceMutationUnproved(logical));
                        assertTrue(data.canQueueEffect(logical).isEmpty());
                        try (var token = data.reserve(logical).orElseThrow()) {
                            assertFalse(data.actionable(logical)); assertTrue(data.resourceMutationUnproved(logical));
                        }
                    }
                    assertFalse(data.actionable(changed.logicalRocketId()));
                    commit(data);
                    for (var logical : List.of(record.logicalRocketId(), absent.logicalRocketId(), untouched, changed.logicalRocketId())) {
                        assertTrue(data.actionable(logical)); assertFalse(data.resourceMutationUnproved(logical));
                    }
                } finally { data.abort(); }
            }
        }
    }

    @Test void blockedUnreadableStoppedAndAbortedRootsRefuseEverySlotAndReservation() {
        for (int mode = 0; mode < 4; mode++) {
            var data = mode == 0 ? RocketTransferSavedData.load(new CompoundTag())
                    : mode == 1 ? RocketTransferSavedData.created(ManagedRootPresence.UNREADABLE)
                    : new RocketTransferSavedData();
            var record = RocketJournalScheduleFixtures.record(1);
            try {
                if (mode >= 2) { data.put(record); commit(data); if (mode == 2) { data.stop(); } else { data.abort(); } }
                for (var logical : List.of(record.logicalRocketId(), UUID.randomUUID())) {
                    assertFalse(data.actionable(logical)); assertTrue(data.resourceMutationUnproved(logical));
                    assertFalse(data.actionableLease(logical).getAsBoolean());
                    assertTrue(data.reserve(logical).isEmpty()); assertTrue(data.canQueueEffect(logical).isEmpty());
                    assertFalse(data.queueEffect(logical, () -> fail("Unavailable body"), () -> fail("Unaccepted discard")));
                }
                assertFalse(data.acknowledgedLease().getAsBoolean());
            } finally { data.abort(); }
        }
    }

    @Test void becomingUnreadableDiscardsEachPendingEffectOnceAndInvalidatesHeldTokens() throws Exception {
        var data = RocketTransferSavedData.created(ManagedRootPresence.PROVEN_ABSENT);
        var discarded = new AtomicInteger();
        var logical = RocketJournalScheduleFixtures.pendingAbsence(data, 1);
        try (var held = data.reserve(UUID.randomUUID()).orElseThrow()) {
            assertTrue(data.queueEffect(logical, () -> fail("Unreadable callback"), discarded::incrementAndGet));
            Files.writeString(file(), "foreign root appeared before the first replacement");
            data.saveChecked(file(), CheckedSavedDataPolicy.atomic(TYPE));
            assertEquals(ManagedRootPresence.UNREADABLE, data.presence());
            assertEquals(1, discarded.get()); assertEquals(0, data.outstandingEffectCount());
            assertFalse(data.queueEffect(held, false, false, () -> fail(), () -> fail()));
            data.pollWriter(1); data.abort(); data.stop();
            assertEquals(1, discarded.get());
            assertEquals("foreign root appeared before the first replacement", Files.readString(file()));
        } finally { data.abort(); }
    }

    @Test void acknowledgedAbsenceLeaseSurvivesCollectionUntilTheR17ConservativeFloorPassesIt() {
        var data = new RocketTransferSavedData();
        var absent = RocketJournalScheduleFixtures.pendingAbsence(data, 1);
        try {
            commit(data); var lease = data.actionableLease(absent); assertTrue(lease.getAsBoolean());
            for (int i = 2; i < RocketJournalLeases.MAX_HISTORY + 4; i++) {
                RocketJournalScheduleFixtures.pendingAbsence(data, i);
                commit(data);
                // R23 preserves R17's bounded history floor: dropping L itself is harmless, a later floor is conservative.
                assertEquals(i <= RocketJournalLeases.MAX_HISTORY + 1, lease.getAsBoolean());
            }
            lease = data.actionableLease(absent); assertTrue(lease.getAsBoolean());
            RocketJournalScheduleFixtures.pendingAbsence(data, 1);
            assertFalse(lease.getAsBoolean()); commit(data); assertFalse(lease.getAsBoolean());
        } finally { data.abort(); }
    }

    @Test void generatedCommitsFailuresAndStopsPreserveCapacityAndExactlyOnceCompletion() {
        var random = new java.util.Random(91_006);
        for (int session = 0; session < 8; session++) {
            var data = new RocketTransferSavedData(); var accepted = new AtomicInteger();
            var ran = new AtomicInteger(); var discarded = new AtomicInteger();
            try {
                commit(data);
                for (int step = 0; step < 80; step++) {
                    var record = RocketJournalScheduleFixtures.record(1 + random.nextInt(128));
                    if (data.actionable(record.logicalRocketId()) && (data.find(record.transferId()).isPresent() || data.entries().size() < 64)) {
                        try (var token = data.reserve(record.logicalRocketId()).orElseThrow()) {
                            if (data.find(record.transferId()).isPresent()) { data.remove(record.transferId()); }
                            else { data.put(record); }
                            assertTrue(data.queueEffect(token, false, false, () -> { ran.incrementAndGet(); return true; }, discarded::incrementAndGet));
                            accepted.incrementAndGet();
                        }
                    }
                    assertTrue(data.outstandingEffectCount() <= 128);
                    assertEquals(accepted.get(), ran.get() + discarded.get() + data.outstandingEffectCount());
                    if (step % 16 == 15) {
                        int outcome = random.nextInt(3);
                        if (outcome == 0) { commit(data); }
                        else {
                            data.saveChecked(file(), new CheckedSavedDataPolicy(TYPE, (staged, target) -> {
                                if (outcome == 1) { throw new IOException("Generated definite failure"); }
                                throw new AssertionError("Generated unknown outcome");
                            }));
                        }
                        data.pollWriter(step);
                    }
                }
                if (session % 2 == 0) { data.stop(); } else { data.abort(); }
                assertEquals(accepted.get(), ran.get() + discarded.get()); assertEquals(0, data.outstandingEffectCount());
                data.stop(); data.abort(); assertEquals(accepted.get(), ran.get() + discarded.get());
            } finally { data.abort(); }
        }
    }
}
