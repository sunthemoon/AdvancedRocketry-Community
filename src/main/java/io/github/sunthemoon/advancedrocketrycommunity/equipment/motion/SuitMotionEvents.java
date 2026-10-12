package io.github.sunthemoon.advancedrocketrycommunity.equipment.motion;

import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import io.github.sunthemoon.advancedrocketrycommunity.config.CommonConfig;
import java.util.Optional;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.MinecraftServer;
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
    private static boolean equipmentEnabled;

    public static Optional<SuitMotionService> service() { return Optional.ofNullable(installed); }

    @SubscribeEvent public static void starting(ServerStartingEvent event) {
        installed = new SuitMotionService(MotionNetwork::sendImpulse);
        equipmentEnabled = CommonConfig.classicEquipmentEnabled();
    }

    @SubscribeEvent public static void stopped(ServerStoppedEvent event) {
        SuitMotionService current = installed;
        installed = null;
        equipmentEnabled = false;
        if (current != null) { current.clear(); }
    }

    @SubscribeEvent public static void tick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.START) { return; }
        syncEquipment(event.getServer());
        SuitMotionService current = installed;
        if (current != null) { current.tick(event.getServer(), event.getServer().getTickCount()); }
        for (ServerPlayer player : event.getServer().getPlayerList().getPlayers()) { BionicLegs.update(player); }
    }

    /** All online clients receive a change; login always receives the actual server value, even if disabled. */
    static void syncEquipment(MinecraftServer server) {
        if (installed == null) { return; }
        boolean enabled = CommonConfig.classicEquipmentEnabled();
        if (equipmentEnabled == enabled) { return; }
        equipmentEnabled = enabled;
        for (ServerPlayer player : server.getPlayerList().getPlayers()) { MotionNetwork.sendEquipmentEnabled(player, enabled); }
    }

    @SubscribeEvent public static void loggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            MotionNetwork.sendEquipmentEnabled(player, CommonConfig.classicEquipmentEnabled());
        }
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

    /** Replacements end the motion intent; in-place hydrogen debits keep the same worn-stack identity. */
    @SubscribeEvent public static void equipment(LivingEquipmentChangeEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && player.server.isSameThread()) {
            if (event.getSlot() == EquipmentSlot.LEGS) { BionicLegs.update(player); }
            if (event.getSlot() == EquipmentSlot.HEAD || event.getSlot() == EquipmentSlot.CHEST) {
                service().ifPresent(current -> current.equipmentChanged(player));
            }
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
