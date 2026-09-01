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
import io.github.sunthemoon.advancedrocketrycommunity.rocket.transaction.RocketOperationLedger;
import java.util.Objects;
import java.util.UUID;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkHooks;

/** Main-thread validation boundary for menus and C2S launch/cancel intents. */
final class RocketFlightService {
    private final RocketOperationLedger requests = new RocketOperationLedger();

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
        Access access = access(player, rocketEntityId);
        if (!access.success()) {
            RocketFlightRequestResult result = RocketFlightRequestResult.failure(access.code());
            report(player, null, action, destination, requestId, result);
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
            result = action == RocketFlightAction.LAUNCH
                    ? launch(access.rocket(), destination, requestId)
                    : cancel(access.rocket(), destination);
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
        rocket.updateFlightData(
                flight.withPlan(planned.plan()).startCountdown(rocket.level().getGameTime())
        );
        return new RocketFlightRequestResult(RocketFlightRequestCode.SUCCESS, planned.requiredFuel());
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
        long requiredFuel = flight.plan().orElseThrow().requiredFuel();
        rocket.updateFlightData(flight.cancelCountdown(rocket.level().getGameTime()));
        return new RocketFlightRequestResult(RocketFlightRequestCode.SUCCESS, requiredFuel);
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
        UUID owner = rocket.ownerId().orElseThrow();
        if (!owner.equals(player.getUUID()) && !player.isCreative() && !player.hasPermissions(2)) {
            return Access.failure(RocketFlightRequestCode.UNAUTHORIZED);
        }
        return Access.success(rocket);
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
        player.displayClientMessage(
                Component.translatable(code.translationKey(), requiredFuel),
                true
        );
    }

    void clear() {
        requests.clear();
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
