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

/** Pre-start validation plus lazy first-join classification; ServerStarting must not replace an earlier owner. */
@Mod.EventBusSubscriber(modid = AdvancedRocketryCommunity.MOD_ID)
public final class RocketRejoinEvents {
    private RocketRejoinEvents() { }
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void starting(ServerStartingEvent event) {
        get(event.getServer());
    }
    @SubscribeEvent
    public static void tick(TickEvent.ServerTickEvent event) {
        if (event.phase == TickEvent.Phase.END) {
            var data = existing(event.getServer()); if (data != null) { data.tick(event.getServer().getTickCount()); }
        }
    }
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void stopping(ServerStoppingEvent event) {
        io.github.sunthemoon.advancedrocketrycommunity.rocket.server.RocketRuntime.closeReleasesForStop();
        var data = existing(event.getServer()); if (data != null) { data.close(); }
    }
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void mounting(net.minecraftforge.event.entity.EntityMountEvent event) {
        if (event.isMounting() && event.getEntityBeingMounted() instanceof io.github.sunthemoon.advancedrocketrycommunity.rocket.entity.RocketEntity rocket
                && io.github.sunthemoon.advancedrocketrycommunity.rocket.server.RocketRuntime.releaseFenced(rocket)) { event.setCanceled(true); }
    }
    public static RocketRejoinSavedData get(MinecraftServer server) {
        if (!server.isSameThread() || server.overworld() == null) { throw new IllegalStateException("Rejoin classification needs the owning Level"); }
        var data = existing(server);
        if (data == null) {
            data = open(server);
            server.overworld().getDataStorage().set(RocketRejoinSavedData.TYPE.dataName(), data);
        }
        return data;
    }
    public static RocketRejoinSavedData existing(MinecraftServer server) {
        return server.overworld() == null ? null : server.overworld().getDataStorage().get(
                tag -> open(server),
                RocketRejoinSavedData.TYPE.dataName());
    }
    private static RocketRejoinSavedData open(MinecraftServer server) {
        return RocketRejoinSavedData.open(ManagedRootSupplier.dataFile(server, RocketRejoinSavedData.TYPE),
                RocketReleaseNativeCuts.policy(server, RocketRejoinSavedData.TYPE),
                io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.CheckedSavedDataFile::presence,
                io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.CheckedSavedDataPolicy.DirectoryForce.PLATFORM,
                RocketRejoinNativeCuts::beforeProof);
    }
}
