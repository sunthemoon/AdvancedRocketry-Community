package io.github.sunthemoon.advancedrocketrycommunity.machine.process;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ProcessMachineLogicTest {
    private static final ProcessResourceKey IRON = key(ProcessResourceKind.ITEM, "item_input", "minecraft:iron_ingot");
    private static final ProcessResourceKey COPPER = key(ProcessResourceKind.ITEM, "item_input", "minecraft:copper_ingot");
    private static final ProcessResourceKey WATER = key(ProcessResourceKind.FLUID, "fluid_input", "minecraft:water");
    private static final ProcessResourceKey PLATE = key(ProcessResourceKind.ITEM, "item_output", "test:plate");
    private static final ProcessResourceKey STEAM = key(ProcessResourceKind.FLUID, "fluid_output", "test:steam");

    @Test
    void validSimulationReturnsExactImmutablePlanWithoutMutatingInput() {
        ProcessDefinition definition = definition();
        ProcessResourceSnapshot before = snapshot(7, 4, 1_000, 0, 0);

        ProcessSimulationResult result = ProcessMachineLogic.simulate(definition, before);

        assertTrue(result.successful());
        ProcessPlan plan = result.plan().orElseThrow();
        assertEquals(4, before.balance(IRON).amount());
        assertEquals(2, plan.after().balance(IRON).amount());
        assertEquals(0, plan.after().balance(WATER).amount());
        assertEquals(1, plan.after().balance(PLATE).amount());
        assertEquals(250, plan.after().balance(STEAM).amount());
        assertEquals(8, plan.after().revision());
        assertEquals(before.fingerprint(), plan.expectedFingerprint());
    }

    @Test
    void deterministicAlternativeSelectionDoesNotDoubleSpend() {
        ProcessDefinition definition = definition();
        ProcessResourceSnapshot before = new ProcessResourceSnapshot(0, Map.of(
                IRON, new ProcessResourceBalance(1, 64),
                COPPER, new ProcessResourceBalance(2, 64),
                WATER, new ProcessResourceBalance(1_000, 4_000),
                PLATE, new ProcessResourceBalance(0, 64),
                STEAM, new ProcessResourceBalance(0, 4_000)
        ));

        ProcessPlan plan = ProcessMachineLogic.simulate(definition, before).plan().orElseThrow();

        assertEquals(1, plan.after().balance(IRON).amount());
        assertEquals(0, plan.after().balance(COPPER).amount());
    }

    @Test
    void missingItemAndFluidInputsHaveStableIndependentFailures() {
        ProcessSimulationResult missingItem = ProcessMachineLogic.simulate(
                definition(),
                snapshot(0, 1, 1_000, 0, 0)
        );
        ProcessSimulationResult missingFluid = ProcessMachineLogic.simulate(
                definition(),
                snapshot(0, 2, 999, 0, 0)
        );

        assertEquals(ProcessFailureCode.MISSING_ITEM_INPUT, missingItem.failure().code());
        assertEquals("item_input", missingItem.failure().subject());
        assertEquals(ProcessFailureCode.MISSING_FLUID_INPUT, missingFluid.failure().code());
        assertEquals("fluid_input", missingFluid.failure().subject());
    }

    @Test
    void blockedItemOrFluidOutputProducesNoPlan() {
        ProcessSimulationResult itemBlocked = ProcessMachineLogic.simulate(
                definition(),
                snapshot(0, 2, 1_000, 64, 0)
        );
        ProcessSimulationResult fluidBlocked = ProcessMachineLogic.simulate(
                definition(),
                snapshot(0, 2, 1_000, 0, 3_900)
        );

        assertFalse(itemBlocked.successful());
        assertEquals(ProcessFailureCode.OUTPUT_BLOCKED, itemBlocked.failure().code());
        assertEquals("item_output", itemBlocked.failure().subject());
        assertEquals(ProcessFailureCode.OUTPUT_BLOCKED, fluidBlocked.failure().code());
        assertEquals("fluid_output", fluidBlocked.failure().subject());
    }

    @Test
    void tickConsumesPerTickEnergyAndReportsCompletionWithoutCommittingResources() {
        ProcessDefinition definition = definition();
        ProcessProgress progress = new ProcessProgress(definition.id(), 9, 180);

        ProcessTickResult result = ProcessMachineLogic.tick(
                definition,
                progress,
                new ProcessTickInput(true, true, ProcessResourceAvailability.READY, 20)
        );

        assertEquals(10, result.progress().progressTicks());
        assertEquals(200, result.progress().consumedEnergy());
        assertEquals(20, result.energyConsumed());
        assertTrue(result.completionDue());
        assertEquals(ProcessMachineState.RUNNING, result.state());
    }

    @Test
    void everyPauseReasonPreservesProgressAndConsumesNothing() {
        ProcessDefinition definition = definition();
        ProcessProgress progress = new ProcessProgress(definition.id(), 4, 80);

        assertPaused(definition, progress, new ProcessTickInput(false, true, ProcessResourceAvailability.READY, 20),
                ProcessMachineState.INVALID_RECIPE, ProcessFailureCode.INVALID_RECIPE);
        assertPaused(definition, progress, new ProcessTickInput(true, false, ProcessResourceAvailability.READY, 20),
                ProcessMachineState.REDSTONE_DISABLED, ProcessFailureCode.REDSTONE_DISABLED);
        assertPaused(definition, progress, new ProcessTickInput(true, true, ProcessResourceAvailability.MISSING_ITEM_INPUT, 20),
                ProcessMachineState.WAITING_INPUT, ProcessFailureCode.MISSING_ITEM_INPUT);
        assertPaused(definition, progress, new ProcessTickInput(true, true, ProcessResourceAvailability.MISSING_FLUID_INPUT, 20),
                ProcessMachineState.WAITING_INPUT, ProcessFailureCode.MISSING_FLUID_INPUT);
        assertPaused(definition, progress, new ProcessTickInput(true, true, ProcessResourceAvailability.OUTPUT_BLOCKED, 20),
                ProcessMachineState.WAITING_OUTPUT, ProcessFailureCode.OUTPUT_BLOCKED);
        assertPaused(definition, progress, new ProcessTickInput(true, true, ProcessResourceAvailability.READY, 19),
                ProcessMachineState.WAITING_ENERGY, ProcessFailureCode.INSUFFICIENT_ENERGY);
    }

    @Test
    void completedOrMismatchedProgressCannotTickAgain() {
        ProcessDefinition definition = definition();
        assertThrows(
                IllegalArgumentException.class,
                () -> ProcessMachineLogic.tick(
                        definition,
                        new ProcessProgress(definition.id(), 10, 200),
                        new ProcessTickInput(true, true, ProcessResourceAvailability.READY, 20)
                )
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> ProcessMachineLogic.tick(
                        definition,
                        new ProcessProgress("test:other", 0, 0),
                        new ProcessTickInput(true, true, ProcessResourceAvailability.READY, 20)
                )
        );
    }

    @Test
    void revisionOverflowIsReportedBeforeMutation() {
        ProcessResourceSnapshot snapshot = snapshot(Long.MAX_VALUE, 2, 1_000, 0, 0);

        ProcessSimulationResult result = ProcessMachineLogic.simulate(definition(), snapshot);

        assertEquals(ProcessFailureCode.ARITHMETIC_OVERFLOW, result.failure().code());
        assertEquals(2, snapshot.balance(IRON).amount());
    }

    @Test
    void outputAmountOverflowIsReportedBeforeMutation() {
        ProcessDefinition definition = new ProcessDefinition(
                "test:overflow",
                1,
                100,
                1,
                1,
                List.of(),
                List.of(new ProcessOutput(PLATE, 1))
        );
        ProcessResourceSnapshot snapshot = new ProcessResourceSnapshot(
                0,
                Map.of(PLATE, new ProcessResourceBalance(Long.MAX_VALUE, Long.MAX_VALUE))
        );

        ProcessSimulationResult result = ProcessMachineLogic.simulate(definition, snapshot);

        assertEquals(ProcessFailureCode.ARITHMETIC_OVERFLOW, result.failure().code());
        assertEquals(Long.MAX_VALUE, snapshot.balance(PLATE).amount());
    }

    @Test
    void snapshotFingerprintIsDeterministicAndIncludesCapacity() {
        ProcessResourceSnapshot ordered = new ProcessResourceSnapshot(3, Map.of(
                IRON, new ProcessResourceBalance(2, 64),
                WATER, new ProcessResourceBalance(1_000, 4_000)
        ));
        Map<ProcessResourceKey, ProcessResourceBalance> reversed = new LinkedHashMap<>();
        reversed.put(WATER, new ProcessResourceBalance(1_000, 4_000));
        reversed.put(IRON, new ProcessResourceBalance(2, 64));
        ProcessResourceSnapshot same = new ProcessResourceSnapshot(3, reversed);
        ProcessResourceSnapshot changedCapacity = new ProcessResourceSnapshot(3, Map.of(
                IRON, new ProcessResourceBalance(2, 63),
                WATER, new ProcessResourceBalance(1_000, 4_000)
        ));

        assertEquals(ordered.fingerprint(), same.fingerprint());
        assertFalse(ordered.fingerprint().equals(changedCapacity.fingerprint()));
    }

    private static void assertPaused(
            ProcessDefinition definition,
            ProcessProgress progress,
            ProcessTickInput input,
            ProcessMachineState expectedState,
            ProcessFailureCode expectedFailure
    ) {
        ProcessTickResult result = ProcessMachineLogic.tick(definition, progress, input);
        assertEquals(progress, result.progress());
        assertEquals(0, result.energyConsumed());
        assertFalse(result.completionDue());
        assertEquals(expectedState, result.state());
        assertEquals(expectedFailure, result.failure().code());
    }

    private static ProcessDefinition definition() {
        return new ProcessDefinition(
                "test:rolling",
                1,
                256,
                10,
                20,
                List.of(
                        new ProcessInput(
                                ProcessResourceKind.ITEM,
                                "item_input",
                                List.of("minecraft:iron_ingot", "minecraft:copper_ingot"),
                                2
                        ),
                        new ProcessInput(ProcessResourceKind.FLUID, "fluid_input", List.of("minecraft:water"), 1_000)
                ),
                List.of(new ProcessOutput(PLATE, 1), new ProcessOutput(STEAM, 250))
        );
    }

    private static ProcessResourceSnapshot snapshot(
            long revision,
            long iron,
            long water,
            long plates,
            long steam
    ) {
        Map<ProcessResourceKey, ProcessResourceBalance> balances = new LinkedHashMap<>();
        balances.put(IRON, new ProcessResourceBalance(iron, 64));
        balances.put(WATER, new ProcessResourceBalance(water, 4_000));
        balances.put(PLATE, new ProcessResourceBalance(plates, 64));
        balances.put(STEAM, new ProcessResourceBalance(steam, 4_000));
        return new ProcessResourceSnapshot(revision, balances);
    }

    private static ProcessResourceKey key(ProcessResourceKind kind, String channel, String resourceId) {
        return new ProcessResourceKey(kind, channel, resourceId);
    }
}
