package io.github.sunthemoon.advancedrocketrycommunity.rocket.stats;

public record RocketStats(
        int blockCount,
        long mass,
        long thrust,
        long fuelCapacity,
        int engineCount,
        int seatCount,
        int guidanceCount,
        int blockEntityCount
) {
    public RocketStats {
        if (blockCount <= 0 || mass <= 0L) {
            throw new IllegalArgumentException("Rocket stats require blocks with positive mass");
        }
        if (thrust < 0L || fuelCapacity < 0L
                || engineCount < 0 || seatCount < 0
                || guidanceCount < 0 || blockEntityCount < 0) {
            throw new IllegalArgumentException("Rocket stats must not be negative");
        }
        if (engineCount > blockCount || seatCount > blockCount
                || guidanceCount > blockCount || blockEntityCount > blockCount) {
            throw new IllegalArgumentException("Rocket stat counts exceed the block count");
        }
    }

    public boolean hasFlightComponents() {
        return engineCount > 0 && seatCount > 0 && guidanceCount > 0;
    }

    public boolean hasSufficientThrust() {
        return thrust >= mass;
    }
}
