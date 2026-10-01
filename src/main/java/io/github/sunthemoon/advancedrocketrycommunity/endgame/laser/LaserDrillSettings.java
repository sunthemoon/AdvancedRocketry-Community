package io.github.sunthemoon.advancedrocketrycommunity.endgame.laser;

/**
 * ADR-055 section 4 budgets and limits, read at each use. The ranges are fixed; the counts and per-tick caps can only
 * be lowered below their maxima.
 */
public record LaserDrillSettings(int energyPercent, int operationIntervalTicks, int maxDepth, int activeGlobal,
                                 int activePerOwner, int logicalOperationsPerTick, int layersPerTick) {
    public static final int BASE_COST_FE = 10_000;
    public static final int MIN_ENERGY_PERCENT = 10;
    public static final int MAX_ENERGY_PERCENT = 1_000;
    public static final int MIN_INTERVAL_TICKS = 20;
    public static final int MAX_INTERVAL_TICKS = 1_200;
    public static final int MAX_DEPTH = 256;
    public static final int MAX_ACTIVE_GLOBAL = 32;
    public static final int MAX_ACTIVE_PER_OWNER = 4;
    public static final int MAX_LOGICAL_OPERATIONS_PER_TICK = 32;
    public static final int MAX_LAYERS_PER_TICK = 7;

    public static final LaserDrillSettings DEFAULTS = new LaserDrillSettings(100, MIN_INTERVAL_TICKS, 64,
            MAX_ACTIVE_GLOBAL, MAX_ACTIVE_PER_OWNER, MAX_LOGICAL_OPERATIONS_PER_TICK, MAX_LAYERS_PER_TICK);

    public LaserDrillSettings {
        bounded(energyPercent, MIN_ENERGY_PERCENT, MAX_ENERGY_PERCENT, "energy percent");
        bounded(operationIntervalTicks, MIN_INTERVAL_TICKS, MAX_INTERVAL_TICKS, "operation interval");
        bounded(maxDepth, 1, MAX_DEPTH, "maximum depth");
        bounded(activeGlobal, 1, MAX_ACTIVE_GLOBAL, "active drills");
        bounded(activePerOwner, 1, MAX_ACTIVE_PER_OWNER, "active drills per owner");
        bounded(logicalOperationsPerTick, 1, MAX_LOGICAL_OPERATIONS_PER_TICK, "logical operations per tick");
        bounded(layersPerTick, 1, MAX_LAYERS_PER_TICK, "layers per tick");
    }

    /** {@code cost = 10,000 × energyPercent / 100} FE per operation (legacy 10,000). */
    public int costFe() {
        return BASE_COST_FE * energyPercent / 100;
    }

    private static void bounded(int value, int min, int max, String name) {
        if (value < min || value > max) {
            throw new IllegalArgumentException("Laser drill " + name + " is outside " + min + ".." + max);
        }
    }
}
