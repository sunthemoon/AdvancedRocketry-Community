package io.github.sunthemoon.advancedrocketrycommunity.machine.classic.adapter;

import io.github.sunthemoon.advancedrocketrycommunity.machine.classic.resource.ClassicResources;
import io.github.sunthemoon.advancedrocketrycommunity.machine.process.ProcessTransactionJournal;
import java.util.Objects;
import java.util.Optional;

/** Read-only value, not a publishable checkpoint. Guarded native/cut verification is deferred. */
public record ClassicControllerFrame(ClassicMachineState machine, ClassicResources resources,
                                     Optional<ProcessTransactionJournal> journal,
                                     ClassicSignatureMarker signatureMarker) {
    public ClassicControllerFrame {
        Objects.requireNonNull(machine, "machine");
        Objects.requireNonNull(resources, "resources");
        Objects.requireNonNull(journal, "journal");
        Objects.requireNonNull(signatureMarker, "signatureMarker");
        ClassicValueChecks.require(machine.machineId().equals(resources.machineId()), "Resource owner differs");
        ClassicValueChecks.require(machine.nativePlan().isPresent() == journal.isPresent(), "Orphan plan/journal");
        for (ClassicAssignment assignment : machine.assignments()) {
            assignment.bankKey().ifPresent(key -> ClassicValueChecks.require(resources.bank(key).isPresent(),
                    "Assigned bank is not retained"));
        }
        if (machine.process() instanceof ClassicProcessFrame.Work work) {
            ClassicValueChecks.require(signatureMarker.recipeId().filter(work.recipeId()::equals).isPresent(),
                    "Work marker differs");
        } else { ClassicValueChecks.require(signatureMarker.recipeId().isEmpty(), "Idle marker names a recipe"); }
        if (journal.isPresent()) {
            ProcessTransactionJournal log = journal.orElseThrow();
            ClassicNativePlan plan = machine.nativePlan().orElseThrow();
            ClassicValueChecks.require(log.machineId().equals(machine.machineId())
                    && log.transactionId().equals(plan.transactionId())
                    && log.definitionId().equals(plan.recipeId().toString())
                    && log.before().revision() == plan.beforeRevision()
                    && log.after().revision() == plan.afterRevision(), "Journal identity differs from plan");
        }
    }
}
