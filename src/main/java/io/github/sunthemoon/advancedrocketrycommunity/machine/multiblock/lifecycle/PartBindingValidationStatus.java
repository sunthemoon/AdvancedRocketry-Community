package io.github.sunthemoon.advancedrocketrycommunity.machine.multiblock.lifecycle;

public enum PartBindingValidationStatus {
    VALID,
    WRONG_LEVEL,
    CONTROLLER_UNLOADED,
    CONTROLLER_MISSING,
    WRONG_INSTANCE,
    STALE_GENERATION,
    CONTROLLER_INACTIVE
}
