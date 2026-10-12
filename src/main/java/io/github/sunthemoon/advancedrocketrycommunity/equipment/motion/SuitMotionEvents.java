package io.github.sunthemoon.advancedrocketrycommunity.equipment.motion;

import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import java.util.Optional;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingEquipmentChangeEvent;
import net.minecraftforge.event.entity.living.LivingFallEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStartingEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;

/** ADR-066 section 5.2 lifecycle: one service per running server, installed at start and cleared at stop. */
@Mod.EventBusSubscriber(modid = ModIdentity.MOD_ID)
public final class SuitMotionEvents {
    private static volatile SuitMotionService installed;

    public static Optional<SuitMotionService> service() { return Optional.ofNullable(installed); }

    @SubscribeEvent public static void starting(ServerStartingEvent event) {
        installed = new SuitMotionService(MotionNetwork::sendImpulse);
    }

    @SubscribeEvent public static void stopped(ServerStoppedEvent event) {
        SuitMotionService current = installed;
        installed = null;
        if (current != null) { current.clear(); }
    }

    @SubscribeEvent public static void tick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.START) { return; }
        SuitMotionService current = installed;
        if (current != null) { current.tick(event.getServer(), event.getServer().getTickCount()); }
        for (ServerPlayer player : event.getServer().getPlayerList().getPlayers()) { BionicLegs.update(player); }
    }

    @SubscribeEvent public static void loggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) { return; }
        service().ifPresent(current -> current.forget(player.getUUID()));
        BionicLegs.apply(player.getAttribute(Attributes.MOVEMENT_SPEED), false);
    }

    @SubscribeEvent public static void changedLevel(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) { service().ifPresent(current -> current.changedLevel(player)); }
    }

    @SubscribeEvent public static void respawned(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) { service().ifPresent(current -> current.respawned(player.getUUID())); }
    }

    /** Removes the bionic modifier at once when the leggings change, not only on the next tick. */
    @SubscribeEvent public static void equipment(LivingEquipmentChangeEvent event) {
        if (event.getSlot() == EquipmentSlot.LEGS && event.getEntity() instanceof ServerPlayer player
                && player.server.isSameThread()) {
            BionicLegs.update(player);
        }
    }

    @SubscribeEvent public static void fall(LivingFallEvent event) { PaddedBoots.onFall(event); }

    @Mod.EventBusSubscriber(modid = ModIdentity.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD)
    public static final class Setup {
        @SubscribeEvent public static void common(FMLCommonSetupEvent event) { event.enqueueWork(MotionNetwork::register); }
        private Setup() { }
    }

    private SuitMotionEvents() { }
}
