package io.github.sunthemoon.advancedrocketrycommunity.forcefield;

import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.lifecycle.FMLConstructModEvent;

/** SERVER configuration is stored per world/profile, without extending another slice's COMMON spec. */
@Mod.EventBusSubscriber(modid = ModIdentity.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class ForceFieldConfig {
    private static final ForgeConfigSpec.Builder BUILDER = new ForgeConfigSpec.Builder();
    public static final ForgeConfigSpec.BooleanValue ENABLED = BUILDER
            .comment("Allow owned redstone projectors to work. Registrations and orphan recovery remain available.")
            .define("forceField.enabled", true);
    public static final ForgeConfigSpec SPEC = BUILDER.build();
    @SubscribeEvent public static void construct(FMLConstructModEvent event) {
        ModLoadingContext.get().registerConfig(ModConfig.Type.SERVER, SPEC, "advancedrocketrycommunity-force-field.toml");
    }
    public static boolean enabled() { return SPEC.isLoaded() ? ENABLED.get() : ENABLED.getDefault(); }
    private ForceFieldConfig() { }
}
