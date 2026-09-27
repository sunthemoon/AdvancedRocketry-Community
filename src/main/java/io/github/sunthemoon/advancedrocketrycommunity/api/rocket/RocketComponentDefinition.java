package io.github.sunthemoon.advancedrocketrycommunity.api.rocket;

/**
 * Immutable contribution of one block, identical for every state of that block.
 * Mass is 1..1,000,000, thrust 0..1,000,000, fuel capacity 0..2,048,000 in host
 * units. Nonzero thrust requires engine=true. Roles may be combined; registration
 * does not grant movement permission or guarantee a flyable aggregate structure.
 * Existing snapshots retain captured values, not a live reference to this record.
 */
public record RocketComponentDefinition(
        long mass,
        long thrust,
        long fuelCapacity,
        boolean engine,
        boolean seat,
        boolean guidance
) {
    public RocketComponentDefinition {
        if (mass < 1L || mass > 1_000_000L || thrust < 0L || thrust > 1_000_000L
                || fuelCapacity < 0L || fuelCapacity > 2_048_000L || !engine && thrust != 0L) {
            throw new IllegalArgumentException("Rocket component contribution is outside its fixed bounds");
        }
    }
}
