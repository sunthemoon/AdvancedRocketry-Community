package io.github.sunthemoon.advancedrocketrycommunity.endgame.railgun;

/**
 * ADR-056 sections 3 and 4: the energy percent applied to every launch cost (10..400, so the largest cost, 1,000,000
 * FE, still fits the buffer) and the launches the whole server starts in one tick (at most 4), read at each use.
 */
public record RailgunSettings(int energyPercent, int launchesPerTick) {
    public static final int MIN_PERCENT = 10;
    public static final int MAX_PERCENT = 400;
    public static final int MAX_LAUNCHES_PER_TICK = 4;
    public static final RailgunSettings DEFAULTS = new RailgunSettings(100, MAX_LAUNCHES_PER_TICK);

    public RailgunSettings {
        if (energyPercent < MIN_PERCENT || energyPercent > MAX_PERCENT || launchesPerTick < 1
                || launchesPerTick > MAX_LAUNCHES_PER_TICK) {
            throw new IllegalArgumentException("Railgun settings are outside their bounds");
        }
    }
}
