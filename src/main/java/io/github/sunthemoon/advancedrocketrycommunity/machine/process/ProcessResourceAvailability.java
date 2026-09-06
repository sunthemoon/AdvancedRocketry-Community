package io.github.sunthemoon.advancedrocketrycommunity.machine.process;

/** Bounded resource preflight outcome passed into the pure tick transition. */
public enum ProcessResourceAvailability {
    READY,
    MISSING_ITEM_INPUT,
    MISSING_FLUID_INPUT,
    OUTPUT_BLOCKED
}
