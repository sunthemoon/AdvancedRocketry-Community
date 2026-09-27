package io.github.sunthemoon.advancedrocketrycommunity.client;

import io.github.sunthemoon.advancedrocketrycommunity.AdvancedRocketryCommunity;
import io.github.sunthemoon.advancedrocketrycommunity.registry.ModItems;
import io.github.sunthemoon.advancedrocketrycommunity.registry.ModMenuTypes;
import io.github.sunthemoon.advancedrocketrycommunity.registry.ModEntities;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterColorHandlersEvent;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.client.event.RegisterClientReloadListenersEvent;
import net.minecraftforge.client.event.RegisterDimensionSpecialEffectsEvent;
import net.minecraft.client.renderer.DimensionSpecialEffects;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.visual.SkyProfiles;
import io.github.sunthemoon.advancedrocketrycommunity.client.sky.PlanetaryDimensionEffects;
import io.github.sunthemoon.advancedrocketrycommunity.client.sky.PlanetarySkyClient;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

@Mod.EventBusSubscriber(
        modid = AdvancedRocketryCommunity.MOD_ID,
        bus = Mod.EventBusSubscriber.Bus.MOD,
        value = Dist.CLIENT
)
public final class ClientBootstrap {
    private ClientBootstrap() {
    }

    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> MenuScreens.register(
                ModMenuTypes.ELECTROLYZER.get(),
                ElectrolyzerScreen::new
        ));
        event.enqueueWork(() -> MenuScreens.register(
                ModMenuTypes.ROLLING_MACHINE.get(),
                RollingMachineScreen::new
        ));
        event.enqueueWork(() -> MenuScreens.register(
                ModMenuTypes.PRECISION_ASSEMBLER.get(),
                PrecisionAssemblerScreen::new
        ));
        event.enqueueWork(() -> MenuScreens.register(
                ModMenuTypes.ROCKET_FLIGHT.get(),
                RocketFlightScreen::new
        ));
        event.enqueueWork(() -> MenuScreens.register(
                ModMenuTypes.SATELLITE_TERMINAL.get(),
                SatelliteTerminalScreen::new
        ));
        AdvancedRocketryCommunity.LOGGER.debug("Client bootstrap initialized");
    }

    @SubscribeEvent
    public static void onRegisterItemColors(RegisterColorHandlersEvent.Item event) {
        event.register(
                (stack, tintIndex) -> tintIndex == 0 ? 0xB7D5DD : 0xFFFFFF,
                ModItems.EMPTY_CANISTER.get()
        );
        event.register(
                (stack, tintIndex) -> tintIndex == 0 ? 0x71E5EE : 0xFFFFFF,
                ModItems.HYDROGEN_CANISTER.get()
        );
        event.register(
                (stack, tintIndex) -> tintIndex == 0 ? 0x79AFFF : 0xFFFFFF,
                ModItems.OXYGEN_CANISTER.get()
        );
    }

    @SubscribeEvent
    public static void onRegisterGuiOverlays(RegisterGuiOverlaysEvent event) {
        event.registerAboveAll("life_support", LifeSupportHud.OVERLAY);
    }

    @SubscribeEvent
    public static void onRegisterEntityRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(ModEntities.ROCKET.get(), RocketEntityRenderer::new);
    }

    @SubscribeEvent
    public static void onRegisterDimensionEffects(RegisterDimensionSpecialEffectsEvent event) {
        event.register(SkyProfiles.SURFACE_EFFECTS, new PlanetaryDimensionEffects(new DimensionSpecialEffects.OverworldEffects()));
        event.register(SkyProfiles.SPACE_EFFECTS, new PlanetaryDimensionEffects(new DimensionSpecialEffects.EndEffects()));
    }

    @SubscribeEvent
    public static void onRegisterReloadListeners(RegisterClientReloadListenersEvent event) {
        event.registerReloadListener(PlanetarySkyClient.RESOURCES);
    }
}
