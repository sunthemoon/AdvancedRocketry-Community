package io.github.sunthemoon.advancedrocketrycommunity;

import com.mojang.logging.LogUtils;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.service.CelestialCatalogManager;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.command.CelestialCommands;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.network.CelestialNetwork;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.network.CelestialSnapshotSynchronizer;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.service.CelestialDefinitionReloadListener;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.service.CelestialEnvironmentService;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.service.CelestialGravityController;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.service.CelestialVisitTracker;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.service.SafeCelestialTravel;
import io.github.sunthemoon.advancedrocketrycommunity.config.CommonConfig;
import io.github.sunthemoon.advancedrocketrycommunity.atmosphere.command.AtmosphereCommands;
import io.github.sunthemoon.advancedrocketrycommunity.atmosphere.network.LifeSupportNetwork;
import io.github.sunthemoon.advancedrocketrycommunity.atmosphere.server.AtmosphereManager;
import io.github.sunthemoon.advancedrocketrycommunity.atmosphere.server.AtmosphereRuntime;
import io.github.sunthemoon.advancedrocketrycommunity.atmosphere.server.AtmosphereServerEvents;
import io.github.sunthemoon.advancedrocketrycommunity.atmosphere.server.PlayerLifeSupportService;
import io.github.sunthemoon.advancedrocketrycommunity.registry.ModRegistries;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.command.RocketCommands;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.server.RocketManager;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.server.RocketRuntime;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.network.RocketVisualNetwork;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.network.RocketVisualSynchronizer;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.network.RocketFlightNetwork;
import io.github.sunthemoon.advancedrocketrycommunity.station.service.StationManager;
import io.github.sunthemoon.advancedrocketrycommunity.station.service.StationRuntime;
import io.github.sunthemoon.advancedrocketrycommunity.station.command.StationCommands;
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
    private final AtmosphereManager atmosphereManager;
    private final PlayerLifeSupportService playerLifeSupport;
    private final RocketManager rocketManager;
    private final StationManager stationManager;

    public AdvancedRocketryCommunity(FMLJavaModLoadingContext context) {
        IEventBus modBus = context.getModEventBus();

        ModRegistries.register(modBus);
        context.registerConfig(ModConfig.Type.COMMON, CommonConfig.SPEC);
        modBus.addListener(this::onCommonSetup);
        MinecraftForge.EVENT_BUS.addListener(this::onAddReloadListeners);
        MinecraftForge.EVENT_BUS.addListener(this::onServerStopped);
        CelestialVisitTracker visitTracker = new CelestialVisitTracker(celestialCatalogs);
        MinecraftForge.EVENT_BUS.addListener(visitTracker::onPlayerLoggedIn);
        MinecraftForge.EVENT_BUS.addListener(visitTracker::onPlayerChangedDimension);
        CelestialEnvironmentService environments = new CelestialEnvironmentService(celestialCatalogs);
        atmosphereManager = new AtmosphereManager(environments);
        AtmosphereRuntime.install(atmosphereManager);
        stationManager = new StationManager();
        StationRuntime.install(stationManager);
        MinecraftForge.EVENT_BUS.addListener(stationManager::onServerStarted);
        MinecraftForge.EVENT_BUS.addListener(stationManager::onBlockBroken);
        MinecraftForge.EVENT_BUS.addListener(stationManager::onBlockPlaced);
        MinecraftForge.EVENT_BUS.addListener(new StationCommands(stationManager)::register);
        rocketManager = new RocketManager();
        RocketRuntime.install(rocketManager);
        new RocketFlightNetwork();
        MinecraftForge.EVENT_BUS.addListener(rocketManager::onServerTick);
        MinecraftForge.EVENT_BUS.addListener(rocketManager::onPlayerLoggedIn);
        MinecraftForge.EVENT_BUS.addListener(new RocketCommands(rocketManager)::register);
        RocketVisualNetwork rocketVisualNetwork = new RocketVisualNetwork();
        RocketVisualSynchronizer rocketVisualSynchronizer = new RocketVisualSynchronizer(rocketVisualNetwork);
        MinecraftForge.EVENT_BUS.addListener(rocketVisualSynchronizer::onStartTracking);
        LifeSupportNetwork lifeSupportNetwork = new LifeSupportNetwork();
        playerLifeSupport = new PlayerLifeSupportService(atmosphereManager, lifeSupportNetwork::send);
        AtmosphereServerEvents atmosphereEvents = new AtmosphereServerEvents(atmosphereManager);
        MinecraftForge.EVENT_BUS.addListener(atmosphereEvents::onServerTick);
        MinecraftForge.EVENT_BUS.addListener(atmosphereEvents::onBlockBroken);
        MinecraftForge.EVENT_BUS.addListener(atmosphereEvents::onBlockPlaced);
        MinecraftForge.EVENT_BUS.addListener(atmosphereEvents::onFluidPlaced);
        MinecraftForge.EVENT_BUS.addListener(atmosphereEvents::onRightClickBlock);
        MinecraftForge.EVENT_BUS.addListener(atmosphereEvents::onNeighborNotify);
        MinecraftForge.EVENT_BUS.addListener(atmosphereEvents::onChunkLoad);
        MinecraftForge.EVENT_BUS.addListener(atmosphereEvents::onChunkUnload);
        MinecraftForge.EVENT_BUS.addListener(playerLifeSupport::onLivingTick);
        MinecraftForge.EVENT_BUS.addListener(playerLifeSupport::onPlayerLoggedOut);
        MinecraftForge.EVENT_BUS.addListener(new AtmosphereCommands(atmosphereManager)::register);
        CelestialGravityController gravityController = new CelestialGravityController(environments);
        MinecraftForge.EVENT_BUS.addListener(gravityController::onLivingTick);
        CelestialCommands celestialCommands = new CelestialCommands(
                celestialCatalogs,
                new SafeCelestialTravel()
        );
        MinecraftForge.EVENT_BUS.addListener(celestialCommands::register);
        CelestialNetwork celestialNetwork = new CelestialNetwork();
        CelestialSnapshotSynchronizer snapshotSynchronizer = new CelestialSnapshotSynchronizer(
                celestialCatalogs,
                celestialNetwork
        );
        MinecraftForge.EVENT_BUS.addListener(snapshotSynchronizer::onDatapackSync);
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
        playerLifeSupport.clear();
        atmosphereManager.clear();
        rocketManager.clear();
        stationManager.clear();
        StationRuntime.clear();
        celestialCatalogs.clear();
    }
}
