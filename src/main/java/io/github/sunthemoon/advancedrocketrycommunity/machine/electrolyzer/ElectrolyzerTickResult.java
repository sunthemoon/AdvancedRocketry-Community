package io.github.sunthemoon.advancedrocketrycommunity.machine.electrolyzer;

/** Result of one pure processing transition. */
public record ElectrolyzerTickResult(
        int progress,
        int energyConsumed,
        boolean completed,
        ElectrolyzerStatus status
) {
}
