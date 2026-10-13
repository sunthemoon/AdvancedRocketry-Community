package io.github.sunthemoon.advancedrocketrycommunity.resourceescrow;

import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.entity.RocketEntity;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.event.entity.EntityMountEvent;
import net.minecraftforge.event.entity.EntityTeleportEvent;
import net.minecraftforge.event.entity.EntityTravelToDimensionEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Forge cancellation boundaries protect both a pending rider and a pending vehicle. */
@Mod.EventBusSubscriber(modid = ModIdentity.MOD_ID)
public final class RocketEscrowInterlocks {
    private RocketEscrowInterlocks() { }
    private static boolean protectedRocket(Entity entity) {
        return entity instanceof RocketEntity rocket && !entity.level().isClientSide && !RocketEscrowGate.idle(rocket);
    }
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void joined(net.minecraftforge.event.entity.EntityJoinLevelEvent event) {
        if (!event.getLevel().isClientSide && event.getEntity() instanceof RocketEntity rocket) {
            io.github.sunthemoon.advancedrocketrycommunity.rocket.server.RocketRuntime.onRocketJoined(rocket);
        }
    }
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void left(net.minecraftforge.event.entity.EntityLeaveLevelEvent event) {
        // Forge posts this on tracking loss as well as removal. A previously loaded proof is no longer current.
        if (!event.getLevel().isClientSide && event.getEntity() instanceof RocketEntity rocket) {
            io.github.sunthemoon.advancedrocketrycommunity.rocket.server.RocketRuntime.onRocketJoined(rocket);
        }
    }
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void mount(EntityMountEvent event) {
        if (protectedRocket(event.getEntityMounting()) || protectedRocket(event.getEntityBeingMounted())) { event.setCanceled(true); }
    }
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void teleport(EntityTeleportEvent.TeleportCommand event) {
        if (protectedRocket(event.getEntity())) { event.setCanceled(true); }
    }
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void spread(EntityTeleportEvent.SpreadPlayersCommand event) {
        if (protectedRocket(event.getEntity())) { event.setCanceled(true); }
    }
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void travel(EntityTravelToDimensionEvent event) {
        if (protectedRocket(event.getEntity())) { event.setCanceled(true); }
    }
}
