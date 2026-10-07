package io.github.sunthemoon.advancedrocketrycommunity.machine.classic.adapter;

import static org.junit.jupiter.api.Assertions.*;
import io.github.sunthemoon.advancedrocketrycommunity.machine.classic.resource.ClassicBankKey;
import io.github.sunthemoon.advancedrocketrycommunity.machine.classic.resource.ClassicBankKind;
import io.github.sunthemoon.advancedrocketrycommunity.machine.multiblock.lifecycle.MultiblockFormationState;
import io.github.sunthemoon.advancedrocketrycommunity.machine.multiblock.pattern.PatternRotation;
import io.github.sunthemoon.advancedrocketrycommunity.machine.process.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

/** Necessary data predicates only. These tests never construct an installed owner or ticket. */
class ClassicOwnerAdmissionPolicyTest {
    private static final UUID MACHINE = new UUID(1, 2);
    private static final ResourceLocation RECIPE = new ResourceLocation("test:recipe");

    @Test void idleAndOrdinaryWorkWithoutPendingDataRemainEligibleForFurtherLiveChecks() {
        assertTrue(ClassicCheckpointChecks.ordinaryCapabilityAllowed(machine(false, Optional.empty()), Optional.empty()));
        var idle = new ClassicMachineState(new ResourceLocation("advancedrocketrycommunity:lathe"), MACHINE,
                new ResourceLocation("minecraft:overworld"), BlockPos.ZERO, 1, PatternRotation.ZERO,
                MultiblockFormationState.FORMED, assignments(), 0, new ClassicRefusal("none", ""),
                new ClassicProcessFrame.Idle(), Optional.empty(), Optional.empty(), Optional.empty());
        assertTrue(ClassicCheckpointChecks.ordinaryCapabilityAllowed(idle, Optional.empty()));
        // Eligibility here still does not prove thread, FULL/owner, formation admission or availability.
    }

    @Test void nativePlanAloneCannotEnterOrdinaryCapabilityEvenBeforeJournalValidation() {
        var retained = machine(true, Optional.empty());
        assertFalse(ClassicCheckpointChecks.ordinaryCapabilityAllowed(retained, Optional.empty()));
        assertTrue(retained.nativePlan().isPresent());
    }

    @Test void everyRetainedJournalPhaseRefusesOrdinaryCapabilityWithoutChangingRecordedData() {
        var retained = machine(true, Optional.empty());
        var plan = retained.nativePlan().orElseThrow();
        var before = ClassicCheckpointChecks.quantitative(plan, true);
        var after = ClassicCheckpointChecks.quantitative(plan, false);
        for (ProcessJournalPhase phase : ProcessJournalPhase.values()) {
            var journal = new ProcessTransactionJournal(1, plan.transactionId(), MACHINE, RECIPE.toString(),
                    before.revision(), before.fingerprint(), before, after, phase);
            assertFalse(ClassicCheckpointChecks.ordinaryCapabilityAllowed(retained, Optional.of(journal)));
            assertFalse(ClassicCheckpointChecks.ordinaryCapabilityAllowed(machine(false, Optional.empty()), Optional.of(journal)));
            assertSame(before, journal.before()); assertSame(after, journal.after()); assertEquals(phase, journal.phase());
        }
        // A private recovery suffix needs the separate complete native/cut proof, not this predicate.
    }

    @Test void supportedQuarantineCannotEnterOrdinaryCapabilityAndIsNotErased() {
        var uncertain = new ClassicUncertainDrop(assignments().get(0).bankKey().orElseThrow(), 0, new UUID(7, 8));
        var quarantine = new ClassicDropQuarantine(new UUID(5, 6), true, List.of(uncertain));
        var retained = machine(false, Optional.of(quarantine));
        assertFalse(ClassicCheckpointChecks.ordinaryCapabilityAllowed(retained, Optional.empty()));
        assertSame(quarantine, retained.dropQuarantine().orElseThrow());
        assertEquals("drop_uncertain", retained.refusal().code());
    }

    @Test void completeAssignmentComparisonRequiresEveryExactRowAndRejectsMissingDuplicateOrForeignRows() {
        var expected = assignments();
        assertTrue(ClassicCheckpointChecks.completeAssignments(expected, expected));
        assertTrue(ClassicCheckpointChecks.completeAssignments(expected, List.of(expected.get(1), expected.get(0))));
        assertFalse(ClassicCheckpointChecks.completeAssignments(expected, List.of(expected.get(0))));
        assertFalse(ClassicCheckpointChecks.completeAssignments(expected, List.of(expected.get(0), expected.get(0))));
        var foreign = new ClassicAssignment(new BlockPos(0, 0, 2), ClassicHatchKind.POWER_INPUT, Optional.empty());
        assertFalse(ClassicCheckpointChecks.completeAssignments(expected, List.of(expected.get(0), foreign)));
        var wrongKind = new ClassicAssignment(BlockPos.ZERO, ClassicHatchKind.ITEM_OUTPUT,
                Optional.of(new ClassicBankKey(ClassicBankKind.ITEM_OUTPUT, 0, 0, 0)));
        assertFalse(ClassicCheckpointChecks.completeAssignments(expected, List.of(wrongKind, expected.get(1))));
        assertFalse(ClassicCheckpointChecks.completeAssignments(List.of(expected.get(0), expected.get(0)),
                List.of(expected.get(0), expected.get(0))));
        // Matching rows do not prove native identity, binding generation, lifetime or availability.
    }

    @Test void fullSetComparisonRetainsThe64OwnerCeilingWithoutTruncation() {
        var rows = new ArrayList<ClassicAssignment>();
        for (int x = 0; x < 64; x++) {
            rows.add(new ClassicAssignment(new BlockPos(x, 0, 0), ClassicHatchKind.POWER_INPUT, Optional.empty()));
        }
        assertTrue(ClassicCheckpointChecks.completeAssignments(rows, rows));
        rows.add(new ClassicAssignment(new BlockPos(64, 0, 0), ClassicHatchKind.POWER_INPUT, Optional.empty()));
        assertFalse(ClassicCheckpointChecks.completeAssignments(rows, rows)); assertEquals(65, rows.size());
    }

    private static List<ClassicAssignment> assignments() {
        return List.of(new ClassicAssignment(BlockPos.ZERO, ClassicHatchKind.ITEM_INPUT,
                        Optional.of(new ClassicBankKey(ClassicBankKind.ITEM_INPUT, 0, 0, 0))),
                new ClassicAssignment(new BlockPos(0, 0, 1), ClassicHatchKind.POWER_INPUT, Optional.empty()));
    }

    private static ClassicMachineState machine(boolean prepared, Optional<ClassicDropQuarantine> quarantine) {
        var work = new ClassicProcessFrame.Work(RECIPE, "0".repeat(64), 1, 1, 1, 1, 1, ProcessMachineState.RUNNING);
        return new ClassicMachineState(new ResourceLocation("advancedrocketrycommunity:lathe"), MACHINE,
                new ResourceLocation("minecraft:overworld"), BlockPos.ZERO, 1, PatternRotation.ZERO,
                MultiblockFormationState.FORMED, assignments(), 1,
                new ClassicRefusal(quarantine.isPresent() ? "drop_uncertain" : "none", ""), work,
                prepared ? Optional.of(plan()) : Optional.empty(), Optional.empty(), quarantine);
    }

    private static ClassicNativePlan plan() {
        var key = assignments().get(0).bankKey().orElseThrow();
        CompoundTag before = new CompoundTag(); before.putString("id", "minecraft:stone"); before.putByte("Count", (byte) 1);
        var entry = new ClassicNativeEntry(key, OptionalInt.of(0), "minecraft:stone", 64,
                OwnedNativeTag.captureData(before, ClassicBankKind.ITEM_INPUT),
                OwnedNativeTag.captureData(new CompoundTag(), ClassicBankKind.ITEM_INPUT));
        var allocation = new ClassicAllocation(true, ProcessResourceKind.ITEM, 0, "minecraft:stone", 1,
                List.of(new ClassicAllocationPart(0, 1)));
        return new ClassicNativePlan(new UUID(3, 4), 1, 1, RECIPE, "0".repeat(64), 0, 1,
                "1".repeat(64), "2".repeat(64), List.of(entry), List.of(allocation));
    }
}
