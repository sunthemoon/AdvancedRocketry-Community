package io.github.sunthemoon.advancedrocketrycommunity.machine.process;

/** Stable machine-readable failure codes; localized text is supplied by adapters. */
public enum ProcessFailureCode {
    NONE("none"),
    MISSING_ITEM_INPUT("missing_item_input"),
    MISSING_FLUID_INPUT("missing_fluid_input"),
    OUTPUT_BLOCKED("output_blocked"),
    INSUFFICIENT_ENERGY("insufficient_energy"),
    REDSTONE_DISABLED("redstone_disabled"),
    INVALID_RECIPE("invalid_recipe"),
    STALE_TRANSACTION("stale_transaction"),
    JOURNAL_CONFLICT("journal_conflict"),
    RECOVERY_DIVERGED("recovery_diverged"),
    ARITHMETIC_OVERFLOW("arithmetic_overflow");

    private final String code;

    ProcessFailureCode(String code) {
        this.code = code;
    }

    public String code() {
        return code;
    }
}
