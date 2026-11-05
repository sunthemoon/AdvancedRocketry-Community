package io.github.sunthemoon.advancedrocketrycommunity.rocket.server;

import io.github.sunthemoon.advancedrocketrycommunity.AdvancedRocketryCommunity;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.entity.RocketEntity;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFlightRequestCode;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFlightRequestResult;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.rejoin.RocketRejoinProtection;
import java.util.UUID;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/** Shared membership mutation; a held bay's physical exit deliberately does not mutate membership. */
public final class RocketSeatRelease {
    private RocketSeatRelease() { }

    static RocketFlightRequestResult leave(ServerPlayer player, RocketEntity rocket) {
        var physicalExit = RocketRuntime.passengerExits().map(exits -> exits.heldLeave(player, rocket)).orElse(null);
        return physicalExit != null ? physicalExit : release(rocket, player.getUUID(), player);
    }

    static RocketFlightRequestResult release(RocketEntity rocket, UUID playerId) {
        return release(rocket, playerId, ((ServerLevel) rocket.level()).getServer().getPlayerList().getPlayer(playerId));
    }

    private static RocketFlightRequestResult release(RocketEntity rocket, UUID playerId, ServerPlayer player) {
        if (RocketReleaseAccess.fenced(rocket) ? !RocketReleaseAccess.retainedRiderMayLeave(rocket, playerId)
                : !RocketFlightService.passengerMutationAllowed(rocket)) {
            return RocketFlightRequestResult.failure(RocketFlightRequestCode.TRANSFER_JOURNAL_BLOCKED);
        }
        var flight = rocket.flightData().orElseThrow();
        if (!RocketFlightService.stationary(flight.state()) || flight.activeTransferId().isPresent()) {
            return RocketFlightRequestResult.failure(RocketFlightRequestCode.INVALID_STATE);
        }
        if (flight.passengers().assignment(playerId).isEmpty()) {
            return RocketFlightRequestResult.failure(RocketFlightRequestCode.NOT_BOARDED);
        }
        var server = ((ServerLevel) rocket.level()).getServer();
        var exits = RocketRuntime.passengerExits().orElse(null);
        if (RocketRejoinProtection.required(server, playerId) || player != null
                && (BayPassengerExitPermit.guarded(player) || exits != null && exits.state.internal(player))) {
            return RocketFlightRequestResult.failure(RocketFlightRequestCode.TRANSFER_JOURNAL_BLOCKED);
        }
        // An absent assigned player can be riding something else. Never detach that other vehicle.
        if (player != null && player.getVehicle() == rocket) {
            player.stopRiding();
            if (player.getVehicle() == rocket) {
                return RocketFlightRequestResult.failure(RocketFlightRequestCode.INVALID_STATE);
            }
        }
        rocket.updateFlightData(flight.withPassengers(flight.passengers().remove(playerId)));
        return new RocketFlightRequestResult(RocketFlightRequestCode.SUCCESS, 0L);
    }

    /** Permission belongs to the command; this boundary still validates loaded state and all mutation guards. */
    public static RocketFlightRequestResult operator(RocketEntity rocket, UUID player) {
        if (!(rocket.level() instanceof ServerLevel level) || !level.getServer().isSameThread()
                || level.getServer().isStopped() || !rocket.operational() || !rocket.isAlive()
                || !RocketTransferEntities.loadedEntity(rocket)) {
            return RocketFlightRequestResult.failure(RocketFlightRequestCode.ENTITY_UNAVAILABLE);
        }
        if (RocketRuntime.sealedFrozen(rocket)) {
            return RocketFlightRequestResult.failure(RocketFlightRequestCode.TRANSFER_JOURNAL_BLOCKED);
        }
        try {
            var result = release(rocket, player);
            if (result.success()) { released(rocket, player, "OPERATOR"); }
            return result;
        } catch (RuntimeException failure) {
            held(rocket, player, failure);
            return RocketFlightRequestResult.failure(RocketFlightRequestCode.TRANSFER_JOURNAL_BLOCKED);
        }
    }

    static void released(RocketEntity rocket, UUID player, String cause) {
        AdvancedRocketryCommunity.LOGGER.info("ARCE_SEAT_RELEASED rocket={} player={} cause={}", rocket.getUUID(), player, cause);
    }

    static void held(RocketEntity rocket, UUID player, RuntimeException failure) {
        String reason = failure.getClass().getSimpleName();
        AdvancedRocketryCommunity.LOGGER.warn("ARCE_SEAT_RELEASE_HELD rocket={} player={} reason={}",
                rocket.getUUID(), player, reason.substring(0, Math.min(reason.length(), 80)), failure);
    }
}
