package io.github.sunthemoon.advancedrocketrycommunity.rocket.server;

import io.github.sunthemoon.advancedrocketrycommunity.AdvancedRocketryCommunity;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.entity.RocketEntity;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketDestination;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFlightAction;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFlightData;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFlightPlanResult;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFlightPlanner;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFlightRequestCode;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFlightRequestResult;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFlightState;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketTransferInspection;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketTransferRecoveryReport;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.transaction.RocketOperationLedger;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkHooks;

/** Main-thread validation boundary for menus and C2S launch/cancel intents. */
final class RocketFlightService {
    private final RocketOperationLedger requests = new RocketOperationLedger();
    private final RocketIntentRateLimiter rateLimiter = new RocketIntentRateLimiter();
    private final RocketTransferService transfers = new RocketTransferService();

    void openMenu(ServerPlayer player, RocketEntity requestedRocket) {
        Access access = access(player, requestedRocket.getId());
        if (!access.success()) {
            notify(player, access.code(), 0L);
            return;
        }
        RocketEntity rocket = access.rocket();
        NetworkHooks.openScreen(
                player,
                rocket,
                buffer -> buffer.writeVarInt(rocket.getId())
        );
    }

    RocketFlightRequestResult request(
            ServerPlayer player,
            int rocketEntityId,
            RocketFlightAction action,
            RocketDestination destination,
            UUID requestId
    ) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(action, "action");
        Objects.requireNonNull(destination, "destination");
        Objects.requireNonNull(requestId, "requestId");
        RocketIntentRateLimiter.Decision rateDecision = rateLimiter.check(
                player.getUUID(),
                player.level().getGameTime()
        );
        if (rateDecision != RocketIntentRateLimiter.Decision.ALLOWED) {
            RocketFlightRequestResult result = RocketFlightRequestResult.failure(
                    RocketFlightRequestCode.RATE_LIMITED
            );
            if (rateDecision == RocketIntentRateLimiter.Decision.REJECTED_AUDIT) {
                report(player, null, action, destination, requestId, result);
            }
            return result;
        }
        Access access = access(player, rocketEntityId);
        if (!access.success()) {
            RocketFlightRequestResult result = RocketFlightRequestResult.failure(access.code());
            report(player, null, action, destination, requestId, result);
            return result;
        }
        if ((action == RocketFlightAction.LAUNCH || action == RocketFlightAction.CANCEL)
                && !authorized(player, access.rocket())) {
            RocketFlightRequestResult result = RocketFlightRequestResult.failure(
                    RocketFlightRequestCode.UNAUTHORIZED
            );
            report(player, access.rocket(), action, destination, requestId, result);
            return result;
        }
        RocketOperationLedger.BeginResult begin = requests.begin(requestId);
        if (begin != RocketOperationLedger.BeginResult.STARTED) {
            RocketFlightRequestResult result = RocketFlightRequestResult.failure(
                    begin == RocketOperationLedger.BeginResult.REPLAYED
                            ? RocketFlightRequestCode.REQUEST_REPLAYED
                            : RocketFlightRequestCode.REQUEST_LEDGER_FULL
            );
            report(player, access.rocket(), action, destination, requestId, result);
            return result;
        }

        RocketFlightRequestResult result;
        try {
            result = switch (action) {
                case LAUNCH -> launch(access.rocket(), destination, requestId);
                case CANCEL -> cancel(access.rocket(), destination);
                case BOARD -> board(player, access.rocket());
                case LEAVE -> leave(player, access.rocket());
            };
        } catch (RuntimeException exception) {
            AdvancedRocketryCommunity.LOGGER.error(
                    "ARCE_FLIGHT_INTENT_EXCEPTION request={} rocket={} action={}",
                    requestId,
                    access.rocket().getUUID(),
                    action,
                    exception
            );
            result = RocketFlightRequestResult.failure(RocketFlightRequestCode.INVALID_STATE);
        }
        requests.finish(requestId, result.success());
        report(player, access.rocket(), action, destination, requestId, result);
        return result;
    }

    private RocketFlightRequestResult launch(
            RocketEntity rocket,
            RocketDestination destination,
            UUID requestId
    ) {
        RocketFlightData flight = rocket.flightData().orElseThrow();
        if (flight.state() == RocketFlightState.LANDED && flight.fuel().amount() > 0L) {
            flight = flight.withFuel(flight.fuel(), rocket.level().getGameTime());
        }
        if (flight.state() != RocketFlightState.FUELED) {
            return RocketFlightRequestResult.failure(RocketFlightRequestCode.INVALID_STATE);
        }
        RocketDestination source;
        try {
            source = RocketDestination.fromDimension(flight.currentDimension());
        } catch (IllegalArgumentException exception) {
            return RocketFlightRequestResult.failure(RocketFlightRequestCode.INVALID_DESTINATION);
        }
        if (source == destination || source.opposite() != destination
                || !source.bodyId().equals(flight.currentBody())) {
            return RocketFlightRequestResult.failure(RocketFlightRequestCode.INVALID_DESTINATION);
        }
        RocketFlightPlanResult planned = RocketFlightPlanner.plan(
                rocket.snapshot().orElseThrow().stats(),
                flight.fuel(),
                source.profile(),
                destination.profile(),
                requestId,
                rocket.level().getGameTime()
        );
        if (!planned.success()) {
            return RocketFlightRequestResult.failure(
                    RocketFlightRequestCode.fromPlanCode(planned.code()),
                    planned.requiredFuel()
            );
        }
        RocketFlightData countdown = flight.withPlan(planned.plan())
                .startCountdown(rocket.level().getGameTime());
        return transfers.prepareLaunch(rocket, countdown);
    }

    RocketFlightRequestResult requestAdminFlight(
            RocketEntity rocket,
            RocketDestination destination,
            UUID requestId
    ) {
        Objects.requireNonNull(rocket, "rocket");
        Objects.requireNonNull(destination, "destination");
        Objects.requireNonNull(requestId, "requestId");
        if (!(rocket.level() instanceof ServerLevel) || !rocket.isAlive() || !rocket.operational()) {
            return RocketFlightRequestResult.failure(RocketFlightRequestCode.ENTITY_UNAVAILABLE);
        }
        return launch(rocket, destination, requestId);
    }

    private RocketFlightRequestResult cancel(
            RocketEntity rocket,
            RocketDestination destination
    ) {
        RocketFlightData flight = rocket.flightData().orElseThrow();
        if (flight.state() != RocketFlightState.COUNTDOWN) {
            return RocketFlightRequestResult.failure(RocketFlightRequestCode.INVALID_STATE);
        }
        if (flight.plan().isEmpty()
                || !flight.plan().orElseThrow().destinationBody().equals(destination.bodyId())) {
            return RocketFlightRequestResult.failure(RocketFlightRequestCode.INVALID_DESTINATION);
        }
        return transfers.cancelCountdown(rocket);
    }

    private RocketFlightRequestResult board(ServerPlayer player, RocketEntity rocket) {
        RocketFlightData flight = rocket.flightData().orElseThrow();
        if (!stationary(flight.state())) {
            return RocketFlightRequestResult.failure(RocketFlightRequestCode.INVALID_STATE);
        }
        if (flight.passengers().assignment(player.getUUID()).isPresent()) {
            player.startRiding(rocket, true);
            return RocketFlightRequestResult.failure(RocketFlightRequestCode.ALREADY_BOARDED);
        }
        var assigned = flight.passengers().assign(player.getUUID());
        if (assigned.isEmpty()) {
            return RocketFlightRequestResult.failure(RocketFlightRequestCode.NO_SEAT_AVAILABLE);
        }
        rocket.updateFlightData(flight.withPassengers(assigned.orElseThrow()));
        if (!player.startRiding(rocket, true)) {
            rocket.updateFlightData(flight);
            return RocketFlightRequestResult.failure(RocketFlightRequestCode.INVALID_STATE);
        }
        return new RocketFlightRequestResult(RocketFlightRequestCode.SUCCESS, 0L);
    }

    private RocketFlightRequestResult leave(ServerPlayer player, RocketEntity rocket) {
        RocketFlightData flight = rocket.flightData().orElseThrow();
        if (!stationary(flight.state())) {
            return RocketFlightRequestResult.failure(RocketFlightRequestCode.INVALID_STATE);
        }
        if (flight.passengers().assignment(player.getUUID()).isEmpty()) {
            return RocketFlightRequestResult.failure(RocketFlightRequestCode.NOT_BOARDED);
        }
        player.stopRiding();
        rocket.updateFlightData(flight.withPassengers(flight.passengers().remove(player.getUUID())));
        return new RocketFlightRequestResult(RocketFlightRequestCode.SUCCESS, 0L);
    }

    private static Access access(ServerPlayer player, int rocketEntityId) {
        if (!(player.level() instanceof ServerLevel level)
                || !(level.getEntity(rocketEntityId) instanceof RocketEntity rocket)
                || rocket.level() != level
                || !rocket.isAlive()
                || !rocket.operational()
                || !level.hasChunkAt(rocket.blockPosition())) {
            return Access.failure(RocketFlightRequestCode.ENTITY_UNAVAILABLE);
        }
        if (player.distanceToSqr(rocket) > RocketManager.MAX_INTERACTION_DISTANCE_SQUARED) {
            return Access.failure(RocketFlightRequestCode.OUT_OF_RANGE);
        }
        return Access.success(rocket);
    }

    private static boolean authorized(ServerPlayer player, RocketEntity rocket) {
        UUID owner = rocket.ownerId().orElseThrow();
        return owner.equals(player.getUUID()) || player.isCreative() || player.hasPermissions(2);
    }

    private static boolean stationary(RocketFlightState state) {
        return state == RocketFlightState.ASSEMBLED
                || state == RocketFlightState.FUELED
                || state == RocketFlightState.LANDED;
    }

    private static void report(
            ServerPlayer player,
            RocketEntity rocket,
            RocketFlightAction action,
            RocketDestination destination,
            UUID requestId,
            RocketFlightRequestResult result
    ) {
        notify(player, result.code(), result.requiredFuel());
        AdvancedRocketryCommunity.LOGGER.info(
                "ARCE_FLIGHT_INTENT request={} player={} rocket={} action={} destination={} code={} required_fuel={}",
                requestId,
                player.getUUID(),
                rocket == null ? "none" : rocket.getUUID(),
                action,
                destination.bodyId(),
                result.code(),
                result.requiredFuel()
        );
    }

    private static void notify(
            ServerPlayer player,
            RocketFlightRequestCode code,
            long requiredFuel
    ) {
        if (player.connection == null || !player.connection.connection.isConnected()) {
            return;
        }
        player.displayClientMessage(
                Component.translatable(code.translationKey(), requiredFuel),
                true
        );
    }

    void clear() {
        requests.clear();
        rateLimiter.clear();
        transfers.clear();
    }

    void tick(net.minecraft.server.MinecraftServer server) {
        transfers.tick(server);
    }

    void onPlayerLoggedIn(ServerPlayer player) {
        transfers.onPlayerLoggedIn(player);
    }

    int activeTransferCount(net.minecraft.server.MinecraftServer server) {
        return transfers.activeCount(server);
    }

    Optional<RocketTransferInspection> inspectTransfer(
            net.minecraft.server.MinecraftServer server,
            UUID transferId
    ) {
        return transfers.inspect(server, transferId);
    }

    RocketTransferRecoveryReport recoverTransfer(
            net.minecraft.server.MinecraftServer server,
            UUID transferId
    ) {
        return transfers.recover(server, transferId);
    }

    void releaseLandedReservation(RocketEntity rocket) {
        transfers.releaseLandedReservation(rocket);
    }

    private record Access(RocketEntity rocket, RocketFlightRequestCode code) {
        private Access {
            Objects.requireNonNull(code, "code");
        }

        static Access success(RocketEntity rocket) {
            return new Access(Objects.requireNonNull(rocket, "rocket"), RocketFlightRequestCode.SUCCESS);
        }

        static Access failure(RocketFlightRequestCode code) {
            return new Access(null, code);
        }

        boolean success() {
            return code == RocketFlightRequestCode.SUCCESS;
        }
    }
}
