package io.github.sunthemoon.advancedrocketrycommunity.rocket.rejoin;

import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.EntityMountEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.living.MobEffectEvent;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** The early entity-join hook precedes vanilla's RootVehicle remount. */
@Mod.EventBusSubscriber(modid = ModIdentity.MOD_ID)
public final class RocketRejoinProtection {
    private RocketRejoinProtection() { }
    public static boolean required(MinecraftServer server, java.util.UUID player) {
        if (server == null || server.overworld() == null || !server.isSameThread()) { return false; }
        var guards = RocketRejoinEvents.get(server).guards(server);
        return guards.required(player);
    }
    public static boolean guarded(net.minecraft.world.entity.Entity entity) {
        if (!(entity instanceof ServerPlayer player) || player.getServer() == null || !player.getServer().isSameThread()) { return false; }
        var data = RocketRejoinEvents.existing(player.getServer());
        return data != null && data.guards(player.getServer()).active(player);
    }
    private static RocketRejoinGuards guards(ServerPlayer player) {
        return RocketRejoinEvents.get(player.getServer()).guards(player.getServer());
    }
    @SubscribeEvent(priority = EventPriority.HIGHEST) public static void join(EntityJoinLevelEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) { guards(player).join(player); }
    }
    @SubscribeEvent(priority = EventPriority.HIGHEST) public static void login(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) { guards(player).join(player); }
    }
    @SubscribeEvent(priority = EventPriority.LOWEST) public static void logout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) { guards(player).logout(player); }
    }
    @SubscribeEvent(priority = EventPriority.LOWEST) public static void playerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase == TickEvent.Phase.END && event.player instanceof ServerPlayer player) { guards(player).pin(player); }
    }
    @SubscribeEvent(priority = EventPriority.LOWEST) public static void tick(TickEvent.ServerTickEvent event) {
        if (event.phase == TickEvent.Phase.END) {
            var data = RocketRejoinEvents.existing(event.getServer()); if (data != null) { data.guards(event.getServer()).tick(); }
        }
    }
    @SubscribeEvent(priority = EventPriority.HIGHEST) public static void attack(LivingAttackEvent event) {
        if (guarded(event.getEntity())) { event.setCanceled(true); }
    }
    @SubscribeEvent(priority = EventPriority.HIGHEST) public static void hurt(LivingHurtEvent event) {
        if (guarded(event.getEntity())) { event.setCanceled(true); }
    }
    @SubscribeEvent(priority = EventPriority.HIGHEST) public static void damage(LivingDamageEvent event) {
        if (guarded(event.getEntity())) { event.setCanceled(true); }
    }
    @SubscribeEvent(priority = EventPriority.HIGHEST) public static void effect(MobEffectEvent.Applicable event) {
        if (guarded(event.getEntity()) && event.getEffectInstance().getEffect().getCategory() == MobEffectCategory.HARMFUL) {
            event.setResult(Event.Result.DENY);
        }
    }
    @SubscribeEvent(priority = EventPriority.HIGHEST) public static void mount(EntityMountEvent event) {
        if (event.isMounting() && guarded(event.getEntityMounting())) { event.setCanceled(true); }
    }
    @SubscribeEvent(priority = EventPriority.HIGHEST) public static void interact(PlayerInteractEvent event) {
        if (event.isCancelable() && guarded(event.getEntity())) { event.setCanceled(true); }
    }
    @SubscribeEvent(priority = EventPriority.HIGHEST) public static void hit(AttackEntityEvent event) {
        if (guarded(event.getEntity())) { event.setCanceled(true); }
    }
    @SubscribeEvent(priority = EventPriority.HIGHEST) public static void breakBlock(BlockEvent.BreakEvent event) {
        if (guarded(event.getPlayer())) { event.setCanceled(true); }
    }
}
