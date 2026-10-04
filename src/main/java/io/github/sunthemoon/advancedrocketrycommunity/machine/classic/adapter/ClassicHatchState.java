package io.github.sunthemoon.advancedrocketrycommunity.machine.classic.adapter;

import io.github.sunthemoon.advancedrocketrycommunity.machine.classic.resource.ClassicBankKey;
import io.github.sunthemoon.advancedrocketrycommunity.machine.multiblock.lifecycle.MultiblockPartBinding;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;

public record ClassicHatchState(ClassicHatchKind kind, Optional<MultiblockPartBinding> binding,
                                Optional<ClassicBankKey> bankKey, OptionalInt energy) {
    public ClassicHatchState {
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(binding, "binding");
        Objects.requireNonNull(bankKey, "bankKey");
        Objects.requireNonNull(energy, "energy");
        boolean power = kind == ClassicHatchKind.POWER_INPUT;
        ClassicValueChecks.require(energy.isPresent() == power, "Energy exists only on power plugs");
        ClassicValueChecks.require(bankKey.isPresent() == (!power && binding.isPresent()), "Hatch bank binding");
        if (power) { ClassicValueChecks.require(energy.getAsInt() >= 0 && energy.getAsInt() <= 10_000, "FE bounds"); }
        if (bankKey.isPresent()) {
            ClassicValueChecks.require(bankKey.orElseThrow().kind() == kind.bankKind().orElseThrow(), "Hatch bank kind");
        }
    }
}
