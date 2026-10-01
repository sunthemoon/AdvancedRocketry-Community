package io.github.sunthemoon.advancedrocketrycommunity.endgame;

import io.github.sunthemoon.advancedrocketrycommunity.celestial.service.CelestialCatalogManager;
import io.github.sunthemoon.advancedrocketrycommunity.config.CommonConfig;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.blackhole.BlackHoleDataReloadListener;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.command.EndgameCommands;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.device.EndgameDevices;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.gravity.GravityFieldCommands;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.laser.LaserDrillTableReloadListener;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.network.EndgameNetwork;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.service.EndgameRuntime;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.service.EndgameService;
import io.github.sunthemoon.advancedrocketrycommunity.machine.multiblock.pattern.service.MultiblockPatternCatalogManager;
import io.github.sunthemoon.advancedrocketrycommunity.registry.ModBlockEntities;
import java.util.Set;
import net.minecraftforge.common.MinecraftForge;

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
                CommonConfig::gravityFieldLimits, CommonConfig::blackHoleSettings, patterns, celestial, laserTables,
                blackHoleData);
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
        return service;
    }

    /** The block entity type IDs of endgame endpoints, whose presence the chunk observations read. */
    static Set<String> endgameBlockEntityIds() {
        return Set.of(ModBlockEntities.LASER_TARGET.getId().toString());
    }
}
