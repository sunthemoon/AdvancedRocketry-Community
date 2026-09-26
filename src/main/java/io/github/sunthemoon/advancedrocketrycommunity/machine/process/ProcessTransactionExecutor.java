package io.github.sunthemoon.advancedrocketrycommunity.machine.process;

import java.util.Optional;
import java.util.UUID;

/** Orders journal state transitions and performs idempotent final resource replacement. */
public final class ProcessTransactionExecutor {
    private ProcessTransactionExecutor() {
    }

    public static ProcessTransactionResult commit(
            UUID transactionId,
            UUID machineId,
            ProcessPlan plan,
            ProcessResourceStore resources,
            ProcessJournalStore journals
    ) {
        if (resources.lastAppliedTransactionId().filter(transactionId::equals).isPresent()) {
            return resources.snapshot().equals(plan.after())
                    ? ProcessTransactionResult.success(ProcessTransactionStatus.DUPLICATE)
                    : ProcessTransactionResult.failure(
                            ProcessTransactionStatus.RECOVERY_REQUIRED,
                            ProcessFailureCode.RECOVERY_DIVERGED,
                            plan.definitionId()
                    );
        }
        Optional<ProcessTransactionJournal> active = journals.load();
        if (active.isPresent()) {
            if (active.orElseThrow().transactionId().equals(transactionId)) {
                return recover(resources, journals);
            }
            return ProcessTransactionResult.failure(
                    ProcessTransactionStatus.JOURNAL_CONFLICT,
                    ProcessFailureCode.JOURNAL_CONFLICT,
                    active.orElseThrow().transactionId().toString()
            );
        }
        if (!matches(resources.snapshot(), plan.before(), plan.expectedFingerprint())) {
            return ProcessTransactionResult.failure(
                    ProcessTransactionStatus.STALE_TRANSACTION,
                    ProcessFailureCode.STALE_TRANSACTION,
                    plan.definitionId()
            );
        }

        ProcessTransactionJournal journal = ProcessTransactionJournal.prepared(transactionId, machineId, plan);
        journals.save(journal);
        journal = journal.advance(ProcessJournalPhase.APPLYING);
        journals.save(journal);
        if (!resources.replaceIfMatches(journal.before(), journal.after())) {
            return resolveFailedReplacement(journal, resources, journals, ProcessTransactionStatus.APPLIED);
        }
        resources.markApplied(transactionId);
        persistAppliedAndClear(journal, journals);
        return ProcessTransactionResult.success(ProcessTransactionStatus.APPLIED);
    }

    public static ProcessTransactionResult recover(
            ProcessResourceStore resources,
            ProcessJournalStore journals
    ) {
        Optional<ProcessTransactionJournal> loaded = journals.load();
        if (loaded.isEmpty()) {
            return ProcessTransactionResult.success(ProcessTransactionStatus.NO_TRANSACTION);
        }
        ProcessTransactionJournal journal = loaded.orElseThrow();
        if (resources.lastAppliedTransactionId().filter(journal.transactionId()::equals).isPresent()) {
            if (!resources.snapshot().equals(journal.after())) {
                return ProcessTransactionResult.failure(
                        ProcessTransactionStatus.RECOVERY_REQUIRED,
                        ProcessFailureCode.RECOVERY_DIVERGED,
                        journal.definitionId()
                );
            }
            persistAppliedAndClear(journal, journals);
            return ProcessTransactionResult.success(ProcessTransactionStatus.RECOVERED_FINALIZE);
        }

        ProcessResourceSnapshot actual = resources.snapshot();
        if (actual.equals(journal.after())) {
            resources.markApplied(journal.transactionId());
            persistAppliedAndClear(journal, journals);
            return ProcessTransactionResult.success(ProcessTransactionStatus.RECOVERED_FINALIZE);
        }
        if (!matches(actual, journal.before(), journal.beforeFingerprint())) {
            return ProcessTransactionResult.failure(
                    ProcessTransactionStatus.RECOVERY_REQUIRED,
                    ProcessFailureCode.RECOVERY_DIVERGED,
                    journal.definitionId()
            );
        }
        if (journal.phase() == ProcessJournalPhase.APPLIED) {
            return ProcessTransactionResult.failure(
                    ProcessTransactionStatus.RECOVERY_REQUIRED,
                    ProcessFailureCode.RECOVERY_DIVERGED,
                    journal.definitionId()
            );
        }
        if (journal.phase() == ProcessJournalPhase.PREPARED) {
            journal = journal.advance(ProcessJournalPhase.APPLYING);
            journals.save(journal);
        }
        if (!resources.replaceIfMatches(journal.before(), journal.after())) {
            return resolveFailedReplacement(
                    journal,
                    resources,
                    journals,
                    ProcessTransactionStatus.RECOVERED_APPLY
            );
        }
        resources.markApplied(journal.transactionId());
        persistAppliedAndClear(journal, journals);
        return ProcessTransactionResult.success(ProcessTransactionStatus.RECOVERED_APPLY);
    }

    private static ProcessTransactionResult resolveFailedReplacement(
            ProcessTransactionJournal journal,
            ProcessResourceStore resources,
            ProcessJournalStore journals,
            ProcessTransactionStatus successStatus
    ) {
        if (resources.snapshot().equals(journal.after())) {
            resources.markApplied(journal.transactionId());
            persistAppliedAndClear(journal, journals);
            return ProcessTransactionResult.success(successStatus);
        }
        return ProcessTransactionResult.failure(
                ProcessTransactionStatus.RECOVERY_REQUIRED,
                ProcessFailureCode.RECOVERY_DIVERGED,
                journal.definitionId()
        );
    }

    private static boolean matches(
            ProcessResourceSnapshot actual,
            ProcessResourceSnapshot expected,
            String fingerprint
    ) {
        return actual.revision() == expected.revision()
                && actual.fingerprint().equals(fingerprint)
                && actual.equals(expected);
    }

    private static void persistAppliedAndClear(
            ProcessTransactionJournal journal,
            ProcessJournalStore journals
    ) {
        ProcessTransactionJournal applying = journal.phase() == ProcessJournalPhase.PREPARED
                ? journal.advance(ProcessJournalPhase.APPLYING)
                : journal;
        if (applying != journal) {
            journals.save(applying);
        }
        ProcessTransactionJournal applied = applying.phase() == ProcessJournalPhase.APPLIED
                ? applying
                : applying.advance(ProcessJournalPhase.APPLIED);
        journals.save(applied);
        journals.clear(applied.transactionId());
    }
}
