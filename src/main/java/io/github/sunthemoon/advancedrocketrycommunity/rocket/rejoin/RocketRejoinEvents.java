package io.github.sunthemoon.advancedrocketrycommunity.rocket.rejoin;

import io.github.sunthemoon.advancedrocketrycommunity.AdvancedRocketryCommunity;
import io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.ManagedRootSupplier;
import net.minecraft.server.MinecraftServer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStartingEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Pre-start migration validates first; install the cache before ServerStarted recovery or login. */
@Mod.EventBusSubscriber(modid = AdvancedRocketryCommunity.MOD_ID)
public final class RocketRejoinEvents {
    private RocketRejoinEvents() { }
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void starting(ServerStartingEvent event) {
        var server = event.getServer();
        var data = RocketRejoinSavedData.open(ManagedRootSupplier.dataFile(server, RocketRejoinSavedData.TYPE));
        server.overworld().getDataStorage().set(RocketRejoinSavedData.TYPE.dataName(), data);
    }
    @SubscribeEvent
    public static void tick(TickEvent.ServerTickEvent event) {
        if (event.phase == TickEvent.Phase.END) {
            var data = existing(event.getServer()); if (data != null) { data.tick(event.getServer().getTickCount()); }
        }
    }
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void stopping(ServerStoppingEvent event) {
        var data = existing(event.getServer()); if (data != null) { data.close(); }
    }
    public static RocketRejoinSavedData existing(MinecraftServer server) {
        return server.overworld() == null ? null : server.overworld().getDataStorage().get(RocketRejoinSavedData::load,
                RocketRejoinSavedData.TYPE.dataName());
    }
}
