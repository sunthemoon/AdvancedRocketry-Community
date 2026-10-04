package io.github.sunthemoon.advancedrocketrycommunity.machine.classic.adapter;

import io.github.sunthemoon.advancedrocketrycommunity.machine.process.ProcessMachineState;
import java.util.Objects;
import net.minecraft.resources.ResourceLocation;

public sealed interface ClassicProcessFrame {
    record Idle() implements ClassicProcessFrame { }

    record Work(ResourceLocation recipeId, String jsonSignature, int durationTicks,
                int energyPerTick, int progressTicks, long consumedEnergy, long ordinal,
                ProcessMachineState state) implements ClassicProcessFrame {
        public Work {
            ClassicValueChecks.id(recipeId);
            ClassicValueChecks.hash(jsonSignature);
            Objects.requireNonNull(state, "state");
            ClassicValueChecks.require(durationTicks >= 1 && durationTicks <= 72_000
                    && energyPerTick >= 1 && energyPerTick <= 10_000
                    && progressTicks >= 0 && progressTicks <= durationTicks && ordinal >= 1,
                    "Work counters outside bounds");
            ClassicValueChecks.require(consumedEnergy == Math.multiplyExact((long) progressTicks, energyPerTick),
                    "Consumed FE differs from progress");
            ClassicValueChecks.require(state != ProcessMachineState.IDLE && state != ProcessMachineState.UNSUPPORTED_DATA,
                    "Invalid supported work state");
        }
    }
}
