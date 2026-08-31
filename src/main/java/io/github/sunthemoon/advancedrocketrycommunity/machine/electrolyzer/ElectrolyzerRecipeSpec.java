package io.github.sunthemoon.advancedrocketrycommunity.machine.electrolyzer;

/** Immutable, bounded processing values shared by recipe and machine adapters. */
public record ElectrolyzerRecipeSpec(
        int schemaVersion,
        int inputCount,
        int waterAmount,
        int processingTicks,
        int energyPerTick,
        int hydrogenOutputCount,
        int oxygenOutputCount
) {
    public static final int CURRENT_SCHEMA_VERSION = 1;
    public static final int MAX_INPUT_COUNT = 64;
    public static final int MAX_WATER_AMOUNT = 4_000;
    public static final int MAX_PROCESSING_TICKS = 1_200;
    public static final int MAX_ENERGY_PER_TICK = 1_000;
    public static final int MAX_ENERGY_PER_RECIPE = 20_000;
    public static final int MAX_OUTPUT_COUNT = 64;

    public ElectrolyzerRecipeSpec {
        if (schemaVersion != CURRENT_SCHEMA_VERSION) {
            throw new IllegalArgumentException("Unsupported Electrolyzer recipe schema: " + schemaVersion);
        }
        requireRange("inputCount", inputCount, 1, MAX_INPUT_COUNT);
        requireRange("waterAmount", waterAmount, 1, MAX_WATER_AMOUNT);
        requireRange("processingTicks", processingTicks, 1, MAX_PROCESSING_TICKS);
        requireRange("energyPerTick", energyPerTick, 1, MAX_ENERGY_PER_TICK);
        requireRange("hydrogenOutputCount", hydrogenOutputCount, 1, MAX_OUTPUT_COUNT);
        requireRange("oxygenOutputCount", oxygenOutputCount, 1, MAX_OUTPUT_COUNT);
        if ((long) processingTicks * energyPerTick > MAX_ENERGY_PER_RECIPE) {
            throw new IllegalArgumentException(
                    "Electrolyzer recipe energy exceeds buffer capacity: "
                            + (long) processingTicks * energyPerTick
            );
        }
    }

    public static ElectrolyzerRecipeSpec fixedRecipe() {
        return new ElectrolyzerRecipeSpec(1, 2, 1_000, 100, 20, 1, 1);
    }

    public int totalEnergy() {
        return Math.multiplyExact(processingTicks, energyPerTick);
    }

    private static void requireRange(String field, int value, int minimum, int maximum) {
        if (value < minimum || value > maximum) {
            throw new IllegalArgumentException(
                    field + " must be in [" + minimum + ", " + maximum + "]: " + value
            );
        }
    }
}
