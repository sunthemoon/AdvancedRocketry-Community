package io.github.sunthemoon.advancedrocketrycommunity.machine.multiblock.lifecycle;

/** Persisted server-authoritative controller formation state. */
public enum MultiblockFormationState {
    UNFORMED,
    FORMED,
    WAITING_UNLOADED,
    INVALID_DEFINITION,
    BINDING_CONFLICT,
    UNSUPPORTED_DATA
}
