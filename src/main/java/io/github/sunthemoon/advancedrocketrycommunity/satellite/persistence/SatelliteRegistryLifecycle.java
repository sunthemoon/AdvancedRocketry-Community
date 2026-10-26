package io.github.sunthemoon.advancedrocketrycommunity.satellite.persistence;

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
 * ADR-074 items 2 and 10, as the transaction journal's lifecycle: the satellite registry's writer publishes at tick
 * START, before any gameplay reads it; the stop path latches at {@code ServerStoppingEvent}, drains at the shutdown
 * save and joins at the Overworld unload, before world storage closes. {@code ServerStoppedEvent} only aborts.
 */
@Mod.EventBusSubscriber(modid = AdvancedRocketryCommunity.MOD_ID)
public final class SatelliteRegistryLifecycle {
    private SatelliteRegistryLifecycle() { }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void tick(TickEvent.ServerTickEvent event) {
        if (event.phase == TickEvent.Phase.START) {
            var registry = SatelliteMissionSavedData.existing(event.getServer());
            if (registry != null) { registry.pollWriter(); }
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void stopping(ServerStoppingEvent event) {
        var registry = SatelliteMissionSavedData.existing(event.getServer());
        if (registry != null) { registry.latchShutdown(); }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void unload(LevelEvent.Unload event) {
        if (event.getLevel() instanceof ServerLevel level && level.dimension() == Level.OVERWORLD) {
            var registry = SatelliteMissionSavedData.existing(level.getServer());
            if (registry != null) { registry.close(); }
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void stopped(ServerStoppedEvent event) {
        var registry = SatelliteMissionSavedData.existing(event.getServer());
        if (registry != null) { registry.abort(); }
    }
}
