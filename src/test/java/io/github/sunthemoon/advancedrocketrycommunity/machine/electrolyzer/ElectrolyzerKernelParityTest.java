package io.github.sunthemoon.advancedrocketrycommunity.machine.electrolyzer;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.sunthemoon.advancedrocketrycommunity.machine.process.ProcessDefinition;
import io.github.sunthemoon.advancedrocketrycommunity.machine.process.ProcessInput;
import io.github.sunthemoon.advancedrocketrycommunity.machine.process.ProcessMachineLogic;
import io.github.sunthemoon.advancedrocketrycommunity.machine.process.ProcessOutput;
import io.github.sunthemoon.advancedrocketrycommunity.machine.process.ProcessPlan;
import io.github.sunthemoon.advancedrocketrycommunity.machine.process.ProcessResourceBalance;
import io.github.sunthemoon.advancedrocketrycommunity.machine.process.ProcessResourceKey;
import io.github.sunthemoon.advancedrocketrycommunity.machine.process.ProcessResourceKind;
import io.github.sunthemoon.advancedrocketrycommunity.machine.process.ProcessResourceSnapshot;
import java.nio.ByteBuffer;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ElectrolyzerKernelParityTest {
    private static final ProcessResourceKey EMPTY = key(
            ProcessResourceKind.ITEM,
            ElectrolyzerPortPolicy.ITEM_INPUT_CHANNEL,
            "advancedrocketrycommunity:empty_canister"
    );
    private static final ProcessResourceKey WATER = key(
            ProcessResourceKind.FLUID,
            ElectrolyzerPortPolicy.FLUID_INPUT_CHANNEL,
            "minecraft:water"
    );
    private static final ProcessResourceKey HYDROGEN = key(
            ProcessResourceKind.ITEM,
            ElectrolyzerPortPolicy.ITEM_OUTPUT_CHANNEL,
            "advancedrocketrycommunity:hydrogen_canister"
    );
    private static final ProcessResourceKey OXYGEN = key(
            ProcessResourceKind.ITEM,
            ElectrolyzerPortPolicy.ITEM_OUTPUT_CHANNEL,
            "advancedrocketrycommunity:oxygen_canister"
    );

    @Test
    void v120Elec002FiftyKernelCyclesMatchTheAcceptedLedgerByteForByte() {
        ElectrolyzerRecipeSpec spec = ElectrolyzerRecipeSpec.fixedRecipe();
        ProcessDefinition definition = definition(spec);
        ProcessResourceSnapshot kernel = snapshot();
        ElectrolyzerMaterialLedger accepted = new ElectrolyzerMaterialLedger(
                100,
                50_000,
                100_000,
                0,
                0
        );
        long kernelEnergy = accepted.storedEnergy();

        for (int cycle = 0; cycle < 50; cycle++) {
            ProcessPlan plan = ProcessMachineLogic.simulate(definition, kernel).plan().orElseThrow();
            kernel = plan.after();
            kernelEnergy = Math.subtractExact(kernelEnergy, definition.totalEnergy());
            accepted = accepted.process(spec);
            assertArrayEquals(encode(accepted), encode(kernel, kernelEnergy), "cycle " + (cycle + 1));
        }

        assertEquals(50, kernel.revision());
        assertTrue(ProcessMachineLogic.simulate(definition, kernel).plan().isEmpty());
        assertEquals(100, accepted.totalCanisters());
    }

    private static ProcessDefinition definition(ElectrolyzerRecipeSpec spec) {
        return new ProcessDefinition(
                "advancedrocketrycommunity:electrolyzer_water",
                ProcessDefinition.SCHEMA_VERSION,
                256,
                spec.processingTicks(),
                spec.energyPerTick(),
                List.of(
                        new ProcessInput(
                                ProcessResourceKind.ITEM,
                                ElectrolyzerPortPolicy.ITEM_INPUT_CHANNEL,
                                List.of(EMPTY.resourceId()),
                                spec.inputCount()
                        ),
                        new ProcessInput(
                                ProcessResourceKind.FLUID,
                                ElectrolyzerPortPolicy.FLUID_INPUT_CHANNEL,
                                List.of(WATER.resourceId()),
                                spec.waterAmount()
                        )
                ),
                List.of(
                        new ProcessOutput(HYDROGEN, spec.hydrogenOutputCount()),
                        new ProcessOutput(OXYGEN, spec.oxygenOutputCount())
                )
        );
    }

    private static ProcessResourceSnapshot snapshot() {
        Map<ProcessResourceKey, ProcessResourceBalance> balances = new LinkedHashMap<>();
        balances.put(EMPTY, new ProcessResourceBalance(100, 100));
        balances.put(WATER, new ProcessResourceBalance(50_000, 50_000));
        balances.put(HYDROGEN, new ProcessResourceBalance(0, 50));
        balances.put(OXYGEN, new ProcessResourceBalance(0, 50));
        return new ProcessResourceSnapshot(0, balances);
    }

    private static byte[] encode(ElectrolyzerMaterialLedger ledger) {
        return ByteBuffer.allocate(Long.BYTES * 5)
                .putLong(ledger.emptyCanisters())
                .putLong(ledger.waterAmount())
                .putLong(ledger.storedEnergy())
                .putLong(ledger.hydrogenCanisters())
                .putLong(ledger.oxygenCanisters())
                .array();
    }

    private static byte[] encode(ProcessResourceSnapshot snapshot, long energy) {
        return ByteBuffer.allocate(Long.BYTES * 5)
                .putLong(snapshot.balance(EMPTY).amount())
                .putLong(snapshot.balance(WATER).amount())
                .putLong(energy)
                .putLong(snapshot.balance(HYDROGEN).amount())
                .putLong(snapshot.balance(OXYGEN).amount())
                .array();
    }

    private static ProcessResourceKey key(ProcessResourceKind kind, String channel, String resourceId) {
        return new ProcessResourceKey(kind, channel, resourceId);
    }
}
