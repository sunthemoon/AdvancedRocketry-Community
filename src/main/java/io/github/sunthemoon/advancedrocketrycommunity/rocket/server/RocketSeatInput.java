package io.github.sunthemoon.advancedrocketrycommunity.rocket.server;

import io.github.sunthemoon.advancedrocketrycommunity.rocket.entity.RocketEntity;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.common.util.FakePlayer;

/** Per-entity transient observations, bounded by the existing durable seat limit. Never serialized. */
public final class RocketSeatInput {
    static final int ABSENCE_GRACE_TICKS = 40;
    private final Map<UUID, Absence> absent = new HashMap<>();
    private boolean waitingForThaw;
    private record Absence(ServerPlayer player, long tick, int ticks) { }

    public void tick(RocketEntity rocket) {
        if (!rocket.operational() || !rocket.isAlive() || !RocketTransferEntities.loadedEntity(rocket)) {
            absent.clear(); return;
        }
        var flight = rocket.flightData().orElseThrow();
        if (!RocketFlightService.stationary(flight.state()) || flight.activeTransferId().isPresent()) {
            absent.clear(); return;
        }
        var server = ((ServerLevel) rocket.level()).getServer();
        var exits = RocketRuntime.passengerExits().orElse(null);
        long tick = server.getTickCount();
        // Assignment snapshots are immutable; removing a seat cannot invalidate this bounded iteration.
        var assignments = flight.passengers().assignments();
        absent.keySet().removeIf(id -> flight.passengers().assignment(id).isEmpty());
        Boolean frozen = null;
        for (var assignment : assignments) {
            UUID id = assignment.passengerId();
            var player = server.getPlayerList().getPlayer(id);
            if (player == null || !BayPassengerExitPermit.online(player) || player.hasDisconnected()
                    || player instanceof FakePlayer || BayPassengerExitPermit.guarded(player)
                    || exits != null && exits.state.internal(player) || RocketRuntime.seatReconnectPending(player)) {
                absent.remove(id); continue;
            }
            boolean riding = player.getVehicle() == rocket;
            boolean sneak = riding && player.level() == rocket.level() && player.isShiftKeyDown() && !player.isSpectator();
            if (riding) { absent.remove(id); }
            else {
                // F6/F7 may already look stationary before F8 releases the manifest. Only eligible ticks count.
                if (!RocketSeatRelease.mutationAllowed(rocket, id)) { absent.remove(id); continue; }
                var previous = absent.get(id);
                int ticks = previous != null && previous.player() == player && previous.tick() == tick - 1
                        ? Math.min(ABSENCE_GRACE_TICKS, previous.ticks() + 1) : 1;
                if (previous != null && previous.player() == player && previous.tick() == tick) { continue; }
                absent.put(id, new Absence(player, tick, ticks));
                if (ticks < ABSENCE_GRACE_TICKS) { continue; }
            }
            if (!sneak && riding) { continue; }
            // Expensive transaction-root inspection only once, immediately before the first attempted LEAVE.
            if (frozen == null) { frozen = RocketRuntime.sealedFrozen(rocket); }
            if (frozen) { waitingForThaw = true; return; }
            if (waitingForThaw) {
                // Retry the blocked release guard, but never count the frozen interval toward a fresh absence.
                waitingForThaw = false; absent.clear();
                if (!sneak) { return; }
            }
            try {
                var result = sneak ? RocketFlightService.leave(player, rocket) : RocketSeatRelease.release(rocket, id);
                if (result.success() && player.getVehicle() != rocket) {
                    if (sneak) { player.setShiftKeyDown(false); }
                    if (rocket.flightData().orElseThrow().passengers().assignment(id).isEmpty()) {
                        absent.remove(id);
                        RocketSeatRelease.released(rocket, id, sneak ? "SNEAK" : "ABSENT");
                    }
                }
            } catch (RuntimeException failure) {
                // Back off another grace period after a refusal instead of logging every tick.
                absent.remove(id);
                RocketSeatRelease.held(rocket, id, failure);
            }
        }
    }
}
