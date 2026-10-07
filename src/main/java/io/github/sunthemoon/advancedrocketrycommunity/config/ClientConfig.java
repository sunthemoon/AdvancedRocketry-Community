package io.github.sunthemoon.advancedrocketrycommunity.config;

import net.minecraftforge.common.ForgeConfigSpec;

/**
 * CLIENT settings: each player's own effects, never read by a server and never sent over the network (ADR-063
 * section 6; ADR-066 section 7.2).
 */
public final class ClientConfig {
    private static final ForgeConfigSpec.Builder BUILDER = new ForgeConfigSpec.Builder();

    public static final ForgeConfigSpec.BooleanValue ELECTRIC_MUSHROOM_FLASHES = BUILDER
            .comment("Flash the sky and play distant thunder near electric mushrooms during rain in a stormland",
                    "(ADR-063 section 6). An effect on this client only: nothing is struck or set on fire.")
            .define("effects.electricMushroomFlashes", true);

    public static final ForgeConfigSpec.BooleanValue SKY_PLANET_OVERRIDE = BUILDER
            .comment("Render the planetary sky and fog on this client. Disable to use the dimension fallback.")
            .define("sky.planetOverride", true);

    public static final ForgeConfigSpec.BooleanValue SKY_STATION_OVERRIDE = BUILDER
            .comment("Render the station sky and fog on this client. Disable to use the dimension fallback.")
            .define("sky.stationOverride", true);

    public static final ForgeConfigSpec SPEC = BUILDER.build();

    private ClientConfig() {
    }

    /** Whether electric mushrooms flash the sky; the default until the CLIENT config is loaded. */
    public static boolean electricMushroomFlashes() {
        return SPEC.isLoaded() ? ELECTRIC_MUSHROOM_FLASHES.get() : ELECTRIC_MUSHROOM_FLASHES.getDefault();
    }

    public static boolean planetSkyOverride() {
        return SPEC.isLoaded() ? SKY_PLANET_OVERRIDE.get() : SKY_PLANET_OVERRIDE.getDefault();
    }

    public static boolean stationSkyOverride() {
        return SPEC.isLoaded() ? SKY_STATION_OVERRIDE.get() : SKY_STATION_OVERRIDE.getDefault();
    }
}
