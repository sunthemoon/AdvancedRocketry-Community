package io.github.sunthemoon.advancedrocketrycommunity.config;

import io.github.sunthemoon.advancedrocketrycommunity.atmosphere.AtmosphereLimits;
import net.minecraftforge.common.ForgeConfigSpec;

public final class CommonConfig {
    private static final ForgeConfigSpec.Builder BUILDER = new ForgeConfigSpec.Builder();

    public static final ForgeConfigSpec.BooleanValue LOG_LIFECYCLE_EVENTS = BUILDER
            .comment("Log bootstrap lifecycle events while the project is in pre-alpha development.")
            .define("logLifecycleEvents", true);

    public static final ForgeConfigSpec.IntValue MAX_ATMOSPHERE_VOLUME = BUILDER
            .comment("Maximum traversable cells in one sealed atmosphere volume.")
            .defineInRange(
                    "atmosphere.maxVolumeCells",
                    AtmosphereLimits.MAX_VOLUME_CELLS,
                    1,
                    AtmosphereLimits.MAX_VOLUME_CELLS
            );

    public static final ForgeConfigSpec.IntValue MAX_ATMOSPHERE_INSPECTIONS_PER_TICK = BUILDER
            .comment("Maximum atmosphere cell inspections per loaded Level per server tick.")
            .defineInRange(
                    "atmosphere.maxInspectionsPerLevelTick",
                    AtmosphereLimits.MAX_LEVEL_INSPECTIONS_PER_TICK,
                    1,
                    AtmosphereLimits.MAX_LEVEL_INSPECTIONS_PER_TICK
            );

    public static final ForgeConfigSpec SPEC = BUILDER.build();

    private CommonConfig() {
    }
}
