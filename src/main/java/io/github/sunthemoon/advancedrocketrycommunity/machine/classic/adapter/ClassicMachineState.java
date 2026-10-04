package io.github.sunthemoon.advancedrocketrycommunity.machine.classic.adapter;

import io.github.sunthemoon.advancedrocketrycommunity.machine.multiblock.lifecycle.MultiblockFormationState;
import io.github.sunthemoon.advancedrocketrycommunity.machine.multiblock.pattern.PatternRotation;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;

public record ClassicMachineState(ResourceLocation machineKind, UUID machineId, ResourceLocation ownerLevel,
        BlockPos ownerPosition, long generation, PatternRotation rotation, MultiblockFormationState formationState,
        List<ClassicAssignment> assignments, long batchOrdinal, ClassicRefusal refusal, ClassicProcessFrame process,
        Optional<ClassicNativePlan> nativePlan, Optional<UUID> lastAppliedTransaction,
        Optional<ClassicDropQuarantine> dropQuarantine) {
    public ClassicMachineState {
        ClassicValueChecks.id(machineKind);
        ClassicValueChecks.id(ownerLevel);
        Objects.requireNonNull(machineId, "machineId");
        ownerPosition = Objects.requireNonNull(ownerPosition, "ownerPosition").immutable();
        Objects.requireNonNull(rotation, "rotation");
        Objects.requireNonNull(formationState, "formationState");
        Objects.requireNonNull(assignments, "assignments");
        Objects.requireNonNull(refusal, "refusal");
        Objects.requireNonNull(process, "process");
        Objects.requireNonNull(nativePlan, "nativePlan");
        Objects.requireNonNull(lastAppliedTransaction, "lastAppliedTransaction");
        Objects.requireNonNull(dropQuarantine, "dropQuarantine");
        ClassicValueChecks.require(generation >= 0 && batchOrdinal >= 0 && assignments.size() <= 64,
                "Machine counter/assignment limits");
        ClassicValueChecks.require(formationState != MultiblockFormationState.UNSUPPORTED_DATA,
                "Rejected raw data is not a supported machine");
        ClassicValueChecks.require(formationState != MultiblockFormationState.FORMED || generation >= 1,
                "Formed generation must be positive");
        boolean retains = formationState == MultiblockFormationState.FORMED
                || formationState == MultiblockFormationState.WAITING_UNLOADED
                || formationState == MultiblockFormationState.BINDING_CONFLICT;
        ClassicValueChecks.require(retains || assignments.isEmpty(), "Formation cannot retain assignments");
        var positions = new HashSet<BlockPos>();
        var banks = new HashSet<Object>();
        ClassicAssignment previous = null;
        for (ClassicAssignment assignment : assignments) {
            ClassicValueChecks.require(positions.add(assignment.position())
                    && (previous == null || ClassicAssignment.ORDER.compare(previous, assignment) < 0),
                    "Assignment duplicate/order");
            assignment.bankKey().ifPresent(key -> ClassicValueChecks.require(banks.add(key), "Duplicate assignment bank"));
            previous = assignment;
        }
        if (process instanceof ClassicProcessFrame.Work work) {
            ClassicValueChecks.require(work.ordinal() == batchOrdinal, "Work ordinal differs");
        } else { ClassicValueChecks.require(nativePlan.isEmpty(), "Idle machine has a native plan"); }
        if (nativePlan.isPresent()) {
            ClassicNativePlan plan = nativePlan.orElseThrow();
            ClassicProcessFrame.Work work = (ClassicProcessFrame.Work) process;
            ClassicValueChecks.require(plan.ownerGeneration() == generation && plan.ordinal() == batchOrdinal
                    && plan.recipeId().equals(work.recipeId()) && plan.jsonSignature().equals(work.jsonSignature())
                    && work.progressTicks() == work.durationTicks(), "Plan differs from completed work");
            for (ClassicNativeEntry entry : plan.entries()) {
                ClassicValueChecks.require(banks.contains(entry.bankKey()), "Plan bank not actively assigned");
            }
        }
        if (dropQuarantine.isPresent()) {
            ClassicValueChecks.require(nativePlan.isEmpty() && refusal.code().equals("drop_uncertain"),
                    "Drop quarantine requires refusal and no pending plan");
        }
        assignments = List.copyOf(assignments);
    }
}
