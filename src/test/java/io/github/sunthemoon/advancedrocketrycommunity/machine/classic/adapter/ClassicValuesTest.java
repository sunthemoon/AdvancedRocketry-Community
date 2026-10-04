package io.github.sunthemoon.advancedrocketrycommunity.machine.classic.adapter;

import static org.junit.jupiter.api.Assertions.*;
import io.github.sunthemoon.advancedrocketrycommunity.machine.classic.resource.*;
import io.github.sunthemoon.advancedrocketrycommunity.machine.multiblock.lifecycle.*;
import io.github.sunthemoon.advancedrocketrycommunity.machine.multiblock.pattern.PatternRotation;
import io.github.sunthemoon.advancedrocketrycommunity.machine.process.ProcessMachineState;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeAll;

class ClassicValuesTest {
    static final UUID OWNER = new UUID(1, 2);
    static final ResourceLocation LATHE = new ResourceLocation("advancedrocketrycommunity:lathe");
    static final ResourceLocation LEVEL = new ResourceLocation("minecraft:overworld");
    static final ResourceLocation RECIPE = new ResourceLocation("advancedrocketrycommunity:lathe_iron_rod");
    static final ClassicRefusal NONE = new ClassicRefusal("none", "");
    static final String HASH = "0".repeat(64);
    @BeforeAll static void bootstrap() { MinecraftBootstrap.initialize(); }

    static ClassicMachineState machine(List<ClassicAssignment> assignments, long generation,
            MultiblockFormationState formation, long ordinal, ClassicProcessFrame process) {
        return new ClassicMachineState(LATHE, OWNER, LEVEL, BlockPos.ZERO, generation,
                PatternRotation.ZERO, formation, assignments, ordinal, NONE, process,
                Optional.empty(), Optional.empty(), Optional.empty());
    }

    static MultiblockPartBinding binding() {
        return new MultiblockPartBinding(1, ResourceKey.create(Registries.DIMENSION, LEVEL),
                BlockPos.ZERO, OWNER, 1);
    }

    static ClassicAssignment item(int x, int y, int z) {
        return new ClassicAssignment(new BlockPos(x, y, z), ClassicHatchKind.ITEM_INPUT,
                Optional.of(new ClassicBankKey(ClassicBankKind.ITEM_INPUT, x, y, z)));
    }

    @Test void fiveIdsAndRolesAreExactAndPowerHasNoBank() {
        String[] ids = {"item_input_hatch", "item_output_hatch", "fluid_input_hatch", "fluid_output_hatch", "power_input_plug"};
        String[] roles = {"item_input", "item_output", "fluid_input", "fluid_output", "energy_input"};
        for (int i = 0; i < ids.length; i++) {
            ClassicHatchKind kind = ClassicHatchKind.values()[i];
            assertEquals("advancedrocketrycommunity:" + ids[i], kind.blockId().toString());
            assertEquals(roles[i], kind.patternRole());
            assertEquals(Optional.of(kind), ClassicHatchKind.fromBlockId(kind.blockId()));
            assertEquals(i != 4, kind.bankKind().isPresent());
        }
        assertTrue(ClassicHatchKind.fromBlockId(new ResourceLocation("minecraft:stone")).isEmpty());
    }

    @Test void assignmentOwnsMutablePositionAndChecksKindCoordinates() {
        BlockPos.MutableBlockPos original = new BlockPos.MutableBlockPos(Integer.MIN_VALUE, 3, Integer.MAX_VALUE);
        var key = new ClassicBankKey(ClassicBankKind.ITEM_INPUT, Integer.MIN_VALUE, 3, Integer.MAX_VALUE);
        var assignment = new ClassicAssignment(original, ClassicHatchKind.ITEM_INPUT, Optional.of(key));
        original.set(0, 0, 0);
        assertEquals(Integer.MIN_VALUE, assignment.position().getX());
        assertFalse(assignment.position() instanceof BlockPos.MutableBlockPos);
        assertThrows(IllegalArgumentException.class, () -> new ClassicAssignment(BlockPos.ZERO,
                ClassicHatchKind.ITEM_INPUT, Optional.of(key)));
        assertThrows(IllegalArgumentException.class, () -> new ClassicAssignment(BlockPos.ZERO,
                ClassicHatchKind.POWER_INPUT, Optional.of(key)));
        assertThrows(IllegalArgumentException.class, () -> new ClassicAssignment(BlockPos.ZERO,
                ClassicHatchKind.FLUID_INPUT, Optional.empty()));
    }

    @Test void hatchOptionalFieldsFollowFiveExactRoles() {
        for (ClassicHatchKind kind : ClassicHatchKind.values()) {
            boolean power = kind == ClassicHatchKind.POWER_INPUT;
            var energy = power ? OptionalInt.of(0) : OptionalInt.empty();
            assertDoesNotThrow(() -> new ClassicHatchState(kind, Optional.empty(), Optional.empty(), energy));
            var key = kind.bankKind().map(k -> new ClassicBankKey(k, -1, 64, 2));
            assertDoesNotThrow(() -> new ClassicHatchState(kind, Optional.of(binding()), key, energy));
            assertThrows(IllegalArgumentException.class, () -> new ClassicHatchState(kind,
                    Optional.empty(), key, power ? OptionalInt.empty() : OptionalInt.of(0)));
        }
    }

    @Test void powerValuesKeepInclusiveFeBoundsWithoutRemovalPolicy() {
        for (int energy : new int[]{0, 1, 10_000}) {
            assertEquals(energy, new ClassicHatchState(ClassicHatchKind.POWER_INPUT,
                    Optional.empty(), Optional.empty(), OptionalInt.of(energy)).energy().getAsInt());
        }
        for (int energy : new int[]{-1, 10_001, Integer.MAX_VALUE}) {
            assertThrows(IllegalArgumentException.class, () -> new ClassicHatchState(ClassicHatchKind.POWER_INPUT,
                    Optional.empty(), Optional.empty(), OptionalInt.of(energy)));
        }
    }

    @Test void boundItemAndFluidRequireTheirOwnBankKind() {
        assertThrows(IllegalArgumentException.class, () -> new ClassicHatchState(ClassicHatchKind.ITEM_INPUT,
                Optional.of(binding()), Optional.empty(), OptionalInt.empty()));
        assertThrows(IllegalArgumentException.class, () -> new ClassicHatchState(ClassicHatchKind.FLUID_INPUT,
                Optional.of(binding()), Optional.of(new ClassicBankKey(ClassicBankKind.FLUID_OUTPUT, 0, 0, 0)),
                OptionalInt.empty()));
    }

    @Test void refusalVocabularyAndSubjectLimitsAreExact() {
        assertEquals(25, ClassicRefusal.CODES.size());
        for (String code : ClassicRefusal.CODES) { assertDoesNotThrow(() -> new ClassicRefusal(code, "")); }
        assertDoesNotThrow(() -> new ClassicRefusal("busy", "x".repeat(128)));
        assertThrows(IllegalArgumentException.class, () -> new ClassicRefusal("none", "x"));
        assertThrows(IllegalArgumentException.class, () -> new ClassicRefusal("future_code", ""));
        assertThrows(IllegalArgumentException.class, () -> new ClassicRefusal("busy", "x".repeat(129)));
    }

    @Test void workCountersAndConsumptionAreCheckedWithoutNarrowing() {
        var work = new ClassicProcessFrame.Work(RECIPE, HASH, 72_000, 10_000, 72_000,
                720_000_000L, Long.MAX_VALUE, ProcessMachineState.RECOVERY_REQUIRED);
        assertEquals(720_000_000L, work.consumedEnergy());
        assertThrows(IllegalArgumentException.class, () -> new ClassicProcessFrame.Work(RECIPE, HASH,
                300, 20, 299, 6_000, 1, ProcessMachineState.RUNNING));
        assertThrows(IllegalArgumentException.class, () -> new ClassicProcessFrame.Work(RECIPE, HASH,
                72_001, 20, 0, 0, 1, ProcessMachineState.RUNNING));
        assertThrows(IllegalArgumentException.class, () -> new ClassicProcessFrame.Work(RECIPE, HASH,
                300, 10_001, 0, 0, 1, ProcessMachineState.RUNNING));
        assertThrows(IllegalArgumentException.class, () -> new ClassicProcessFrame.Work(RECIPE, HASH,
                300, 20, 301, 6_020, 1, ProcessMachineState.RUNNING));
        assertThrows(IllegalArgumentException.class, () -> new ClassicProcessFrame.Work(RECIPE, HASH,
                300, 20, 0, 0, 0, ProcessMachineState.RUNNING));
    }

    @Test void workRejectsIdleUnsupportedAndNoncanonicalHash() {
        for (ProcessMachineState state : new ProcessMachineState[]{ProcessMachineState.IDLE, ProcessMachineState.UNSUPPORTED_DATA}) {
            assertThrows(IllegalArgumentException.class, () -> new ClassicProcessFrame.Work(RECIPE, HASH,
                    300, 20, 0, 0, 1, state));
        }
        for (String hash : new String[]{"A".repeat(64), "0".repeat(63), ""}) {
            assertThrows(IllegalArgumentException.class, () -> new ClassicProcessFrame.Work(RECIPE, hash,
                    300, 20, 0, 0, 1, ProcessMachineState.RUNNING));
        }
    }

    @Test void machineOwnsListAndPositionAndRejectsDuplicateOrUnsortedAssignments() {
        List<ClassicAssignment> source = new ArrayList<>(List.of(item(0, 0, 0), item(1, 0, 0)));
        var value = machine(source, 1, MultiblockFormationState.FORMED, 0, new ClassicProcessFrame.Idle());
        source.clear();
        assertEquals(2, value.assignments().size());
        assertThrows(UnsupportedOperationException.class, () -> value.assignments().clear());
        assertThrows(IllegalArgumentException.class, () -> machine(List.of(item(0, 0, 0), item(0, 0, 0)),
                1, MultiblockFormationState.FORMED, 0, new ClassicProcessFrame.Idle()));
        assertThrows(IllegalArgumentException.class, () -> machine(List.of(item(1, 0, 0), item(0, 0, 0)),
                1, MultiblockFormationState.FORMED, 0, new ClassicProcessFrame.Idle()));
    }

    @Test void assignmentCountAndRetainedFormationStatesRemainBounded() {
        List<ClassicAssignment> assignments = new ArrayList<>();
        for (int i = 0; i < 64; i++) { assignments.add(item(i, 0, 0)); }
        for (var state : List.of(MultiblockFormationState.FORMED, MultiblockFormationState.WAITING_UNLOADED,
                MultiblockFormationState.BINDING_CONFLICT)) {
            assertDoesNotThrow(() -> machine(assignments, 1, state, 0, new ClassicProcessFrame.Idle()));
        }
        assignments.add(item(64, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> machine(assignments, 1,
                MultiblockFormationState.FORMED, 0, new ClassicProcessFrame.Idle()));
        assertThrows(IllegalArgumentException.class, () -> machine(List.of(item(0, 0, 0)), 0,
                MultiblockFormationState.UNFORMED, 0, new ClassicProcessFrame.Idle()));
    }

    @Test void formationAndWorkOrdinalDoNotSilentlyReset() {
        assertDoesNotThrow(() -> machine(List.of(), 0, MultiblockFormationState.UNFORMED, Long.MAX_VALUE,
                new ClassicProcessFrame.Idle()));
        assertThrows(IllegalArgumentException.class, () -> machine(List.of(), 0,
                MultiblockFormationState.FORMED, 0, new ClassicProcessFrame.Idle()));
        assertThrows(IllegalArgumentException.class, () -> machine(List.of(), 1,
                MultiblockFormationState.UNSUPPORTED_DATA, 0, new ClassicProcessFrame.Idle()));
        var work = new ClassicProcessFrame.Work(RECIPE, HASH, 300, 20, 0, 0, 2, ProcessMachineState.RUNNING);
        assertThrows(IllegalArgumentException.class, () -> machine(List.of(), 0,
                MultiblockFormationState.UNFORMED, 1, work));
    }

    @Test void quarantineOwnsListAndChecksUniqueSortedSlotsAndEntities() {
        var key = new ClassicBankKey(ClassicBankKind.ITEM_OUTPUT, 1, 2, 3);
        var a = new ClassicUncertainDrop(key, 0, new UUID(0, 1));
        var b = new ClassicUncertainDrop(key, 1, new UUID(0, 2));
        var source = new ArrayList<>(List.of(a, b));
        var q = new ClassicDropQuarantine(OWNER, false, source);
        source.clear();
        assertEquals(List.of(a, b), q.uncertain());
        assertThrows(UnsupportedOperationException.class, () -> q.uncertain().clear());
        for (var entries : List.of(List.of(a, a), List.of(b, a), List.of(a,
                new ClassicUncertainDrop(key, 1, a.entityId())))) {
            assertThrows(IllegalArgumentException.class, () -> new ClassicDropQuarantine(OWNER, true, entries));
        }
        assertThrows(IllegalArgumentException.class, () -> new ClassicUncertainDrop(
                new ClassicBankKey(ClassicBankKind.FLUID_INPUT, 0, 0, 0), 0, OWNER));
        assertThrows(IllegalArgumentException.class, () -> new ClassicUncertainDrop(key, 4, OWNER));
    }

    @Test void markerAndStatusAreBoundedReadOnlyValues() {
        assertTrue(new ClassicSignatureMarker(Optional.empty()).recipeId().isEmpty());
        assertDoesNotThrow(() -> new ClassicStatusView(MultiblockFormationState.FORMED,
                ProcessMachineState.RUNNING, 72_000, 72_000, 640_000, NONE));
        assertThrows(IllegalArgumentException.class, () -> new ClassicStatusView(MultiblockFormationState.FORMED,
                ProcessMachineState.RUNNING, 1, 0, 0, NONE));
        assertThrows(IllegalArgumentException.class, () -> new ClassicStatusView(MultiblockFormationState.FORMED,
                ProcessMachineState.RUNNING, 0, 0, 640_001, NONE));
        assertThrows(IllegalArgumentException.class, () -> new ClassicSignatureMarker(Optional.of(
                new ResourceLocation("minecraft", "x".repeat(129)))));
    }

    @Test void frameChecksOwnerAndMarkerWithoutCallingNativeBankGetters() {
        var idle = machine(List.of(), 0, MultiblockFormationState.UNFORMED, 0, new ClassicProcessFrame.Idle());
        var resources = ClassicResources.empty(OWNER);
        assertDoesNotThrow(() -> new ClassicControllerFrame(idle, resources, Optional.empty(),
                new ClassicSignatureMarker(Optional.empty())));
        assertThrows(IllegalArgumentException.class, () -> new ClassicControllerFrame(idle,
                ClassicResources.empty(new UUID(9, 9)), Optional.empty(), new ClassicSignatureMarker(Optional.empty())));
        assertThrows(IllegalArgumentException.class, () -> new ClassicControllerFrame(idle, resources,
                Optional.empty(), new ClassicSignatureMarker(Optional.of(RECIPE))));
        var assigned = machine(List.of(item(0, 0, 0)), 1, MultiblockFormationState.FORMED,
                0, new ClassicProcessFrame.Idle());
        assertThrows(IllegalArgumentException.class, () -> new ClassicControllerFrame(assigned, resources,
                Optional.empty(), new ClassicSignatureMarker(Optional.empty())));
    }
}
