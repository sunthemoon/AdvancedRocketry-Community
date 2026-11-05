package io.github.sunthemoon.advancedrocketrycommunity.celestial.service;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/** R328: one lifecycle-owned, connection-bound budget per online low-gravity player. */
public final class LowGravityFlightAllowance {
    private record Phase(ServerGamePacketListenerImpl connection, GravityFlightBudget budget) { }
    private final Map<UUID, Phase> phases = new HashMap<>();

    /** Living tick runs inside the connection tick, before vanilla increments its floating counter. */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void tick(LivingEvent.LivingTickEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || !player.server.isSameThread()) { return; }
        UUID id = player.getUUID();
        double factor = CelestialGravityController.appliedMultiplier(player);
        if (player instanceof FakePlayer || player.connection == null || player.hasDisconnected()
                || player.server.getPlayerList().getPlayer(id) != player || !player.isAlive()
                || player.onGround() || player.isCreative() || player.isSpectator() || player.isFallFlying()
                || player.getAbilities().mayfly || player.isPassenger() || player.server.isFlightAllowed()
                || GravityFlightBudget.allowance(factor) == GravityFlightBudget.VANILLA_TICKS
                || player.connection.aboveGroundTickCount <= 0) {
            phases.remove(id);
            return;
        }
        Phase phase = phases.get(id);
        if (phase == null || phase.connection() != player.connection) {
            phase = new Phase(player.connection, new GravityFlightBudget());
            phases.put(id, phase);
        }
        player.connection.aboveGroundTickCount = phase.budget().adjust(player.connection.aboveGroundTickCount, factor);
    }

    @SubscribeEvent public void loggedOut(PlayerEvent.PlayerLoggedOutEvent event) { phases.remove(event.getEntity().getUUID()); }
    @SubscribeEvent public void changedLevel(PlayerEvent.PlayerChangedDimensionEvent event) { phases.remove(event.getEntity().getUUID()); }
    @SubscribeEvent public void respawned(PlayerEvent.PlayerRespawnEvent event) { phases.remove(event.getEntity().getUUID()); }
    @SubscribeEvent public void stopped(ServerStoppedEvent event) { phases.clear(); }
}
