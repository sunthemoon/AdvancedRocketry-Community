package io.github.sunthemoon.advancedrocketrycommunity.machine.process;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** Immutable and bounded process recipe definition. */
public record ProcessDefinition(
        String id,
        int schemaVersion,
        int encodedSizeBytes,
        int durationTicks,
        int energyPerTick,
        List<ProcessInput> inputs,
        List<ProcessOutput> outputs
) {
    public static final int SCHEMA_VERSION = 1;
    public static final int MAX_DEFINITION_BYTES = 65_536;
    public static final int MAX_DURATION_TICKS = 72_000;
    public static final int MAX_ENERGY_PER_TICK = 16_384;
    public static final int MAX_INPUTS_PER_KIND = 8;
    public static final int MAX_OUTPUTS_PER_KIND = 8;

    public ProcessDefinition {
        ProcessResourceKey identity = new ProcessResourceKey(ProcessResourceKind.ITEM, "recipe", id);
        id = identity.resourceId();
        if (schemaVersion != SCHEMA_VERSION) {
            throw new IllegalArgumentException("unsupported process definition schema");
        }
        if (encodedSizeBytes < 1 || encodedSizeBytes > MAX_DEFINITION_BYTES) {
            throw new IllegalArgumentException("encoded definition exceeds the byte limit");
        }
        if (durationTicks < 1 || durationTicks > MAX_DURATION_TICKS) {
            throw new IllegalArgumentException("durationTicks is outside the bounded range");
        }
        if (energyPerTick < 1 || energyPerTick > MAX_ENERGY_PER_TICK) {
            throw new IllegalArgumentException("energyPerTick is outside the bounded range");
        }
        long totalEnergy = Math.multiplyExact((long) durationTicks, energyPerTick);
        if (totalEnergy > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("total process energy exceeds the supported range");
        }
        inputs = List.copyOf(Objects.requireNonNull(inputs, "inputs"));
        outputs = List.copyOf(Objects.requireNonNull(outputs, "outputs"));
        requireCountWithinLimit(inputs, ProcessResourceKind.ITEM, MAX_INPUTS_PER_KIND, "item inputs");
        requireCountWithinLimit(inputs, ProcessResourceKind.FLUID, MAX_INPUTS_PER_KIND, "fluid inputs");
        requireOutputCountWithinLimit(outputs, ProcessResourceKind.ITEM, MAX_OUTPUTS_PER_KIND, "item outputs");
        requireOutputCountWithinLimit(outputs, ProcessResourceKind.FLUID, MAX_OUTPUTS_PER_KIND, "fluid outputs");
        Set<ProcessResourceKey> outputKeys = new HashSet<>();
        for (ProcessOutput output : outputs) {
            Objects.requireNonNull(output, "output");
            if (!outputKeys.add(output.key())) {
                throw new IllegalArgumentException("duplicate output resource key");
            }
        }
        inputs.forEach(input -> Objects.requireNonNull(input, "input"));
    }

    public long totalEnergy() {
        return (long) durationTicks * energyPerTick;
    }

    private static void requireCountWithinLimit(
            List<ProcessInput> values,
            ProcessResourceKind kind,
            int limit,
            String field
    ) {
        if (values.stream().filter(input -> input != null && input.kind() == kind).count() > limit) {
            throw new IllegalArgumentException(field + " exceed the limit");
        }
    }

    private static void requireOutputCountWithinLimit(
            List<ProcessOutput> values,
            ProcessResourceKind kind,
            int limit,
            String field
    ) {
        if (values.stream().filter(output -> output != null && output.key().kind() == kind).count() > limit) {
            throw new IllegalArgumentException(field + " exceed the limit");
        }
    }
}
