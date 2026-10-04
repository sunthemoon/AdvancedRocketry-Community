package io.github.sunthemoon.advancedrocketrycommunity.machine.classic.adapter;

import io.github.sunthemoon.advancedrocketrycommunity.machine.multiblock.lifecycle.MultiblockFormationState;
import io.github.sunthemoon.advancedrocketrycommunity.machine.process.ProcessMachineState;
import java.util.Objects;

public record ClassicStatusView(MultiblockFormationState formation, ProcessMachineState process,
                                int progressTicks, int durationTicks, int totalLoadedEnergy,
                                ClassicRefusal refusal) {
    public ClassicStatusView {
        Objects.requireNonNull(formation, "formation");
        Objects.requireNonNull(process, "process");
        Objects.requireNonNull(refusal, "refusal");
        ClassicValueChecks.require(durationTicks >= 0 && durationTicks <= 72_000
                && progressTicks >= 0 && progressTicks <= durationTicks
                && totalLoadedEnergy >= 0 && totalLoadedEnergy <= 640_000, "Status bounds");
    }
}
