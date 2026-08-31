package io.github.sunthemoon.advancedrocketrycommunity.machine.electrolyzer;

/** Exact material/energy counts used to prove atomic batch conservation. */
public record ElectrolyzerMaterialLedger(
        long emptyCanisters,
        long waterAmount,
        long storedEnergy,
        long hydrogenCanisters,
        long oxygenCanisters
) {
    public ElectrolyzerMaterialLedger {
        requireNonNegative("emptyCanisters", emptyCanisters);
        requireNonNegative("waterAmount", waterAmount);
        requireNonNegative("storedEnergy", storedEnergy);
        requireNonNegative("hydrogenCanisters", hydrogenCanisters);
        requireNonNegative("oxygenCanisters", oxygenCanisters);
    }

    public ElectrolyzerMaterialLedger process(ElectrolyzerRecipeSpec recipe) {
        if (emptyCanisters < recipe.inputCount()) {
            throw new IllegalStateException("Not enough empty canisters");
        }
        if (waterAmount < recipe.waterAmount()) {
            throw new IllegalStateException("Not enough water");
        }
        if (storedEnergy < recipe.totalEnergy()) {
            throw new IllegalStateException("Not enough energy");
        }
        return new ElectrolyzerMaterialLedger(
                Math.subtractExact(emptyCanisters, recipe.inputCount()),
                Math.subtractExact(waterAmount, recipe.waterAmount()),
                Math.subtractExact(storedEnergy, recipe.totalEnergy()),
                Math.addExact(hydrogenCanisters, recipe.hydrogenOutputCount()),
                Math.addExact(oxygenCanisters, recipe.oxygenOutputCount())
        );
    }

    public long totalCanisters() {
        return Math.addExact(emptyCanisters, Math.addExact(hydrogenCanisters, oxygenCanisters));
    }

    private static void requireNonNegative(String field, long value) {
        if (value < 0) {
            throw new IllegalArgumentException(field + " cannot be negative");
        }
    }
}
