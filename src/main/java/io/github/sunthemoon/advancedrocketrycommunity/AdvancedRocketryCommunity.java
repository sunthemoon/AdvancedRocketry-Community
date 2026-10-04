package io.github.sunthemoon.advancedrocketrycommunity;

import com.mojang.logging.LogUtils;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.service.CelestialCatalogManager;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.command.CelestialCommands;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.command.PlanetaryRouteCommands;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.network.CelestialNetwork;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.blackhole.BlackHoleDataReloadListener;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.laser.LaserDrillTableReloadListener;
import io.github.sunthemoon.advancedrocketrycommunity.station.orbit.StationSkyContextRuntime;
import io.github.sunthemoon.advancedrocketrycommunity.station.orbit.StationSkyContextService;
import io.github.sunthemoon.advancedrocketrycommunity.station.service.StationWriteBudget;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.network.CelestialSnapshotSynchronizer;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.data.PlanetaryCatalogManager;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.data.PlanetaryDefinitionReloadListener;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.service.CelestialEnvironmentService;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.service.CelestialGravityController;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.service.CelestialVisitTracker;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.service.SafeCelestialTravel;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.binding.PlanetaryBindingLifecycle;
import io.github.sunthemoon.advancedrocketrycommunity.config.ClientConfig;
import io.github.sunthemoon.advancedrocketrycommunity.config.CommonConfig;
import io.github.sunthemoon.advancedrocketrycommunity.diagnostics.BetaDiagnosticId;
import io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.BetaWorldMigrationEvents;
import io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.BetaDataCommands;
import io.github.sunthemoon.advancedrocketrycommunity.atmosphere.command.AtmosphereCommands;
import io.github.sunthemoon.advancedrocketrycommunity.atmosphere.instrument.AtmosphereAnalyzerLifecycle;
import io.github.sunthemoon.advancedrocketrycommunity.atmosphere.network.LifeSupportNetwork;
import io.github.sunthemoon.advancedrocketrycommunity.atmosphere.server.AtmosphereManager;
import io.github.sunthemoon.advancedrocketrycommunity.atmosphere.server.AtmosphereRuntime;
import io.github.sunthemoon.advancedrocketrycommunity.atmosphere.server.AtmosphereServerEvents;
import io.github.sunthemoon.advancedrocketrycommunity.atmosphere.server.PlayerLifeSupportService;
import io.github.sunthemoon.advancedrocketrycommunity.machine.multiblock.pattern.service.MultiblockPatternCatalogManager;
import io.github.sunthemoon.advancedrocketrycommunity.machine.multiblock.pattern.service.MultiblockPatternReloadListener;
import io.github.sunthemoon.advancedrocketrycommunity.machine.precision.PrecisionAssemblerManager;
import io.github.sunthemoon.advancedrocketrycommunity.machine.precision.PrecisionAssemblerCommands;
import io.github.sunthemoon.advancedrocketrycommunity.machine.precision.PrecisionAssemblerRuntime;
import io.github.sunthemoon.advancedrocketrycommunity.machine.precision.PrecisionAssemblerServerEvents;
import io.github.sunthemoon.advancedrocketrycommunity.machine.rolling.RollingMachineCommands;
import io.github.sunthemoon.advancedrocketrycommunity.machine.rolling.RollingMachineManager;
import io.github.sunthemoon.advancedrocketrycommunity.machine.rolling.RollingMachineRuntime;
import io.github.sunthemoon.advancedrocketrycommunity.machine.rolling.RollingMachineServerEvents;
import io.github.sunthemoon.advancedrocketrycommunity.registry.ModRegistries;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.command.RocketCommands;
import io.github.sunthemoon.advancedrocketrycommunity.api.rocket.RegisterRocketAdaptersEvent;
import io.github.sunthemoon.advancedrocketrycommunity.api.rocket.RegisterRocketComponentsEvent;
import io.github.sunthemoon.advancedrocketrycommunity.api.rocket.RegisterRocketFuelsEvent;
import io.github.sunthemoon.advancedrocketrycommunity.compat.rocket.fuel.RocketFuelRegistry;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.fuel.RocketFuelRuntime;
import io.github.sunthemoon.advancedrocketrycommunity.compat.rocket.component.RocketComponentCatalog;
import io.github.sunthemoon.advancedrocketrycommunity.compat.rocket.component.RocketComponentRegistry;
import io.github.sunthemoon.advancedrocketrycommunity.compat.rocket.RocketAdapterRegistry;
import io.github.sunthemoon.advancedrocketrycommunity.api.atmosphere.RegisterAtmosphereBoundariesEvent;
import io.github.sunthemoon.advancedrocketrycommunity.compat.atmosphere.AtmosphereBoundaryRegistry;
import io.github.sunthemoon.advancedrocketrycommunity.api.atmosphere.RegisterSuitEquipmentEvent;
import io.github.sunthemoon.advancedrocketrycommunity.compat.atmosphere.SuitEquipmentRegistry;
import io.github.sunthemoon.advancedrocketrycommunity.compat.atmosphere.SuitEquipmentService;
import io.github.sunthemoon.advancedrocketrycommunity.compat.environment.EnvironmentQueryLifecycle;
import io.github.sunthemoon.advancedrocketrycommunity.atmosphere.server.SuitEquipmentRuntime;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.server.RocketManager;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.server.RocketRuntime;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.network.RocketVisualNetwork;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.network.RocketVisualSynchronizer;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.network.RocketFlightNetwork;
import io.github.sunthemoon.advancedrocketrycommunity.station.service.StationManager;
import io.github.sunthemoon.advancedrocketrycommunity.station.model.StationState;
import io.github.sunthemoon.advancedrocketrycommunity.station.warp.StationRocketAuthority;
import io.github.sunthemoon.advancedrocketrycommunity.station.warp.StationWarpRuntime;
import io.github.sunthemoon.advancedrocketrycommunity.station.warp.StationWarpService;
import io.github.sunthemoon.advancedrocketrycommunity.station.service.StationRuntime;
import io.github.sunthemoon.advancedrocketrycommunity.station.command.StationCommands;
import io.github.sunthemoon.advancedrocketrycommunity.station.command.StationWarpCommands;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.command.SatelliteCommands;
import io.github.sunthemoon.advancedrocketrycommunity.api.satellite.RegisterSatellitePayloadsEvent;
import io.github.sunthemoon.advancedrocketrycommunity.compat.satellite.SatellitePayloadRegistry;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.service.SatellitePayloadRuntime;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.service.SatelliteCatalogManager;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.service.SatelliteComponentReloadListener;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.service.SatelliteDefinitionReloadListener;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.service.SatelliteManager;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.service.SatelliteRuntime;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.resource.ResourceTableReloadListener;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.network.SatelliteNetwork;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.AddReloadListenerEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.ModLoader;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;

@Mod(AdvancedRocketryCommunity.MOD_ID)
public final class AdvancedRocketryCommunity {
    public static final String MOD_ID = ModIdentity.MOD_ID;
    public static final Logger LOGGER = LogUtils.getLogger();

    private final PlanetaryCatalogManager planetaryCatalogs = new PlanetaryCatalogManager();
    private final CelestialCatalogManager celestialCatalogs = planetaryCatalogs.celestialView();
    private final SatelliteCatalogManager satelliteCatalogs = new SatelliteCatalogManager();
    private final SatelliteComponentReloadListener.Manager satelliteComponents =
            new SatelliteComponentReloadListener.Manager();
    private final ResourceTableReloadListener.Manager resourceTables = new ResourceTableReloadListener.Manager();
    private final LaserDrillTableReloadListener.Manager laserDrillTables = new LaserDrillTableReloadListener.Manager();
    private final BlackHoleDataReloadListener.Manager blackHoleData = new BlackHoleDataReloadListener.Manager();
    private final MultiblockPatternCatalogManager multiblockPatterns =
            new MultiblockPatternCatalogManager();
    private final RollingMachineManager rollingMachines = new RollingMachineManager(multiblockPatterns);
    private final PrecisionAssemblerManager precisionAssemblers = new PrecisionAssemblerManager(multiblockPatterns);
    private final CelestialEnvironmentService environments = new CelestialEnvironmentService(celestialCatalogs);
    private final LifeSupportNetwork lifeSupportNetwork;
    private AtmosphereManager atmosphereManager;
    private PlayerLifeSupportService playerLifeSupport;
    private RocketManager rocketManager;
    private final StationManager stationManager;
    private final StationWarpService stationWarp;
    private final StationWriteBudget stationWriteBudget;
    private final SatelliteManager satelliteManager;

    public AdvancedRocketryCommunity(FMLJavaModLoadingContext context) {
        IEventBus modBus = context.getModEventBus();

        ModRegistries.register(modBus);
        context.registerConfig(ModConfig.Type.COMMON, CommonConfig.SPEC);
        context.registerConfig(ModConfig.Type.CLIENT, ClientConfig.SPEC);
        modBus.addListener(this::onCommonSetup);
        MinecraftForge.EVENT_BUS.addListener(this::onAddReloadListeners);
        MinecraftForge.EVENT_BUS.addListener(this::onServerStopped);
        MinecraftForge.EVENT_BUS.addListener(new BetaWorldMigrationEvents()::onServerAboutToStart);
        MinecraftForge.EVENT_BUS.addListener(new PlanetaryBindingLifecycle(planetaryCatalogs)::onServerAboutToStart);
        MinecraftForge.EVENT_BUS.addListener(new BetaDataCommands()::register);
        RollingMachineRuntime.install(rollingMachines);
        RollingMachineServerEvents rollingEvents = new RollingMachineServerEvents(rollingMachines);
        MinecraftForge.EVENT_BUS.addListener(rollingEvents::onServerTick);
        MinecraftForge.EVENT_BUS.addListener(rollingEvents::onBlockBroken);
        MinecraftForge.EVENT_BUS.addListener(rollingEvents::onBlockPlaced);
        MinecraftForge.EVENT_BUS.addListener(rollingEvents::onNeighborNotify);
        MinecraftForge.EVENT_BUS.addListener(rollingEvents::onChunkLoad);
        MinecraftForge.EVENT_BUS.addListener(rollingEvents::onChunkUnload);
        MinecraftForge.EVENT_BUS.addListener(rollingEvents::onDatapackSync);
        MinecraftForge.EVENT_BUS.addListener(rollingEvents::onServerAboutToStart);
        MinecraftForge.EVENT_BUS.addListener(new RollingMachineCommands()::register);
        PrecisionAssemblerRuntime.install(precisionAssemblers);
        PrecisionAssemblerServerEvents precisionEvents = new PrecisionAssemblerServerEvents(precisionAssemblers);
        MinecraftForge.EVENT_BUS.addListener(precisionEvents::onServerTick);
        MinecraftForge.EVENT_BUS.addListener(precisionEvents::onBlockBroken);
        MinecraftForge.EVENT_BUS.addListener(precisionEvents::onBlockPlaced);
        MinecraftForge.EVENT_BUS.addListener(precisionEvents::onNeighborNotify);
        MinecraftForge.EVENT_BUS.addListener(precisionEvents::onChunkLoad);
        MinecraftForge.EVENT_BUS.addListener(precisionEvents::onChunkUnload);
        MinecraftForge.EVENT_BUS.addListener(precisionEvents::onDatapackSync);
        MinecraftForge.EVENT_BUS.addListener(precisionEvents::onServerAboutToStart);
        MinecraftForge.EVENT_BUS.addListener(new PrecisionAssemblerCommands()::register);
        CelestialVisitTracker visitTracker = new CelestialVisitTracker(celestialCatalogs);
        MinecraftForge.EVENT_BUS.addListener(visitTracker::onPlayerLoggedIn);
        MinecraftForge.EVENT_BUS.addListener(visitTracker::onPlayerChangedDimension);
        stationWriteBudget = new StationWriteBudget(CommonConfig::checkedWriteTicksPer100Stations);
        stationManager = new StationManager(celestialCatalogs, stationWriteBudget);
        StationRuntime.install(stationManager);
        stationWarp = new StationWarpService(celestialCatalogs,
                StationWarpService.routesInSystem(planetaryCatalogs), CommonConfig::warpSettings, stationWriteBudget);
        StationWarpRuntime.install(stationWarp);
        MinecraftForge.EVENT_BUS.addListener(stationWarp::onServerTick);
        MinecraftForge.EVENT_BUS.addListener(stationWarp::onServerStopping);
        MinecraftForge.EVENT_BUS.addListener(stationWarp::onPlayerLoggedOut);
        MinecraftForge.EVENT_BUS.addListener(stationManager::onServerAboutToStart);
        MinecraftForge.EVENT_BUS.addListener(stationManager::onServerStarted);
        EnvironmentQueryLifecycle environmentQueries = new EnvironmentQueryLifecycle(celestialCatalogs);
        MinecraftForge.EVENT_BUS.addListener(environmentQueries::onServerStarted);
        MinecraftForge.EVENT_BUS.addListener(EventPriority.HIGHEST, environmentQueries::onServerStopping);
        MinecraftForge.EVENT_BUS.addListener(EventPriority.HIGHEST, environmentQueries::onServerStopped);
        AtmosphereAnalyzerLifecycle analyzer = new AtmosphereAnalyzerLifecycle(celestialCatalogs, () -> atmosphereManager);
        MinecraftForge.EVENT_BUS.addListener(analyzer::onServerStarted);
        MinecraftForge.EVENT_BUS.addListener(EventPriority.HIGHEST, analyzer::onServerStopping);
        MinecraftForge.EVENT_BUS.addListener(EventPriority.HIGHEST, analyzer::onServerStopped);
        MinecraftForge.EVENT_BUS.addListener(stationManager::onBlockBroken);
        MinecraftForge.EVENT_BUS.addListener(stationManager::onBlockPlaced);
        MinecraftForge.EVENT_BUS.addListener(stationManager::onPlayerLoggedOut);
        MinecraftForge.EVENT_BUS.addListener(new StationCommands(stationManager)::register);
        MinecraftForge.EVENT_BUS.addListener(new StationWarpCommands(stationWarp)::register);
        new RocketFlightNetwork();
        RocketVisualNetwork rocketVisualNetwork = new RocketVisualNetwork();
        RocketVisualSynchronizer rocketVisualSynchronizer = new RocketVisualSynchronizer(rocketVisualNetwork);
        MinecraftForge.EVENT_BUS.addListener(rocketVisualSynchronizer::onStartTracking);
        lifeSupportNetwork = new LifeSupportNetwork();
        CelestialGravityController gravityController = new CelestialGravityController(environments,
                stationManager::effectiveGravity, player -> io.github.sunthemoon.advancedrocketrycommunity.endgame
                .service.EndgameRuntime.devices().map(devices -> devices.fieldGravity(player))
                .orElse(java.util.OptionalDouble.empty()));
        MinecraftForge.EVENT_BUS.addListener(gravityController::onLivingTick);
        CelestialCommands celestialCommands = new CelestialCommands(
                celestialCatalogs,
                new SafeCelestialTravel()
        );
        MinecraftForge.EVENT_BUS.addListener(celestialCommands::register);
        MinecraftForge.EVENT_BUS.addListener(new PlanetaryRouteCommands(planetaryCatalogs)::register);
        CelestialNetwork celestialNetwork = new CelestialNetwork();
        CelestialSnapshotSynchronizer snapshotSynchronizer = new CelestialSnapshotSynchronizer(
                celestialCatalogs,
                celestialNetwork
        );
        MinecraftForge.EVENT_BUS.addListener(snapshotSynchronizer::onDatapackSync);
        StationSkyContextService skyContexts = new StationSkyContextService(celestialNetwork::sendSkyContext);
        StationSkyContextRuntime.install(skyContexts);
        MinecraftForge.EVENT_BUS.addListener(skyContexts::onServerTick);
        MinecraftForge.EVENT_BUS.addListener(skyContexts::onPlayerChangedDimension);
        MinecraftForge.EVENT_BUS.addListener(skyContexts::onPlayerRespawn);
        MinecraftForge.EVENT_BUS.addListener(skyContexts::onPlayerLoggedOut);
        MinecraftForge.EVENT_BUS.addListener(skyContexts::onServerStopping);
        satelliteManager = new SatelliteManager(
                satelliteCatalogs,
                celestialCatalogs,
                snapshotSynchronizer
        );
        SatelliteRuntime.install(satelliteManager);
        SatelliteNetwork.install(new SatelliteNetwork());
        MinecraftForge.EVENT_BUS.addListener(satelliteManager::onServerStarted);
        MinecraftForge.EVENT_BUS.addListener(satelliteManager::onServerTick);
        MinecraftForge.EVENT_BUS.addListener(satelliteManager::onPlayerLoggedOut);
        MinecraftForge.EVENT_BUS.addListener(new SatelliteCommands(satelliteManager, resourceTables)::register);
        // ADR-051: resource missions, terminal delivery and the chunk-tag persistence signal.
        var resourceMissions = new io.github.sunthemoon.advancedrocketrycommunity.satellite.service
                .ResourceMissionService(satelliteCatalogs, celestialCatalogs, resourceTables);
        io.github.sunthemoon.advancedrocketrycommunity.satellite.service.ResourceMissionRuntime.install(resourceMissions);
        var terminalChunks = new io.github.sunthemoon.advancedrocketrycommunity.satellite.terminal.TerminalChunkEvents(
                ModIdentity.id("satellite_terminal").toString());
        MinecraftForge.EVENT_BUS.addListener(terminalChunks::onLoad);
        MinecraftForge.EVENT_BUS.addListener(terminalChunks::onSave);
        MinecraftForge.EVENT_BUS.addListener(new io.github.sunthemoon.advancedrocketrycommunity.satellite.command
                .ResourceMissionCommands(resourceMissions)::register);
        // C9 evidence hooks; they register nothing unless the release-test JVM flag is set.
        MinecraftForge.EVENT_BUS.addListener(new io.github.sunthemoon.advancedrocketrycommunity.satellite.command
                .ReleaseTestCommands()::register);
        // C15b evidence hooks (ADR-063 section 9, S1); registered only with the release-test JVM flag.
        MinecraftForge.EVENT_BUS.addListener(new io.github.sunthemoon.advancedrocketrycommunity.celestial.surface
                .SurfaceReleaseTestCommands()::register);
        MinecraftForge.EVENT_BUS.addListener(new io.github.sunthemoon.advancedrocketrycommunity.machine.combustion
                .CombustionReleaseTestCommands()::register);
        MinecraftForge.EVENT_BUS.addListener(new io.github.sunthemoon.advancedrocketrycommunity.machine.tank
                .TankReleaseTestCommands()::register);
        MinecraftForge.EVENT_BUS.addListener(new io.github.sunthemoon.advancedrocketrycommunity.machine.pump
                .PumpReleaseTestCommands()::register);
        MinecraftForge.EVENT_BUS.addListener(new io.github.sunthemoon.advancedrocketrycommunity.machine.recipe
                .RecipeSignatureReleaseTestCommands()::register);
        MinecraftForge.EVENT_BUS.addListener(new io.github.sunthemoon.advancedrocketrycommunity.fluid
                .FluidReleaseTestCommands()::register);
        // v1.7 (ADR-054): the endgame framework's root, observations, audit and commands.
        io.github.sunthemoon.advancedrocketrycommunity.endgame.EndgameModule.install(multiblockPatterns, celestialCatalogs,
                laserDrillTables, blackHoleData);
    }

    private void onCommonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(io.github.sunthemoon.advancedrocketrycommunity.machine.menu.MachineMenuNetwork::register);
        event.enqueueWork(this::initializeRocketAdapters);
        event.enqueueWork(this::initializeRocketFuels);
        event.enqueueWork(this::initializeSatellitePayloads);
        event.enqueueWork(this::initializeAtmosphereBoundaries);
        String version = ModList.get()
                .getModContainerById(MOD_ID)
                .map(container -> container.getModInfo().getVersion().toString())
                .orElse("unknown");
        LOGGER.info("{} {} initialized", ModIdentity.DISPLAY_NAME, version);
        String jeiVersion = ModList.get()
                .getModContainerById("jei")
                .map(container -> container.getModInfo().getVersion().toString())
                .orElse("absent");
        LOGGER.info(
                "{} optional_compat=jei status={} version={}",
                BetaDiagnosticId.OPTIONAL_COMPATIBILITY.code(),
                "absent".equals(jeiVersion) ? "absent" : "present",
                jeiVersion
        );
    }

    private void initializeRocketAdapters() {
        if (rocketManager != null) {
            throw new IllegalStateException("Rocket services were already initialized");
        }
        try (RocketAdapterRegistry registry = new RocketAdapterRegistry(
                ForgeRegistries.BLOCK_ENTITY_TYPES::containsKey)) {
            ModLoader.get().runEventGenerator(container -> new RegisterRocketAdaptersEvent(
                    registry.forOwner(container.getModId())));
            RocketManager manager = new RocketManager(registry.freeze(), celestialCatalogs, planetaryCatalogs,
                    initializeRocketComponents());
            RocketRuntime.install(manager);
            MinecraftForge.EVENT_BUS.addListener(manager::onServerTick);
            MinecraftForge.EVENT_BUS.addListener(manager::onPlayerLoggedIn);
            MinecraftForge.EVENT_BUS.addListener(manager::onPlayerLoggedOut);
            MinecraftForge.EVENT_BUS.addListener(new RocketCommands(manager)::register);
            rocketManager = manager;
            // ADR-044 §5: the rocket module's journal rule replaces the fail-closed default.
            stationWarp.installRocketAuthority(new StationRocketAuthority() {
                @Override
                public boolean inMotion(net.minecraft.server.MinecraftServer server, StationState station) {
                    return manager.stationRegionInMotion(server, station.region().minimumX(),
                            station.region().minimumZ(), station.region().maximumX(), station.region().maximumZ());
                }

                @Override
                public java.util.Optional<String> diagnostics(net.minecraft.server.MinecraftServer server) {
                    return java.util.Optional.of(manager.transferJournalDiagnostics(server));
                }
            });
        }
    }

    private RocketComponentCatalog initializeRocketComponents() {
        try (RocketComponentRegistry registry = new RocketComponentRegistry(id ->
                ForgeRegistries.BLOCKS.containsKey(id) ? ForgeRegistries.BLOCKS.getValue(id) : null)) {
            ModLoader.get().runEventGenerator(container -> new RegisterRocketComponentsEvent(
                    registry.forOwner(container.getModId())));
            return registry.freeze();
        }
    }

    private void initializeRocketFuels() {
        try (RocketFuelRegistry registry = new RocketFuelRegistry(id ->
                ForgeRegistries.ITEMS.containsKey(id) ? ForgeRegistries.ITEMS.getValue(id) : null)) {
            ModLoader.get().runEventGenerator(container -> new RegisterRocketFuelsEvent(
                    registry.forOwner(container.getModId())));
            RocketFuelRuntime.install(registry.freeze());
        }
    }

    private void onAddReloadListeners(AddReloadListenerEvent event) {
        event.addListener(new PlanetaryDefinitionReloadListener(planetaryCatalogs));
        // ADR-049 §2: components load before the definitions that name their primary component.
        event.addListener(new SatelliteComponentReloadListener(satelliteComponents));
        event.addListener(new SatelliteDefinitionReloadListener(satelliteCatalogs, celestialCatalogs, satelliteComponents));
        event.addListener(new ResourceTableReloadListener(resourceTables, celestialCatalogs));
        event.addListener(new LaserDrillTableReloadListener(laserDrillTables, celestialCatalogs));
        event.addListener(new BlackHoleDataReloadListener(blackHoleData));
        event.addListener(new MultiblockPatternReloadListener(multiblockPatterns));
    }

    private void initializeSatellitePayloads() {
        try (SatellitePayloadRegistry registry = new SatellitePayloadRegistry(id ->
                ForgeRegistries.ITEMS.containsKey(id) ? ForgeRegistries.ITEMS.getValue(id) : null)) {
            ModLoader.get().runEventGenerator(container -> new RegisterSatellitePayloadsEvent(
                    registry.forOwner(container.getModId())));
            SatellitePayloadRuntime.install(registry.freeze());
        }
    }

    private void initializeAtmosphereBoundaries() {
        if (atmosphereManager != null) {
            throw new IllegalStateException("Atmosphere services were already initialized");
        }
        try (AtmosphereBoundaryRegistry registry = new AtmosphereBoundaryRegistry(id ->
                ForgeRegistries.BLOCKS.containsKey(id) ? ForgeRegistries.BLOCKS.getValue(id) : null)) {
            ModLoader.get().runEventGenerator(container -> new RegisterAtmosphereBoundariesEvent(
                    registry.forOwner(container.getModId())));
            atmosphereManager = new AtmosphereManager(environments, registry.freeze());
            AtmosphereRuntime.install(atmosphereManager);
            SuitEquipmentService equipment = initializeSuitEquipment();
            SuitEquipmentRuntime.install(equipment);
            playerLifeSupport = new PlayerLifeSupportService(atmosphereManager, lifeSupportNetwork::send, equipment);
            AtmosphereServerEvents events = new AtmosphereServerEvents(atmosphereManager);
            MinecraftForge.EVENT_BUS.addListener(events::onServerTick);
            MinecraftForge.EVENT_BUS.addListener(events::onBlockBroken);
            MinecraftForge.EVENT_BUS.addListener(events::onBlockPlaced);
            MinecraftForge.EVENT_BUS.addListener(events::onFluidPlaced);
            MinecraftForge.EVENT_BUS.addListener(events::onRightClickBlock);
            MinecraftForge.EVENT_BUS.addListener(events::onNeighborNotify);
            MinecraftForge.EVENT_BUS.addListener(events::onChunkLoad);
            MinecraftForge.EVENT_BUS.addListener(events::onChunkUnload);
            MinecraftForge.EVENT_BUS.addListener(events::onDatapackSync);
            MinecraftForge.EVENT_BUS.addListener(playerLifeSupport::onLivingTick);
            MinecraftForge.EVENT_BUS.addListener(playerLifeSupport::onPlayerLoggedOut);
            MinecraftForge.EVENT_BUS.addListener(new AtmosphereCommands(atmosphereManager)::register);
        }
    }

    private SuitEquipmentService initializeSuitEquipment() {
        try (SuitEquipmentRegistry registry = new SuitEquipmentRegistry(id ->
                ForgeRegistries.ITEMS.containsKey(id) ? ForgeRegistries.ITEMS.getValue(id) : null)) {
            ModLoader.get().runEventGenerator(container -> new RegisterSuitEquipmentEvent(
                    registry.forOwner(container.getModId())));
            return new SuitEquipmentService(registry.freeze());
        }
    }

    private void onServerStopped(ServerStoppedEvent event) {
        if (playerLifeSupport != null) {
            playerLifeSupport.clear();
        }
        if (atmosphereManager != null) {
            atmosphereManager.clear();
        }
        if (rocketManager != null) {
            rocketManager.clear();
        }
        stationManager.clear();
        stationWarp.clear();
        stationWriteBudget.clear();
        satelliteManager.clear();
        rollingMachines.clear();
        RollingMachineRuntime.clear();
        precisionAssemblers.clear();
        PrecisionAssemblerRuntime.clear();
        multiblockPatterns.clear();
        StationRuntime.clear();
        SatelliteRuntime.clear();
        io.github.sunthemoon.advancedrocketrycommunity.satellite.service.ResourceMissionRuntime.clear();
        planetaryCatalogs.clear();
        satelliteComponents.clear();
        resourceTables.clear();
        laserDrillTables.clear();
        blackHoleData.clear();
    }
}
