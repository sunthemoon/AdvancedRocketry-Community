package io.github.sunthemoon.advancedrocketrycommunity;

import com.mojang.logging.LogUtils;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.service.CelestialCatalogManager;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.service.CelestialDefinitionReloadListener;
import io.github.sunthemoon.advancedrocketrycommunity.config.CommonConfig;
import io.github.sunthemoon.advancedrocketrycommunity.registry.ModRegistries;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.AddReloadListenerEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;

@Mod(AdvancedRocketryCommunity.MOD_ID)
public final class AdvancedRocketryCommunity {
    public static final String MOD_ID = ModIdentity.MOD_ID;
    public static final Logger LOGGER = LogUtils.getLogger();

    private final CelestialCatalogManager celestialCatalogs = new CelestialCatalogManager();

    public AdvancedRocketryCommunity(FMLJavaModLoadingContext context) {
        IEventBus modBus = context.getModEventBus();

        ModRegistries.register(modBus);
        context.registerConfig(ModConfig.Type.COMMON, CommonConfig.SPEC);
        modBus.addListener(this::onCommonSetup);
        MinecraftForge.EVENT_BUS.addListener(this::onAddReloadListeners);
        MinecraftForge.EVENT_BUS.addListener(this::onServerStopped);
    }

    private void onCommonSetup(FMLCommonSetupEvent event) {
        String version = ModList.get()
                .getModContainerById(MOD_ID)
                .map(container -> container.getModInfo().getVersion().toString())
                .orElse("unknown");
        LOGGER.info("{} {} initialized", ModIdentity.DISPLAY_NAME, version);
    }

    private void onAddReloadListeners(AddReloadListenerEvent event) {
        event.addListener(new CelestialDefinitionReloadListener(celestialCatalogs));
    }

    private void onServerStopped(ServerStoppedEvent event) {
        celestialCatalogs.clear();
    }
}
