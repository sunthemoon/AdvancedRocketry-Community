package io.github.sunthemoon.advancedrocketrycommunity.machine.process;

/** Server-observed conditions for one bounded process tick. */
public record ProcessTickInput(
        boolean recipeAvailable,
        boolean enabled,
        ProcessResourceAvailability resourceAvailability,
        long storedEnergy
) {
    public ProcessTickInput {
        java.util.Objects.requireNonNull(resourceAvailability, "resourceAvailability");
        if (storedEnergy < 0) {
            throw new IllegalArgumentException("storedEnergy cannot be negative");
        }
    }
}
