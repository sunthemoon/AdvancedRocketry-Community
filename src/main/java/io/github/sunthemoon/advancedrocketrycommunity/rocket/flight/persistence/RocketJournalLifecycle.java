package io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.persistence;

import io.github.sunthemoon.advancedrocketrycommunity.AdvancedRocketryCommunity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Publish IO results before gameplay, and join the writer before releasing world storage. */
@Mod.EventBusSubscriber(modid = AdvancedRocketryCommunity.MOD_ID)
public final class RocketJournalLifecycle {
    private RocketJournalLifecycle() { }
    private static RocketTransferSavedData existing(MinecraftServer server) {
        return RocketTransferSavedData.existing(server);
    }
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void tick(TickEvent.ServerTickEvent event) {
        if (event.phase == TickEvent.Phase.START) {
            var journal = existing(event.getServer());
            if (journal != null && journal.nativeProbe() != null) { journal.nativeProbe().tickStart(event.getServer().getTickCount()); }
            if (journal != null) { journal.pollWriter(event.getServer().getTickCount()); }
        }
    }
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void tickEnd(TickEvent.ServerTickEvent event) {
        if (!RocketJournalNativeProbe.REQUESTED) { return; }
        if (event.phase == TickEvent.Phase.END) {
            var journal = existing(event.getServer());
            if (journal != null && journal.nativeProbe() != null) { journal.nativeProbe().tickEnd(); }
        }
    }
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void stopping(ServerStoppingEvent event) {
        var journal = existing(event.getServer()); if (journal != null) { journal.stop(); }
        if (journal != null && journal.nativeProbe() != null) {
            try { journal.dumpNativeProbe(event.getServer()); }
            catch (java.io.IOException failed) { AdvancedRocketryCommunity.LOGGER.error("Cannot write native journal observations", failed); }
        }
    }
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void unload(LevelEvent.Unload event) {
        if (event.getLevel() instanceof ServerLevel level && level.dimension() == Level.OVERWORLD) {
            var journal = existing(level.getServer()); if (journal != null) { journal.abort(); }
        }
    }
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void stopped(ServerStoppedEvent event) {
        var journal = existing(event.getServer()); if (journal != null) { journal.abort(); }
    }
}
