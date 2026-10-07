package io.github.sunthemoon.advancedrocketrycommunity.machine.classic.adapter;

import java.util.Objects;
import java.util.Optional;

record ClassicHatchCheckpoint(ClassicHatchState view, Optional<ClassicPowerHandoff> handoff) {
    ClassicHatchCheckpoint {
        Objects.requireNonNull(view, "view");
        Objects.requireNonNull(handoff, "handoff");
        handoff.ifPresent(value -> {
            ClassicValueChecks.require(view.kind() == ClassicHatchKind.POWER_INPUT, "Nonpower handoff");
            boolean retains = value.phase() == ClassicPowerHandoffPhase.DROP_SOURCE
                    || value.phase() == ClassicPowerHandoffPhase.DROP_UNCERTAIN;
            ClassicValueChecks.require(view.energy().orElseThrow() == (retains ? value.expectedEnergy() : 0),
                    "Handoff source energy");
        });
    }
}
