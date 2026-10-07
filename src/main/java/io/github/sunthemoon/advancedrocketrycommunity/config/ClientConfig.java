package io.github.sunthemoon.advancedrocketrycommunity.config;

import io.github.sunthemoon.advancedrocketrycommunity.client.LifeSupportHudLayout;
import io.github.sunthemoon.advancedrocketrycommunity.client.LifeSupportHudLayout.Anchor;
import io.github.sunthemoon.advancedrocketrycommunity.client.LifeSupportHudLayout.PanelSettings;
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

    public static final ForgeConfigSpec.ConfigValue<Number> HUD_ENVIRONMENT_X = hudOffset("environment.x", -6);
    public static final ForgeConfigSpec.ConfigValue<Number> HUD_ENVIRONMENT_Y = hudOffset("environment.y", 6);
    public static final ForgeConfigSpec.EnumValue<Anchor> HUD_ENVIRONMENT_ANCHOR_X = BUILDER
            .comment("Horizontal anchor for the environment panel: START, CENTER or END.")
            .defineEnum("hud.environment.anchorX", Anchor.END);
    public static final ForgeConfigSpec.EnumValue<Anchor> HUD_ENVIRONMENT_ANCHOR_Y = BUILDER
            .comment("Vertical anchor for the environment panel: START, CENTER or END.")
            .defineEnum("hud.environment.anchorY", Anchor.START);

    public static final ForgeConfigSpec.ConfigValue<Number> HUD_OXYGEN_X = hudOffset("oxygen.x", -6);
    public static final ForgeConfigSpec.ConfigValue<Number> HUD_OXYGEN_Y = hudOffset("oxygen.y", 30);
    public static final ForgeConfigSpec.EnumValue<Anchor> HUD_OXYGEN_ANCHOR_X = BUILDER
            .comment("Horizontal anchor for the oxygen panel: START, CENTER or END.")
            .defineEnum("hud.oxygen.anchorX", Anchor.END);
    public static final ForgeConfigSpec.EnumValue<Anchor> HUD_OXYGEN_ANCHOR_Y = BUILDER
            .comment("Vertical anchor for the oxygen panel: START, CENTER or END.")
            .defineEnum("hud.oxygen.anchorY", Anchor.START);

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

    public static PanelSettings environmentHudSettings() {
        return settings(HUD_ENVIRONMENT_X, HUD_ENVIRONMENT_Y,
                HUD_ENVIRONMENT_ANCHOR_X, HUD_ENVIRONMENT_ANCHOR_Y);
    }

    public static PanelSettings oxygenHudSettings() {
        return settings(HUD_OXYGEN_X, HUD_OXYGEN_Y, HUD_OXYGEN_ANCHOR_X, HUD_OXYGEN_ANCHOR_Y);
    }

    private static ForgeConfigSpec.ConfigValue<Number> hudOffset(String path, int defaultValue) {
        return BUILDER.comment("Signed offset in scaled GUI pixels, from -4096 to 4096 inclusive.")
                .<Number>define("hud." + path, defaultValue, ClientConfig::validHudOffset);
    }

    private static boolean validHudOffset(Object value) {
        if (!(value instanceof Byte || value instanceof Short || value instanceof Integer || value instanceof Long)) {
            return false;
        }
        long offset = ((Number) value).longValue();
        return offset >= LifeSupportHudLayout.MIN_OFFSET && offset <= LifeSupportHudLayout.MAX_OFFSET;
    }

    private static PanelSettings settings(
            ForgeConfigSpec.ConfigValue<Number> x,
            ForgeConfigSpec.ConfigValue<Number> y,
            ForgeConfigSpec.EnumValue<Anchor> anchorX,
            ForgeConfigSpec.EnumValue<Anchor> anchorY
    ) {
        if (!SPEC.isLoaded()) {
            return new PanelSettings(x.getDefault().intValue(), y.getDefault().intValue(),
                    anchorX.getDefault(), anchorY.getDefault());
        }
        return new PanelSettings(x.get().intValue(), y.get().intValue(), anchorX.get(), anchorY.get());
    }
}
