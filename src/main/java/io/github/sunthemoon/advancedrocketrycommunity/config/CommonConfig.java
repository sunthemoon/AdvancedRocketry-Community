package io.github.sunthemoon.advancedrocketrycommunity.config;

import net.minecraftforge.common.ForgeConfigSpec;

public final class CommonConfig {
    private static final ForgeConfigSpec.Builder BUILDER = new ForgeConfigSpec.Builder();

    public static final ForgeConfigSpec.BooleanValue LOG_LIFECYCLE_EVENTS = BUILDER
            .comment("Log bootstrap lifecycle events while the project is in pre-alpha development.")
            .define("logLifecycleEvents", true);

    public static final ForgeConfigSpec SPEC = BUILDER.build();

    private CommonConfig() {
    }
}
