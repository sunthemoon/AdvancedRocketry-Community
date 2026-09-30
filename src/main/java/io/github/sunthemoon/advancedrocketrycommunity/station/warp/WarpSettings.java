package io.github.sunthemoon.advancedrocketrycommunity.station.warp;

import io.github.sunthemoon.advancedrocketrycommunity.station.model.StationLimits;

/** ADR-044 COMMON settings captured when a warp is quoted and rechecked at commit. */
public record WarpSettings(boolean enabled, int inSystemCost, int interstellarCost) {
    public static final WarpSettings DEFAULTS = new WarpSettings(true, 2_000_000, 8_000_000);

    public WarpSettings {
        requireCost(inSystemCost);
        requireCost(interstellarCost);
    }

    public int cost(WarpCostClass costClass) {
        return switch (costClass) {
            case IN_SYSTEM -> inSystemCost;
            case INTERSTELLAR -> interstellarCost;
        };
    }

    private static void requireCost(int cost) {
        if (cost < StationLimits.MIN_WARP_COST || cost > StationLimits.MAX_WARP_COST) {
            throw new IllegalArgumentException("Warp cost is outside its configured bound");
        }
    }
}
