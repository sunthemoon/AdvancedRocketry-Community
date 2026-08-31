package io.github.sunthemoon.advancedrocketrycommunity.machine.electrolyzer;

/** Resource and control snapshot for one server-authoritative processing tick. */
public record ElectrolyzerTickInput(
        boolean recipeAvailable,
        boolean enabled,
        boolean waterAvailable,
        boolean outputSpaceAvailable,
        int storedEnergy,
        int energyPerTick
) {
    public ElectrolyzerTickInput {
        if (storedEnergy < 0) {
            throw new IllegalArgumentException("storedEnergy cannot be negative");
        }
        if (energyPerTick < 1 || energyPerTick > ElectrolyzerRecipeSpec.MAX_ENERGY_PER_TICK) {
            throw new IllegalArgumentException("energyPerTick is outside the bounded recipe range");
        }
    }
}
