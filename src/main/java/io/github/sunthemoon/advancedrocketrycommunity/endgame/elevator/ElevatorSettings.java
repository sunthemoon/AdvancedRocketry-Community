package io.github.sunthemoon.advancedrocketrycommunity.endgame.elevator;

/**
 * ADR-059 sections 6 and 8: the energy percent applied to cargo (20,000 FE) and ride (50,000 FE) costs, 10..1,000 so
 * the largest ride cost fills the 500,000 FE buffer exactly, and the cargo launches the server starts per tick (≤ 4).
 * Rides commit at most one per tick, a fixed value. Read at each use.
 */
public record ElevatorSettings(int energyPercent, int launchesPerTick) {
    public static final int MAX_LAUNCHES_PER_TICK = 4;
    public static final ElevatorSettings DEFAULTS = new ElevatorSettings(100, MAX_LAUNCHES_PER_TICK);

    public ElevatorSettings {
        if (energyPercent < ElevatorRules.MIN_PERCENT || energyPercent > ElevatorRules.MAX_PERCENT
                || launchesPerTick < 1 || launchesPerTick > MAX_LAUNCHES_PER_TICK) {
            throw new IllegalArgumentException("Space elevator settings are outside their bounds");
        }
    }
}
