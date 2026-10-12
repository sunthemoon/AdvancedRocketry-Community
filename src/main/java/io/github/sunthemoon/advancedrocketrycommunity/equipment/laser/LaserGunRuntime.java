package io.github.sunthemoon.advancedrocketrycommunity.equipment.laser;

import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingEntityUseItemEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Static bridge only; the collection is owned by a service with an explicit server lifetime. */
@Mod.EventBusSubscriber(modid = ModIdentity.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class LaserGunRuntime {
    private static LaserGunService active;
    private LaserGunRuntime() { }
    static LaserGunService service() { return active; }

    @SubscribeEvent public static void started(ServerStartedEvent event) {
        if (active != null) { active.clear(); }
        active = new LaserGunService(event.getServer());
    }
    @SubscribeEvent public static void stopping(ServerStoppingEvent event) { clear(); }
    @SubscribeEvent public static void stopped(ServerStoppedEvent event) { clear(); }
    private static void clear() { if (active != null) { active.clear(); active = null; } }
    @SubscribeEvent public static void tick(TickEvent.ServerTickEvent event) {
        if (event.phase == TickEvent.Phase.END && active != null) { active.sweep(); }
    }
    @SubscribeEvent public static void logout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (active != null && event.getEntity() instanceof ServerPlayer player) { active.logout(player); }
    }
    @SubscribeEvent public static void stop(LivingEntityUseItemEvent.Stop event) {
        if (active != null && event.getEntity() instanceof ServerPlayer player) { active.stop(player); }
    }
    @SubscribeEvent public static void start(LivingEntityUseItemEvent.Start event) {
        if (active != null && event.getEntity() instanceof ServerPlayer player) { active.stop(player); }
    }
    @SubscribeEvent public static void finish(LivingEntityUseItemEvent.Finish event) {
        if (active != null && event.getEntity() instanceof ServerPlayer player) { active.stop(player); }
    }
}
