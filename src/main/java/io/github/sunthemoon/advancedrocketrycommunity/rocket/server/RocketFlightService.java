package io.github.sunthemoon.advancedrocketrycommunity.rocket.server;

import io.github.sunthemoon.advancedrocketrycommunity.AdvancedRocketryCommunity;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.entity.RocketEntity;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketDestination;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFlightAction;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFlightData;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFlightPlanResult;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFlightPlanner;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketTargetFlightPlanner;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFlightRequestCode;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFlightRequestResult;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFlightState;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketTransferInspection;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketTransferRecoveryReport;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.menu.RocketFlightQuotes;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.transaction.RocketOperationLedger;
import io.github.sunthemoon.advancedrocketrycommunity.station.model.StationDestinationSummary;
import io.github.sunthemoon.advancedrocketrycommunity.station.model.StationState;
import io.github.sunthemoon.advancedrocketrycommunity.station.persistence.StationRegistrySavedData;
import io.github.sunthemoon.advancedrocketrycommunity.station.service.StationAccessAction;
import io.github.sunthemoon.advancedrocketrycommunity.station.service.StationAccessService;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.service.CelestialCatalogManager;
import io.github.sunthemoon.advancedrocketrycommunity.travel.migration.LegacyTravelTargetAdapter;
import io.github.sunthemoon.advancedrocketrycommunity.travel.model.TravelTarget;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.data.PlanetaryCatalog;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.data.PlanetaryCatalogManager;
import io.github.sunthemoon.advancedrocketrycommunity.travel.network.TravelTargetWireCodec;
import java.util.List;
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
    private final StationAccessService stationAccess = new StationAccessService();
    private final PlanetaryCatalogManager catalogs;
    private final boolean configuredCatalogs;

    RocketFlightService(CelestialCatalogManager celestialCatalogs, PlanetaryCatalogManager catalogs) {
        this.catalogs = catalogs;
        this.configuredCatalogs = celestialCatalogs != null || catalogs != null;
    }

    void openMenu(ServerPlayer player, RocketEntity requestedRocket) {
        Access access = access(player, requestedRocket.getId());
        if (!access.success()) {
            notify(player, access.code(), 0L);
            return;
        }
        RocketEntity rocket = access.rocket();
        StationRegistrySavedData stationData = StationRegistrySavedData.get(player.getServer());
        List<StationDestinationSummary> accessible = stationData.operational()
                ? stationAccess.accessibleDestinations(
                        stationData.stations(),
                        player.getUUID(),
                        player.hasPermissions(2)
                ).stream().map(StationDestinationSummary::from).toList()
                : List.of();
        UUID currentStationId = stationData.findAt(
                rocket.blockPosition().getX(),
                rocket.blockPosition().getZ()
        ).filter(station -> rocket.level().dimension().location()
                .equals(RocketDestination.SPACE_STATION.dimensionId()))
                .map(StationState::stationId)
                .orElse(null);
        UUID plannedStationId = rocket.flightData()
                .flatMap(RocketFlightData::plan)
                .flatMap(plan -> plan.destinationStation())
                .orElse(null);
        NetworkHooks.openScreen(
                player,
                rocket,
                buffer -> {
                    buffer.writeVarInt(rocket.getId());
                    TravelTargetWireCodec.encode(
                            buffer,
                            rocket.flightData().orElseThrow().currentTarget().orElseThrow()
                    );
                    buffer.writeVarInt(accessible.size());
                    for (StationDestinationSummary station : accessible) {
                        buffer.writeUUID(station.stationId());
                        buffer.writeUtf(station.name());
                    }
                    buffer.writeBoolean(currentStationId != null);
                    if (currentStationId != null) {
                        buffer.writeUUID(currentStationId);
                    }
                    buffer.writeBoolean(plannedStationId != null);
                    if (plannedStationId != null) {
                        buffer.writeUUID(plannedStationId);
                    }
                }
        );
    }

    RocketFlightQuotes quotes(ServerPlayer player, RocketEntity rocket) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(rocket, "rocket");
        RocketFlightData flight = rocket.flightData().orElse(null);
        if (flight == null || rocket.snapshot().isEmpty()) {
            return RocketFlightQuotes.empty();
        }
        PlanetaryCatalog pair = captureCatalog();
        if (pair == null) {
            if (configuredCatalogs) {
                return RocketFlightQuotes.empty();
            }
            RocketDestination source = flight.currentTarget()
                    .flatMap(LegacyTravelTargetAdapter::toLegacy)
                    .map(LegacyTravelTargetAdapter.LegacyDestination::destination)
                    .orElse(null);
            return RocketFlightQuotes.compute(
                    rocket.snapshot().orElseThrow().stats(),
                    flight.fuel(),
                    source,
                    flight.state()
            );
        }
        StationRegistrySavedData stations = StationRegistrySavedData.get(player.getServer());
        if (!stations.operational()) {
            return RocketFlightQuotes.empty();
        }
        java.util.ArrayList<TravelTarget> targets = new java.util.ArrayList<>();
        pair.celestial().definitions().stream()
                .filter(definition -> definition.supportsSurfaceArrival())
                .map(definition -> definition.id())
                .filter(body -> !body.equals(io.github.sunthemoon.advancedrocketrycommunity.celestial.CelestialIds
                        .SPACE_ID))
                .map(TravelTarget.BodySurface::new)
                .forEach(targets::add);
        stationAccess.accessibleDestinations(
                stations.stations(),
                player.getUUID(),
                player.hasPermissions(2)
        ).stream()
                .filter(station -> pair.celestial().get(station.orbitBody())
                        .filter(body -> body.capabilities().orbitable()).isPresent())
                .map(StationState::stationId)
                .map(TravelTarget.Station::new)
                .forEach(targets::add);
        if (targets.size() > RocketFlightQuotes.MAX_QUOTES) {
            return RocketFlightQuotes.empty();
        }

        boolean launchableState = flight.state() == RocketFlightState.FUELED
                || (flight.state() == RocketFlightState.LANDED && flight.fuel().amount() > 0L);
        java.util.ArrayList<RocketFlightQuotes.TargetQuote> quoted = new java.util.ArrayList<>(targets.size());
        for (TravelTarget target : targets) {
            RocketFlightPlanResult result = plan(
                    rocket,
                    flight,
                    flight.currentTarget().orElseThrow(),
                    target,
                    stations,
                    UUID.fromString("123e4567-e89b-42d3-a456-426614174721"),
                    pair
            );
            quoted.add(new RocketFlightQuotes.TargetQuote(
                    target,
                    new RocketFlightQuotes.Quote(
                            Math.toIntExact(result.requiredFuel()),
                            launchableState && result.success()
                    )
            ));
        }
        return new RocketFlightQuotes(quoted);
    }

    RocketFlightRequestResult request(
            ServerPlayer player,
            int rocketEntityId,
            RocketFlightAction action,
            TravelTarget target,
            UUID requestId
    ) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(action, "action");
        Objects.requireNonNull(target, "target");
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
                report(player, null, action, target, requestId, result);
            }
            return result;
        }
        Access access = access(player, rocketEntityId);
        if (!access.success()) {
            RocketFlightRequestResult result = RocketFlightRequestResult.failure(access.code());
            report(player, null, action, target, requestId, result);
            return result;
        }
        if ((action == RocketFlightAction.LAUNCH || action == RocketFlightAction.CANCEL)
                && !authorized(player, access.rocket())) {
            RocketFlightRequestResult result = RocketFlightRequestResult.failure(
                    RocketFlightRequestCode.UNAUTHORIZED
            );
            report(player, access.rocket(), action, target, requestId, result);
            return result;
        }
        RocketOperationLedger.BeginResult begin = requests.begin(requestId);
        if (begin != RocketOperationLedger.BeginResult.STARTED) {
            RocketFlightRequestResult result = RocketFlightRequestResult.failure(
                    begin == RocketOperationLedger.BeginResult.REPLAYED
                            ? RocketFlightRequestCode.REQUEST_REPLAYED
                            : RocketFlightRequestCode.REQUEST_LEDGER_FULL
            );
            report(player, access.rocket(), action, target, requestId, result);
            return result;
        }

        RocketFlightRequestResult result;
        try {
            result = switch (action) {
                case LAUNCH -> launch(
                        access.rocket(),
                        target,
                        player.getUUID(),
                        player.hasPermissions(2),
                        requestId
                );
                case CANCEL -> cancel(access.rocket(), target);
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
        report(player, access.rocket(), action, target, requestId, result);
        return result;
    }

    private RocketFlightRequestResult launch(
            RocketEntity rocket,
            TravelTarget destination,
            UUID actorId,
            boolean operator,
            UUID requestId
    ) {
        RocketFlightData flight = rocket.flightData().orElseThrow();
        if (flight.state() == RocketFlightState.LANDED && flight.fuel().amount() > 0L) {
            flight = flight.withFuel(flight.fuel(), rocket.level().getGameTime());
        }
        if (flight.state() != RocketFlightState.FUELED) {
            return RocketFlightRequestResult.failure(RocketFlightRequestCode.INVALID_STATE);
        }
        TravelTarget source = flight.currentTarget().orElse(null);
        if (source == null || source.equals(destination)) {
            return RocketFlightRequestResult.failure(RocketFlightRequestCode.INVALID_DESTINATION);
        }
        StationRegistrySavedData stationData = StationRegistrySavedData.get(
                ((ServerLevel) rocket.level()).getServer()
        );
        if (!stationData.operational()) {
            return RocketFlightRequestResult.failure(RocketFlightRequestCode.TRANSFER_JOURNAL_BLOCKED);
        }
        if (source instanceof TravelTarget.Station sourceTarget) {
            StationState sourceStation = stationData.findAt(
                    rocket.blockPosition().getX(),
                    rocket.blockPosition().getZ()
            ).orElse(null);
            if (sourceStation == null || !sourceStation.stationId().equals(sourceTarget.instanceId())) {
                return RocketFlightRequestResult.failure(RocketFlightRequestCode.INVALID_DESTINATION);
            }
            if (!stationAccess.allowed(
                    sourceStation, actorId, operator, StationAccessAction.VISIT
            )) {
                return RocketFlightRequestResult.failure(RocketFlightRequestCode.UNAUTHORIZED);
            }
        }
        if (destination instanceof TravelTarget.Station stationTarget) {
            StationState target = stationData.find(stationTarget.instanceId()).orElse(null);
            if (target == null) {
                return RocketFlightRequestResult.failure(RocketFlightRequestCode.INVALID_DESTINATION);
            }
            if (!stationAccess.allowed(target, actorId, operator, StationAccessAction.VISIT)) {
                return RocketFlightRequestResult.failure(RocketFlightRequestCode.UNAUTHORIZED);
            }
        } else if (!(destination instanceof TravelTarget.BodySurface)) {
            return RocketFlightRequestResult.failure(RocketFlightRequestCode.INVALID_DESTINATION);
        }
        RocketFlightPlanResult planned = plan(
                rocket,
                flight,
                source,
                destination,
                stationData,
                requestId,
                captureCatalog()
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
            TravelTarget destination,
            UUID requestId
    ) {
        Objects.requireNonNull(rocket, "rocket");
        Objects.requireNonNull(destination, "destination");
        Objects.requireNonNull(requestId, "requestId");
        if (!(rocket.level() instanceof ServerLevel) || !rocket.isAlive() || !rocket.operational()) {
            return RocketFlightRequestResult.failure(RocketFlightRequestCode.ENTITY_UNAVAILABLE);
        }
        return launch(
                rocket,
                destination,
                rocket.ownerId().orElseThrow(),
                true,
                requestId
        );
    }

    RocketFlightRequestResult requestAdminStationFlight(
            RocketEntity rocket,
            UUID stationId,
            UUID requestId
    ) {
        Objects.requireNonNull(rocket, "rocket");
        Objects.requireNonNull(stationId, "stationId");
        Objects.requireNonNull(requestId, "requestId");
        if (!(rocket.level() instanceof ServerLevel) || !rocket.isAlive() || !rocket.operational()) {
            return RocketFlightRequestResult.failure(RocketFlightRequestCode.ENTITY_UNAVAILABLE);
        }
        return launch(
                rocket,
                new TravelTarget.Station(stationId),
                rocket.ownerId().orElseThrow(),
                true,
                requestId
        );
    }

    private RocketFlightRequestResult cancel(
            RocketEntity rocket,
            TravelTarget destination
    ) {
        RocketFlightData flight = rocket.flightData().orElseThrow();
        if (flight.state() != RocketFlightState.COUNTDOWN) {
            return RocketFlightRequestResult.failure(RocketFlightRequestCode.INVALID_STATE);
        }
        if (flight.plan().isEmpty()
                || !flight.plan().orElseThrow().destinationTarget().equals(destination)) {
            return RocketFlightRequestResult.failure(RocketFlightRequestCode.INVALID_DESTINATION);
        }
        return transfers.cancelCountdown(rocket);
    }

    private RocketFlightPlanResult plan(
            RocketEntity rocket,
            RocketFlightData flight,
            TravelTarget source,
            TravelTarget destination,
            StationRegistrySavedData stations,
            UUID requestId,
            PlanetaryCatalog pair
    ) {
        if (pair != null) {
            if (!PlanetaryFlightAdmission.allows(rocket, flight, source, destination, pair.celestial(), stations)) {
                return RocketFlightPlanResult.failure(
                        io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFlightPlanCode.UNSUPPORTED_ROUTE, 0L);
            }
            return RocketTargetFlightPlanner.plan(
                    rocket.snapshot().orElseThrow().stats(),
                    flight.fuel(),
                    source,
                    flight.currentDimension(),
                    destination,
                    pair.celestial(),
                    pair.routes(),
                    stations::find,
                    requestId,
                    rocket.level().getGameTime()
            );
        }
        if (configuredCatalogs) {
            return RocketFlightPlanResult.failure(
                    io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFlightPlanCode.UNSUPPORTED_ROUTE,
                    0L
            );
        }
        Optional<LegacyTravelTargetAdapter.LegacyDestination> legacySource =
                LegacyTravelTargetAdapter.toLegacy(source);
        Optional<LegacyTravelTargetAdapter.LegacyDestination> legacyDestination =
                LegacyTravelTargetAdapter.toLegacy(destination);
        if (legacySource.isEmpty() || legacyDestination.isEmpty()) {
            return RocketFlightPlanResult.failure(
                    io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFlightPlanCode.UNSUPPORTED_ROUTE,
                    0L
            );
        }
        return RocketFlightPlanner.plan(
                rocket.snapshot().orElseThrow().stats(),
                flight.fuel(),
                legacySource.orElseThrow().destination().profile(),
                legacyDestination.orElseThrow().destination().profile(),
                legacyDestination.orElseThrow().stationId(),
                requestId,
                rocket.level().getGameTime()
        );
    }

    private PlanetaryCatalog captureCatalog() {
        return catalogs == null ? null : catalogs.capture().map(PlanetaryCatalogManager.Generation::catalog).orElse(null);
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
            TravelTarget destination,
            UUID requestId,
            RocketFlightRequestResult result
    ) {
        notify(player, result.code(), result.requiredFuel());
        AdvancedRocketryCommunity.LOGGER.info(
                "ARCE_FLIGHT_INTENT request={} player={} rocket={} action={} destination={} station={} "
                        + "code={} required_fuel={}",
                requestId,
                player.getUUID(),
                rocket == null ? "none" : rocket.getUUID(),
                action,
                destination.typeId(),
                destination instanceof TravelTarget.Station station ? station.instanceId() : null,
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

    void onPlayerLoggedOut(UUID playerId) {
        transfers.onPlayerLoggedOut(playerId);
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
