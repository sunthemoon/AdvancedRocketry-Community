package io.github.sunthemoon.advancedrocketrycommunity.machine.process;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class ProcessDefinitionTest {
    @Test
    void acceptsEveryInclusiveNumericBoundary() {
        ProcessDefinition minimum = definition(1, 1, 1, List.of(), List.of());
        ProcessDefinition maximum = definition(
                ProcessDefinition.MAX_DEFINITION_BYTES,
                ProcessDefinition.MAX_DURATION_TICKS,
                ProcessDefinition.MAX_ENERGY_PER_TICK,
                List.of(),
                List.of()
        );

        assertEquals(1, minimum.totalEnergy());
        assertEquals(1_179_648_000L, maximum.totalEnergy());
    }

    @Test
    void rejectsEachNumericBound() {
        assertThrows(IllegalArgumentException.class, () -> definition(0, 1, 1, List.of(), List.of()));
        assertThrows(IllegalArgumentException.class, () -> definition(65_537, 1, 1, List.of(), List.of()));
        assertThrows(IllegalArgumentException.class, () -> definition(1, 0, 1, List.of(), List.of()));
        assertThrows(IllegalArgumentException.class, () -> definition(1, 72_001, 1, List.of(), List.of()));
        assertThrows(IllegalArgumentException.class, () -> definition(1, 1, 0, List.of(), List.of()));
        assertThrows(IllegalArgumentException.class, () -> definition(1, 1, 16_385, List.of(), List.of()));
    }

    @Test
    void rejectsNinthInputOrOutputOfEitherKind() {
        List<ProcessInput> itemInputs = new ArrayList<>();
        List<ProcessOutput> fluidOutputs = new ArrayList<>();
        for (int index = 0; index < 9; index++) {
            itemInputs.add(input(ProcessResourceKind.ITEM, "input" + index, "minecraft:iron_ingot", 1));
            fluidOutputs.add(output(ProcessResourceKind.FLUID, "output" + index, "minecraft:water", 1));
        }

        assertThrows(
                IllegalArgumentException.class,
                () -> definition(100, 10, 10, itemInputs, List.of())
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> definition(100, 10, 10, List.of(), fluidOutputs)
        );
    }

    @Test
    void rejectsThirtyThirdVariantAndOversizedResourceIdentity() {
        List<String> variants = new ArrayList<>();
        for (int index = 0; index < 33; index++) {
            variants.add("test:variant_" + index);
        }
        assertThrows(
                IllegalArgumentException.class,
                () -> new ProcessInput(ProcessResourceKind.ITEM, "input", variants, 1)
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> new ProcessResourceKey(ProcessResourceKind.ITEM, "input", "test:" + "a".repeat(124))
        );
    }

    @Test
    void rejectsDuplicateOutputsAndUnsupportedSchema() {
        ProcessOutput output = output(ProcessResourceKind.ITEM, "output", "test:plate", 1);
        assertThrows(
                IllegalArgumentException.class,
                () -> definition(100, 10, 10, List.of(), List.of(output, output))
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> new ProcessDefinition("test:rolling", 2, 100, 10, 10, List.of(), List.of())
        );
    }

    private static ProcessDefinition definition(
            int bytes,
            int duration,
            int energy,
            List<ProcessInput> inputs,
            List<ProcessOutput> outputs
    ) {
        return new ProcessDefinition("test:rolling", 1, bytes, duration, energy, inputs, outputs);
    }

    private static ProcessInput input(
            ProcessResourceKind kind,
            String channel,
            String resourceId,
            long amount
    ) {
        return new ProcessInput(kind, channel, List.of(resourceId), amount);
    }

    private static ProcessOutput output(
            ProcessResourceKind kind,
            String channel,
            String resourceId,
            long amount
    ) {
        return new ProcessOutput(new ProcessResourceKey(kind, channel, resourceId), amount);
    }
}
