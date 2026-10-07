package io.github.sunthemoon.advancedrocketrycommunity.machine.classic.adapter;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

record ClassicPowerHandoff(UUID operationId, ClassicPowerHandoffPhase phase,
                           int expectedEnergy, Optional<UUID> entityId) {
    ClassicPowerHandoff {
        Objects.requireNonNull(operationId, "operationId");
        Objects.requireNonNull(phase, "phase");
        Objects.requireNonNull(entityId, "entityId");
        ClassicValueChecks.require(expectedEnergy >= 0 && expectedEnergy <= 10_000, "Handoff energy");
        boolean drop = phase == ClassicPowerHandoffPhase.DROP_SOURCE
                || phase == ClassicPowerHandoffPhase.DROP_CARRIER
                || phase == ClassicPowerHandoffPhase.DROP_UNCERTAIN;
        ClassicValueChecks.require(entityId.isPresent() == drop, "Handoff entity presence");
    }
}
