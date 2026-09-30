package io.github.sunthemoon.advancedrocketrycommunity.config;

import io.github.sunthemoon.advancedrocketrycommunity.atmosphere.AtmosphereLimits;
import io.github.sunthemoon.advancedrocketrycommunity.station.model.StationLimits;
import io.github.sunthemoon.advancedrocketrycommunity.station.warp.WarpSettings;
import net.minecraftforge.common.ForgeConfigSpec;

public final class CommonConfig {
    private static final ForgeConfigSpec.Builder BUILDER = new ForgeConfigSpec.Builder();

    public static final ForgeConfigSpec.IntValue MAX_ATMOSPHERE_VOLUME = BUILDER
            .comment(
                    "Maximum traversable cells in one sealed atmosphere volume.",
                    "Lower values reject large rooms sooner; values cannot exceed the hard safety limit."
            )
            .defineInRange(
                    "atmosphere.maxVolumeCells",
                    AtmosphereLimits.MAX_VOLUME_CELLS,
                    1,
                    AtmosphereLimits.MAX_VOLUME_CELLS
            );

    public static final ForgeConfigSpec.IntValue MAX_ATMOSPHERE_INSPECTIONS_PER_TICK = BUILDER
            .comment(
                    "Maximum atmosphere cell inspections per loaded Level per server tick.",
                    "Lower values reduce per-tick work but make room updates take longer."
            )
            .defineInRange(
                    "atmosphere.maxInspectionsPerLevelTick",
                    AtmosphereLimits.MAX_LEVEL_INSPECTIONS_PER_TICK,
                    1,
                    AtmosphereLimits.MAX_LEVEL_INSPECTIONS_PER_TICK
            );

    public static final ForgeConfigSpec.BooleanValue WARP_ENABLED = BUILDER
            .comment(
                    "Allow station warp requests, confirmations and commits (ADR-044).",
                    "Charging warp cores, stations and access are unaffected when disabled."
            )
            .define("stations.warpEnabled", true);

    public static final ForgeConfigSpec.IntValue WARP_COST_IN_SYSTEM = BUILDER
            .comment("Forge Energy a station warp costs within one star system.")
            .defineInRange(
                    "stations.warpCostInSystem",
                    WarpSettings.DEFAULTS.inSystemCost(),
                    StationLimits.MIN_WARP_COST,
                    StationLimits.MAX_WARP_COST
            );

    public static final ForgeConfigSpec.IntValue WARP_COST_INTERSTELLAR = BUILDER
            .comment(
                    "Forge Energy a station warp costs between star systems,",
                    "or when leaving an orbit body that is no longer available."
            )
            .defineInRange(
                    "stations.warpCostInterstellar",
                    WarpSettings.DEFAULTS.interstellarCost(),
                    StationLimits.MIN_WARP_COST,
                    StationLimits.MAX_WARP_COST
            );

    public static final ForgeConfigSpec SPEC = BUILDER.build();

    /** Current warp settings; defaults until the COMMON config is loaded. */
    public static WarpSettings warpSettings() {
        if (!SPEC.isLoaded()) {
            return WarpSettings.DEFAULTS;
        }
        return new WarpSettings(WARP_ENABLED.get(), WARP_COST_IN_SYSTEM.get(), WARP_COST_INTERSTELLAR.get());
    }

    private CommonConfig() {
    }
}
