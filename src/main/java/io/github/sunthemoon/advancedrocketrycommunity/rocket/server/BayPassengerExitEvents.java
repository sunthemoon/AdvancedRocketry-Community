package io.github.sunthemoon.advancedrocketrycommunity.rocket.server;

import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.entity.RocketEntity;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityMountEvent;
import net.minecraftforge.event.entity.EntityTeleportEvent;
import net.minecraftforge.event.entity.EntityTravelToDimensionEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingFallEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Forge observation only. No dismount cancellation, packet, persistent state or permission widening. */
@Mod.EventBusSubscriber(modid = ModIdentity.MOD_ID)
public final class BayPassengerExitEvents {
    private BayPassengerExitEvents() { }
    @SubscribeEvent(priority = EventPriority.LOWEST, receiveCanceled = true)
    public static void mount(EntityMountEvent event) {
        if (event.getEntityMounting() instanceof ServerPlayer player) {
            RocketRuntime.passengerExits().ifPresent(exits -> {
                if (event.isMounting()) { exits.revoke(player); }
                else if (!event.isCanceled() && event.getEntityBeingMounted() instanceof RocketEntity rocket) { exits.capture(player, rocket); }
            });
        }
    }
    @SubscribeEvent public static void playerTick(TickEvent.PlayerTickEvent event) {
        if (event.player instanceof ServerPlayer player) {
            // END sees vanilla's temporary travel step, before the connection restores its committed position.
            RocketRuntime.passengerExits().ifPresent(exits -> exits.sample(player, event.phase == TickEvent.Phase.START));
        }
    }
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void tick(TickEvent.ServerTickEvent event) {
        RocketRuntime.passengerExits().ifPresent(exits -> {
            if (event.phase == TickEvent.Phase.END) { exits.endTick(); } else { exits.beginTick(); }
        });
    }
    @SubscribeEvent(priority = EventPriority.LOWEST, receiveCanceled = true)
    public static void fall(LivingFallEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) { RocketRuntime.passengerExits().ifPresent(exits -> exits.fall(event, player)); }
    }
    @SubscribeEvent(priority = EventPriority.LOWEST, receiveCanceled = true)
    public static void attack(LivingAttackEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) { RocketRuntime.passengerExits().ifPresent(exits -> exits.attack(event, player)); }
    }
    @SubscribeEvent(priority = EventPriority.LOWEST, receiveCanceled = true)
    public static void teleport(EntityTeleportEvent event) { revoke(event.getEntity()); }
    @SubscribeEvent(priority = EventPriority.LOWEST, receiveCanceled = true)
    public static void travel(EntityTravelToDimensionEvent event) { revoke(event.getEntity()); }
    @SubscribeEvent public static void dimension(PlayerEvent.PlayerChangedDimensionEvent event) { revoke(event.getEntity()); }
    @SubscribeEvent public static void logout(PlayerEvent.PlayerLoggedOutEvent event) { revoke(event.getEntity()); }
    @SubscribeEvent(priority = EventPriority.LOWEST, receiveCanceled = true)
    public static void death(LivingDeathEvent event) { revoke(event.getEntity()); }
    @SubscribeEvent public static void clonePlayer(PlayerEvent.Clone event) { revoke(event.getOriginal()); revoke(event.getEntity()); }
    private static void revoke(net.minecraft.world.entity.Entity entity) {
        if (entity instanceof ServerPlayer player) { RocketRuntime.passengerExits().ifPresent(exits -> exits.revoke(player)); }
    }
}
