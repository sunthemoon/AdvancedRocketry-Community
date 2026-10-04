package io.github.sunthemoon.advancedrocketrycommunity.machine.pump;

/** Stable, translated refusal/status codes; no coordinates or owner details. */
public enum PumpCode {
    SEARCHING, SOURCE_READY, DRAINED, COOLDOWN, NO_OWNER, NO_ENERGY, DISABLED,
    TANK_FULL, TANK_INCOMPATIBLE, NO_FLUID, SOURCE_UNSUPPORTED, SOURCE_CHANGED,
    TARGET_UNLOADED, TARGET_OUT_OF_BOUNDS, TARGET_PROTECTED, SEARCH_EXHAUSTED,
    SEARCH_LIMIT, REPAIR_REQUIRED
}
