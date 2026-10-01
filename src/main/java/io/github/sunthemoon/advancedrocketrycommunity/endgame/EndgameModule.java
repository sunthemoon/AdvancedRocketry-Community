package io.github.sunthemoon.advancedrocketrycommunity.endgame;

import io.github.sunthemoon.advancedrocketrycommunity.config.CommonConfig;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.command.EndgameCommands;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.service.EndgameRuntime;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.service.EndgameService;
import java.util.Set;
import net.minecraftforge.common.MinecraftForge;

/** Wires the v1.7 endgame framework (ADR-054) into the server lifecycle; the mod constructor calls it once. */
public final class EndgameModule {
    private EndgameModule() {
    }

    public static EndgameService install() {
        EndgameService service = new EndgameService(CommonConfig::endgameSettings, EndgameModule::endgameBlockEntityIds);
        EndgameRuntime.install(service);
        MinecraftForge.EVENT_BUS.addListener(service::onServerStarted);
        MinecraftForge.EVENT_BUS.addListener(service::onServerStopping);
        MinecraftForge.EVENT_BUS.addListener(service::onServerStopped);
        MinecraftForge.EVENT_BUS.addListener(service::onServerTick);
        MinecraftForge.EVENT_BUS.addListener(service::onChunkSave);
        MinecraftForge.EVENT_BUS.addListener(service::onChunkLoad);
        MinecraftForge.EVENT_BUS.addListener(new EndgameCommands(service)::register);
        return service;
    }

    /** The block entity type IDs of endgame devices, whose presence the chunk observations read (C11b onwards). */
    static Set<String> endgameBlockEntityIds() {
        return Set.of();
    }
}
