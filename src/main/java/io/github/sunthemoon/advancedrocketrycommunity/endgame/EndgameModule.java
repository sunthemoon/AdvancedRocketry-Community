package io.github.sunthemoon.advancedrocketrycommunity.endgame;

import io.github.sunthemoon.advancedrocketrycommunity.celestial.service.CelestialCatalogManager;
import io.github.sunthemoon.advancedrocketrycommunity.config.CommonConfig;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.blackhole.BlackHoleDataReloadListener;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.command.ElevatorCommands;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.command.ElevatorReleaseTestCommands;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.command.EndgameCommands;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.command.EndgameReleaseTestCommands;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.command.ReferenceLoadReleaseTestCommands;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.command.TransitCommands;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.device.EndgameDevices;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.elevator.ElevatorGuard;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.elevator.ElevatorRedirectRule;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.gravity.GravityFieldCommands;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.laser.LaserDrillTableReloadListener;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.model.EndgameSystem;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.network.EndgameNetwork;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.railgun.RailgunRedirectRule;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.service.EndgameRuntime;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.service.EndgameService;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.service.EndgameTimings;
import io.github.sunthemoon.advancedrocketrycommunity.machine.multiblock.pattern.service.MultiblockPatternCatalogManager;
import io.github.sunthemoon.advancedrocketrycommunity.registry.ModBlockEntities;
import io.github.sunthemoon.advancedrocketrycommunity.station.elevator.ElevatorStationGuard;
import java.util.Set;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.EventPriority;

/** Wires the v1.7 endgame framework (ADR-054) into the server lifecycle; the mod constructor calls it once. */
public final class EndgameModule {
    private EndgameModule() {
    }

    public static EndgameService install(MultiblockPatternCatalogManager patterns, CelestialCatalogManager celestial,
                                         LaserDrillTableReloadListener.Manager laserTables,
                                         BlackHoleDataReloadListener.Manager blackHoleData) {
        EndgameNetwork.install(new EndgameNetwork());
        EndgameService service = new EndgameService(CommonConfig::endgameSettings, EndgameModule::endgameBlockEntityIds,
                CommonConfig::transitLimits);
        EndgameRuntime.install(service);
        EndgameDevices devices = new EndgameDevices(CommonConfig::endgameSettings, CommonConfig::laserDrillSettings,
                CommonConfig::gravityFieldLimits, CommonConfig::blackHoleSettings, CommonConfig::railgunSettings,
                CommonConfig::elevatorSettings, patterns, celestial, laserTables, blackHoleData);
        service.transitOperations().route(EndgameSystem.SPACE_ELEVATOR, new ElevatorRedirectRule(service,
                EndgameRuntime::devices));
        service.transitOperations().route(EndgameSystem.RAILGUN, new RailgunRedirectRule(service,
                EndgameRuntime::devices));
        // ADR-059 section 5: the station module reaches the pair set only through this port.
        ElevatorStationGuard.Installed.install(new ElevatorGuard(service));
        EndgameRuntime.installDevices(devices);
        MinecraftForge.EVENT_BUS.addListener(service::onServerStarted);
        MinecraftForge.EVENT_BUS.addListener(service::onServerStopping);
        MinecraftForge.EVENT_BUS.addListener(service::onServerStopped);
        MinecraftForge.EVENT_BUS.addListener(service::onServerTick);
        MinecraftForge.EVENT_BUS.addListener(service::onChunkSave);
        MinecraftForge.EVENT_BUS.addListener(service::onChunkLoad);
        MinecraftForge.EVENT_BUS.addListener(devices::onServerTick);
        MinecraftForge.EVENT_BUS.addListener(devices::onServerStopped);
        MinecraftForge.EVENT_BUS.addListener(devices::onPlayerLoggedOut);
        MinecraftForge.EVENT_BUS.addListener(devices::onBlockBroken);
        MinecraftForge.EVENT_BUS.addListener(devices::onBlockPlaced);
        MinecraftForge.EVENT_BUS.addListener(devices::onNeighborNotify);
        MinecraftForge.EVENT_BUS.addListener(devices::onLevelUnload);
        MinecraftForge.EVENT_BUS.addListener(new GravityFieldCommands()::register);
        MinecraftForge.EVENT_BUS.addListener(new EndgameCommands(service, devices)::register);
        MinecraftForge.EVENT_BUS.addListener(new TransitCommands(service)::register);
        MinecraftForge.EVENT_BUS.addListener(new ElevatorCommands(service, devices)::register);
        // C13 evidence hooks; they register nothing unless the release-test JVM flag is set.
        MinecraftForge.EVENT_BUS.addListener(new EndgameReleaseTestCommands(service)::register);
        MinecraftForge.EVENT_BUS.addListener(new ElevatorReleaseTestCommands(service)::register);
        MinecraftForge.EVENT_BUS.addListener(new ReferenceLoadReleaseTestCommands(service)::register);
        // ADR-059 section 8: rides tick after the ledger's END pass; their tickets go at server stop.
        MinecraftForge.EVENT_BUS.addListener((TickEvent.ServerTickEvent event) -> {
            if (event.phase == TickEvent.Phase.END && service.operational()) {
                long start = System.nanoTime();
                devices.elevatorRides().tick(event.getServer(), service, devices);
                service.timings().add(EndgameTimings.Place.RIDES, System.nanoTime() - start);
            }
        });
        // ADR-054 section 7: the measured tick closes after every endgame END handler.
        MinecraftForge.EVENT_BUS.addListener(EventPriority.LOWEST, (TickEvent.ServerTickEvent event) -> {
            if (event.phase == TickEvent.Phase.END) {
                service.timings().endTick();
            }
        });
        MinecraftForge.EVENT_BUS.addListener((ServerStoppingEvent event) ->
                devices.elevatorRides().clear(event.getServer()));
        return service;
    }

    /**
     * The block entity type IDs of endgame endpoints, whose presence the chunk observations read; the destruction
     * GameTests check every block of these types (ADR-054 section 9.1).
     */
    public static Set<String> endgameBlockEntityIds() {
        return Set.of(ModBlockEntities.LASER_TARGET.getId().toString(), ModBlockEntities.RAILGUN.getId().toString(),
                ModBlockEntities.ELEVATOR_ANCHOR.getId().toString(), ModBlockEntities.ELEVATOR_TERMINAL.getId().toString());
    }
}
