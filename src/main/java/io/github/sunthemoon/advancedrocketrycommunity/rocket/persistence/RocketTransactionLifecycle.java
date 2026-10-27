package io.github.sunthemoon.advancedrocketrycommunity.rocket.persistence;

import io.github.sunthemoon.advancedrocketrycommunity.AdvancedRocketryCommunity;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * ADR-074 items 2 and 10: the transaction journal's writer publishes at tick START, before any gameplay reads it; the
 * stop path latches at {@code ServerStoppingEvent}, drains at the shutdown save, and joins at the Overworld unload,
 * before world storage closes. {@code ServerStoppedEvent} is only the abort backstop.
 */
@Mod.EventBusSubscriber(modid = AdvancedRocketryCommunity.MOD_ID)
public final class RocketTransactionLifecycle {
    private RocketTransactionLifecycle() { }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void tick(TickEvent.ServerTickEvent event) {
        if (event.phase == TickEvent.Phase.START) {
            var journal = RocketTransactionSavedData.existing(event.getServer());
            if (journal != null) { journal.pollWriter(); }
            if (journal != null && journal.releaseTestCuts() != null) { journal.releaseTestCuts().onTickStart(journal); }
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void stopping(ServerStoppingEvent event) {
        var journal = RocketTransactionSavedData.existing(event.getServer());
        if (journal != null) { journal.latchShutdown(); }
        if (journal != null && journal.releaseTestCuts() != null) { journal.releaseTestCuts().onStopping(journal); }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void unload(LevelEvent.Unload event) {
        if (event.getLevel() instanceof ServerLevel level && level.dimension() == Level.OVERWORLD) {
            var journal = RocketTransactionSavedData.existing(level.getServer());
            if (journal != null) { journal.close(); }
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void stopped(ServerStoppedEvent event) {
        var journal = RocketTransactionSavedData.existing(event.getServer());
        if (journal != null) { journal.abort(); }
    }
}
