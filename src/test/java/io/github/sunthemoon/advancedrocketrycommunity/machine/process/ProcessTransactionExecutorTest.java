package io.github.sunthemoon.advancedrocketrycommunity.machine.process;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ProcessTransactionExecutorTest {
    private static final UUID TRANSACTION_ID = UUID.fromString("de6dc9be-ea17-4453-929e-e69b629fbbef");
    private static final UUID OTHER_TRANSACTION_ID = UUID.fromString("3a0a7a65-73f7-4ca1-8daa-3fa8e84855f6");
    private static final UUID MACHINE_ID = UUID.fromString("43cf3a74-68c4-4a67-bfe6-737b8580f9dc");
    private static final ProcessResourceKey INPUT = new ProcessResourceKey(
            ProcessResourceKind.ITEM,
            "input",
            "minecraft:iron_ingot"
    );
    private static final ProcessResourceKey OUTPUT = new ProcessResourceKey(
            ProcessResourceKind.ITEM,
            "output",
            "test:plate"
    );

    @Test
    void commitPersistsEveryPhaseAroundOneAtomicReplacement() {
        Fixture fixture = fixture();

        ProcessTransactionResult result = ProcessTransactionExecutor.commit(
                TRANSACTION_ID,
                MACHINE_ID,
                fixture.plan,
                fixture.resources,
                fixture.journals
        );

        assertEquals(ProcessTransactionStatus.APPLIED, result.status());
        assertEquals(fixture.plan.after(), fixture.resources.snapshot());
        assertEquals(Optional.of(TRANSACTION_ID), fixture.resources.lastAppliedTransactionId());
        assertTrue(fixture.journals.load().isEmpty());
        assertEquals(
                List.of("journal:PREPARED", "journal:APPLYING", "resources", "marker", "journal:APPLIED", "clear"),
                fixture.events
        );
    }

    @Test
    void stalePlanAndActiveOtherJournalNeverMutateResources() {
        Fixture stale = fixture();
        stale.resources.snapshot = snapshot(1, 3, 0);

        ProcessTransactionResult staleResult = ProcessTransactionExecutor.commit(
                TRANSACTION_ID,
                MACHINE_ID,
                stale.plan,
                stale.resources,
                stale.journals
        );

        assertEquals(ProcessTransactionStatus.STALE_TRANSACTION, staleResult.status());
        assertTrue(stale.events.isEmpty());

        Fixture conflict = fixture();
        conflict.journals.journal = ProcessTransactionJournal.prepared(
                OTHER_TRANSACTION_ID,
                MACHINE_ID,
                conflict.plan
        );
        ProcessTransactionResult conflictResult = ProcessTransactionExecutor.commit(
                TRANSACTION_ID,
                MACHINE_ID,
                conflict.plan,
                conflict.resources,
                conflict.journals
        );

        assertEquals(ProcessTransactionStatus.JOURNAL_CONFLICT, conflictResult.status());
        assertEquals(conflict.plan.before(), conflict.resources.snapshot());
    }

    @Test
    void duplicateTransactionAfterClearIsNoOp() {
        Fixture fixture = fixture();
        ProcessTransactionExecutor.commit(
                TRANSACTION_ID,
                MACHINE_ID,
                fixture.plan,
                fixture.resources,
                fixture.journals
        );
        fixture.events.clear();

        ProcessTransactionResult duplicate = ProcessTransactionExecutor.commit(
                TRANSACTION_ID,
                MACHINE_ID,
                fixture.plan,
                fixture.resources,
                fixture.journals
        );

        assertEquals(ProcessTransactionStatus.DUPLICATE, duplicate.status());
        assertEquals(fixture.plan.after(), fixture.resources.snapshot());
        assertTrue(fixture.events.isEmpty());
    }

    @Test
    void preparedOrApplyingBeforeSnapshotRecoversByApplyingOnce() {
        for (ProcessJournalPhase phase : List.of(ProcessJournalPhase.PREPARED, ProcessJournalPhase.APPLYING)) {
            Fixture fixture = fixture();
            fixture.journals.journal = journal(fixture.plan, phase);

            ProcessTransactionResult result = ProcessTransactionExecutor.recover(
                    fixture.resources,
                    fixture.journals
            );

            assertEquals(ProcessTransactionStatus.RECOVERED_APPLY, result.status());
            assertEquals(fixture.plan.after(), fixture.resources.snapshot());
            assertEquals(Optional.of(TRANSACTION_ID), fixture.resources.lastAppliedTransactionId());
            assertTrue(fixture.journals.load().isEmpty());
        }
    }

    @Test
    void applyingAfterSnapshotOnlyFinalizes() {
        Fixture fixture = fixture();
        fixture.resources.snapshot = fixture.plan.after();
        fixture.journals.journal = journal(fixture.plan, ProcessJournalPhase.APPLYING);

        ProcessTransactionResult result = ProcessTransactionExecutor.recover(
                fixture.resources,
                fixture.journals
        );

        assertEquals(ProcessTransactionStatus.RECOVERED_FINALIZE, result.status());
        assertEquals(fixture.plan.after(), fixture.resources.snapshot());
        assertEquals(0, fixture.resources.replacements);
        assertTrue(fixture.journals.load().isEmpty());
    }

    @Test
    void divergentSnapshotBlocksAndPreservesJournalAndResources() {
        Fixture fixture = fixture();
        ProcessResourceSnapshot divergent = snapshot(9, 17, 12);
        fixture.resources.snapshot = divergent;
        ProcessTransactionJournal journal = journal(fixture.plan, ProcessJournalPhase.APPLYING);
        fixture.journals.journal = journal;

        ProcessTransactionResult result = ProcessTransactionExecutor.recover(
                fixture.resources,
                fixture.journals
        );

        assertEquals(ProcessTransactionStatus.RECOVERY_REQUIRED, result.status());
        assertEquals(ProcessFailureCode.RECOVERY_DIVERGED, result.failure().code());
        assertEquals(divergent, fixture.resources.snapshot());
        assertEquals(Optional.of(journal), fixture.journals.load());
    }

    @Test
    void interruptionAfterApplyingJournalCanResume() {
        Fixture fixture = fixture();
        fixture.resources.throwOnReplace = true;

        assertThrows(
                SimulatedCrash.class,
                () -> ProcessTransactionExecutor.commit(
                        TRANSACTION_ID,
                        MACHINE_ID,
                        fixture.plan,
                        fixture.resources,
                        fixture.journals
                )
        );
        assertEquals(ProcessJournalPhase.APPLYING, fixture.journals.load().orElseThrow().phase());
        assertEquals(fixture.plan.before(), fixture.resources.snapshot());

        fixture.resources.throwOnReplace = false;
        ProcessTransactionResult recovered = ProcessTransactionExecutor.recover(
                fixture.resources,
                fixture.journals
        );

        assertEquals(ProcessTransactionStatus.RECOVERED_APPLY, recovered.status());
        assertEquals(fixture.plan.after(), fixture.resources.snapshot());
    }

    @Test
    void recoveryWithoutJournalIsExplicitNoOp() {
        Fixture fixture = fixture();

        ProcessTransactionResult result = ProcessTransactionExecutor.recover(
                fixture.resources,
                fixture.journals
        );

        assertEquals(ProcessTransactionStatus.NO_TRANSACTION, result.status());
        assertEquals(fixture.plan.before(), fixture.resources.snapshot());
    }

    @Test
    void journalRejectsSkippedOrBackwardPhaseTransitions() {
        Fixture fixture = fixture();
        ProcessTransactionJournal prepared = journal(fixture.plan, ProcessJournalPhase.PREPARED);
        ProcessTransactionJournal applying = prepared.advance(ProcessJournalPhase.APPLYING);

        assertThrows(IllegalArgumentException.class, () -> prepared.advance(ProcessJournalPhase.APPLIED));
        assertThrows(IllegalArgumentException.class, () -> applying.advance(ProcessJournalPhase.PREPARED));
        assertEquals(applying, applying.advance(ProcessJournalPhase.APPLYING));
    }

    private static ProcessTransactionJournal journal(ProcessPlan plan, ProcessJournalPhase phase) {
        ProcessTransactionJournal journal = ProcessTransactionJournal.prepared(TRANSACTION_ID, MACHINE_ID, plan);
        if (phase != ProcessJournalPhase.PREPARED) {
            journal = journal.advance(ProcessJournalPhase.APPLYING);
        }
        if (phase == ProcessJournalPhase.APPLIED) {
            journal = journal.advance(ProcessJournalPhase.APPLIED);
        }
        return journal;
    }

    private static Fixture fixture() {
        ProcessResourceSnapshot before = snapshot(0, 2, 0);
        ProcessResourceSnapshot after = snapshot(1, 0, 1);
        ProcessPlan plan = new ProcessPlan(
                "test:rolling",
                before.revision(),
                before.fingerprint(),
                before,
                after
        );
        List<String> events = new ArrayList<>();
        return new Fixture(plan, new FakeResourceStore(before, events), new FakeJournalStore(events), events);
    }

    private static ProcessResourceSnapshot snapshot(long revision, long input, long output) {
        return new ProcessResourceSnapshot(revision, Map.of(
                INPUT, new ProcessResourceBalance(input, 64),
                OUTPUT, new ProcessResourceBalance(output, 64)
        ));
    }

    private record Fixture(
            ProcessPlan plan,
            FakeResourceStore resources,
            FakeJournalStore journals,
            List<String> events
    ) {
    }

    private static final class FakeResourceStore implements ProcessResourceStore {
        private ProcessResourceSnapshot snapshot;
        private Optional<UUID> lastApplied = Optional.empty();
        private final List<String> events;
        private boolean throwOnReplace;
        private int replacements;

        private FakeResourceStore(ProcessResourceSnapshot snapshot, List<String> events) {
            this.snapshot = snapshot;
            this.events = events;
        }

        @Override
        public ProcessResourceSnapshot snapshot() {
            return snapshot;
        }

        @Override
        public boolean replaceIfMatches(ProcessResourceSnapshot expected, ProcessResourceSnapshot replacement) {
            if (throwOnReplace) {
                throw new SimulatedCrash();
            }
            if (!snapshot.equals(expected)) {
                return false;
            }
            snapshot = replacement;
            replacements++;
            events.add("resources");
            return true;
        }

        @Override
        public Optional<UUID> lastAppliedTransactionId() {
            return lastApplied;
        }

        @Override
        public void markApplied(UUID transactionId) {
            lastApplied = Optional.of(transactionId);
            events.add("marker");
        }
    }

    private static final class FakeJournalStore implements ProcessJournalStore {
        private ProcessTransactionJournal journal;
        private final List<String> events;

        private FakeJournalStore(List<String> events) {
            this.events = events;
        }

        @Override
        public Optional<ProcessTransactionJournal> load() {
            return Optional.ofNullable(journal);
        }

        @Override
        public void save(ProcessTransactionJournal journal) {
            this.journal = journal;
            events.add("journal:" + journal.phase());
        }

        @Override
        public void clear(UUID transactionId) {
            if (journal == null || !journal.transactionId().equals(transactionId)) {
                throw new IllegalStateException("attempted to clear a different transaction");
            }
            journal = null;
            events.add("clear");
        }
    }

    private static final class SimulatedCrash extends RuntimeException {
    }
}
