package io.github.sunthemoon.advancedrocketrycommunity.rocket.stats;

public record RocketBlockMetrics(
        long mass,
        long thrust,
        long fuelCapacity,
        boolean engine,
        boolean seat,
        boolean guidance
) {
    public RocketBlockMetrics {
        if (mass <= 0L) {
            throw new IllegalArgumentException("Rocket block mass must be positive");
        }
        if (thrust < 0L || fuelCapacity < 0L) {
            throw new IllegalArgumentException("Rocket block contribution must not be negative");
        }
        if (!engine && thrust != 0L) {
            throw new IllegalArgumentException("Only an engine may contribute thrust");
        }
    }

    public static RocketBlockMetrics structural(long mass) {
        return new RocketBlockMetrics(mass, 0L, 0L, false, false, false);
    }
}
