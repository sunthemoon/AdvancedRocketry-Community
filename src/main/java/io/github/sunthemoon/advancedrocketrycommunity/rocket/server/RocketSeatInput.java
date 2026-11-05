package io.github.sunthemoon.advancedrocketrycommunity.rocket.server;

import io.github.sunthemoon.advancedrocketrycommunity.AdvancedRocketryCommunity;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.entity.RocketEntity;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFlightState;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.common.util.FakePlayer;

/** Native player input, not arbitrary lifecycle detaches, authorizes a ground LEAVE. */
final class RocketSeatInput {
    private RocketSeatInput() { }

    static void dismountSneakingPassengers(RocketEntity rocket) {
        if (!rocket.operational() || !rocket.isAlive() || !RocketTransferEntities.loadedEntity(rocket)
                || RocketRuntime.sealedFrozen(rocket)) { return; }
        var flight = rocket.flightData().orElseThrow();
        if (!stationary(flight.state()) || flight.activeTransferId().isPresent()) { return; }
        var exits = RocketRuntime.passengerExits().orElse(null);
        // The actual passenger list is immutable during iteration and bounded by the existing seat limit.
        for (var passenger : rocket.getPassengers()) {
            if (passenger instanceof ServerPlayer player && player.getVehicle() == rocket
                    && player.level() == rocket.level() && player.isShiftKeyDown()
                    && BayPassengerExitPermit.online(player) && !player.hasDisconnected() && !player.isSpectator()
                    && !(player instanceof FakePlayer)
                    && !BayPassengerExitPermit.guarded(player) && (exits == null || !exits.state.internal(player))) {
                try {
                    if (RocketFlightService.leave(player, rocket).success() && player.getVehicle() != rocket) {
                        // Player.rideTick would reset this after consuming the same native input.
                        player.setShiftKeyDown(false);
                        if (rocket.flightData().orElseThrow().passengers().assignment(player.getUUID()).isEmpty()) {
                            AdvancedRocketryCommunity.LOGGER.info("ARCE_SEAT_RELEASED rocket={} player={} cause=SNEAK",
                                    rocket.getUUID(), player.getUUID());
                        }
                    }
                } catch (RuntimeException failure) {
                    // Refusals retain the old seat. Do not propagate them out of the entity tick or log unbounded data.
                    String reason = failure.getClass().getSimpleName();
                    AdvancedRocketryCommunity.LOGGER.warn("ARCE_SEAT_RELEASE_HELD rocket={} player={} reason={}",
                            rocket.getUUID(), player.getUUID(), reason.substring(0, Math.min(reason.length(), 80)));
                }
            }
        }
    }

    private static boolean stationary(RocketFlightState state) {
        return state == RocketFlightState.ASSEMBLED || state == RocketFlightState.FUELED || state == RocketFlightState.LANDED;
    }
}
