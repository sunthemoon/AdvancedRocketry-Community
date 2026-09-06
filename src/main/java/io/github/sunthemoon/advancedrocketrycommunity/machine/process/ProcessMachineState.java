package io.github.sunthemoon.advancedrocketrycommunity.machine.process;

/** Server-authoritative process states persisted by machine adapters. */
public enum ProcessMachineState {
    IDLE,
    RUNNING,
    WAITING_INPUT,
    WAITING_OUTPUT,
    WAITING_ENERGY,
    REDSTONE_DISABLED,
    INVALID_RECIPE,
    UNSUPPORTED_DATA,
    RECOVERY_REQUIRED
}
