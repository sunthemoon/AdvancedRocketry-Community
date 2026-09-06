package io.github.sunthemoon.advancedrocketrycommunity.machine.process;

import java.util.HashMap;
import java.util.Map;

/** Pure process tick and completion simulation logic with no Minecraft dependency. */
public final class ProcessMachineLogic {
    private ProcessMachineLogic() {
    }

    public static ProcessTickResult tick(
            ProcessDefinition definition,
            ProcessProgress progress,
            ProcessTickInput input
    ) {
        requireMatchingProgress(definition, progress);
        if (progress.progressTicks() >= definition.durationTicks()) {
            throw new IllegalArgumentException("completed progress must be committed before another tick");
        }
        if (!input.recipeAvailable()) {
            return paused(progress, ProcessMachineState.INVALID_RECIPE, ProcessFailureCode.INVALID_RECIPE);
        }
        if (!input.enabled()) {
            return paused(progress, ProcessMachineState.REDSTONE_DISABLED, ProcessFailureCode.REDSTONE_DISABLED);
        }
        if (input.resourceAvailability() != ProcessResourceAvailability.READY) {
            return pausedForResources(progress, input.resourceAvailability());
        }
        if (input.storedEnergy() < definition.energyPerTick()) {
            return paused(progress, ProcessMachineState.WAITING_ENERGY, ProcessFailureCode.INSUFFICIENT_ENERGY);
        }

        int nextTicks = Math.addExact(progress.progressTicks(), 1);
        long nextEnergy = Math.addExact(progress.consumedEnergy(), definition.energyPerTick());
        ProcessProgress nextProgress = new ProcessProgress(definition.id(), nextTicks, nextEnergy);
        return new ProcessTickResult(
                nextProgress,
                definition.energyPerTick(),
                nextTicks == definition.durationTicks(),
                ProcessMachineState.RUNNING,
                ProcessFailure.NONE
        );
    }

    public static ProcessSimulationResult simulate(
            ProcessDefinition definition,
            ProcessResourceSnapshot snapshot
    ) {
        Map<ProcessResourceKey, ProcessResourceBalance> next = new HashMap<>(snapshot.balances());
        try {
            for (ProcessInput input : definition.inputs()) {
                ProcessResourceKey selected = selectInput(input, next);
                if (selected == null) {
                    return ProcessSimulationResult.failure(missingCode(input.kind()), input.channel());
                }
                ProcessResourceBalance balance = next.get(selected);
                next.put(selected, balance.withAmount(Math.subtractExact(balance.amount(), input.amount())));
            }
            for (ProcessOutput output : definition.outputs()) {
                ProcessResourceBalance balance = next.getOrDefault(
                        output.key(),
                        new ProcessResourceBalance(0, 0)
                );
                long nextAmount = Math.addExact(balance.amount(), output.amount());
                if (nextAmount > balance.capacity()) {
                    return ProcessSimulationResult.failure(
                            ProcessFailureCode.OUTPUT_BLOCKED,
                            output.key().channel()
                    );
                }
                next.put(output.key(), balance.withAmount(nextAmount));
            }
            ProcessResourceSnapshot after = new ProcessResourceSnapshot(
                    Math.addExact(snapshot.revision(), 1),
                    next
            );
            return ProcessSimulationResult.success(new ProcessPlan(
                    definition.id(),
                    snapshot.revision(),
                    snapshot.fingerprint(),
                    snapshot,
                    after
            ));
        } catch (ArithmeticException exception) {
            return ProcessSimulationResult.failure(ProcessFailureCode.ARITHMETIC_OVERFLOW, definition.id());
        }
    }

    private static ProcessResourceKey selectInput(
            ProcessInput input,
            Map<ProcessResourceKey, ProcessResourceBalance> balances
    ) {
        for (String alternative : input.alternatives()) {
            ProcessResourceKey key = input.key(alternative);
            ProcessResourceBalance balance = balances.get(key);
            if (balance != null && balance.amount() >= input.amount()) {
                return key;
            }
        }
        return null;
    }

    private static void requireMatchingProgress(ProcessDefinition definition, ProcessProgress progress) {
        if (!definition.id().equals(progress.definitionId())) {
            throw new IllegalArgumentException("progress belongs to a different process definition");
        }
    }

    private static ProcessTickResult paused(
            ProcessProgress progress,
            ProcessMachineState state,
            ProcessFailureCode code
    ) {
        return new ProcessTickResult(progress, 0, false, state, new ProcessFailure(code, progress.definitionId()));
    }

    private static ProcessTickResult pausedForResources(
            ProcessProgress progress,
            ProcessResourceAvailability availability
    ) {
        return switch (availability) {
            case MISSING_ITEM_INPUT -> paused(
                    progress,
                    ProcessMachineState.WAITING_INPUT,
                    ProcessFailureCode.MISSING_ITEM_INPUT
            );
            case MISSING_FLUID_INPUT -> paused(
                    progress,
                    ProcessMachineState.WAITING_INPUT,
                    ProcessFailureCode.MISSING_FLUID_INPUT
            );
            case OUTPUT_BLOCKED -> paused(
                    progress,
                    ProcessMachineState.WAITING_OUTPUT,
                    ProcessFailureCode.OUTPUT_BLOCKED
            );
            case READY -> throw new IllegalArgumentException("ready resources do not pause a process");
        };
    }

    private static ProcessFailureCode missingCode(ProcessResourceKind kind) {
        return kind == ProcessResourceKind.ITEM
                ? ProcessFailureCode.MISSING_ITEM_INPUT
                : ProcessFailureCode.MISSING_FLUID_INPUT;
    }
}
