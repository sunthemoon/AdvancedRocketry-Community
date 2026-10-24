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
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketTransferRecord;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.persistence.RocketTransferSavedData;
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
    private final RocketNavigationService navigation;

    RocketFlightService(CelestialCatalogManager celestialCatalogs, PlanetaryCatalogManager catalogs) {
        this.catalogs = catalogs;
        this.configuredCatalogs = celestialCatalogs != null || catalogs != null;
        this.navigation = new RocketNavigationService(catalogs, configuredCatalogs, this::plan);
    }

    void openMenu(ServerPlayer player, RocketEntity requestedRocket) {
        Access access = access(player, requestedRocket.getId());
        if (!access.success()) {
            notify(player, access.code(), 0L);
            return;
        }
        RocketEntity rocket = access.rocket();
        rocket.flightData().orElseThrow().fuel().propulsion().ifPresent(value -> player.sendSystemMessage(
                Component.translatable("message.advancedrocketrycommunity.propulsion.mode", value.descriptor().mode().name(),
                        value.summary())));
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
        return navigation(player, rocket).quotes();
    }

    io.github.sunthemoon.advancedrocketrycommunity.rocket.menu.RocketNavigation navigation(ServerPlayer player, RocketEntity rocket) {
        if (!player.serverLevel().getServer().isSameThread()) {
            throw new IllegalStateException("Navigation requires the logical server thread");
        }
        Access checked = access(player, rocket.getId());
        return checked.success() && checked.rocket() == rocket
                ? navigation.snapshot(player, rocket, authorized(player, rocket))
                : io.github.sunthemoon.advancedrocketrycommunity.rocket.menu.RocketNavigation.empty();
    }

    Optional<io.github.sunthemoon.advancedrocketrycommunity.celestial.network.CelestialSnapshotPacket> navigationCatalog(
            ServerPlayer player, RocketEntity rocket) {
        if (!player.serverLevel().getServer().isSameThread()) {
            throw new IllegalStateException("Navigation refresh requires the logical server thread");
        }
        Access checked = access(player, rocket.getId());
        if (!checked.success() || checked.rocket() != rocket
                || !(player.containerMenu instanceof io.github.sunthemoon.advancedrocketrycommunity.rocket.menu.RocketFlightMenu menu)
                || menu.rocketEntityId() != rocket.getId() || !menu.stillValid(player)) {
            return Optional.empty();
        }
        return navigation.catalog(player);
    }

    RocketFlightRequestResult request(
            ServerPlayer player,
            int rocketEntityId,
            RocketFlightAction action,
            TravelTarget target,
            UUID requestId
    ) {
        return request(player, rocketEntityId, action, target, requestId, null);
    }

    RocketFlightRequestResult request(ServerPlayer player, int rocketEntityId, RocketFlightAction action,
            TravelTarget target, UUID requestId,
            io.github.sunthemoon.advancedrocketrycommunity.station.pad.LandingPadReference pad) {
        if (pad != null && (!player.serverLevel().getServer().isSameThread() || player.connection == null
                || !player.connection.connection.isConnected() || !player.isAlive() || player.isSpectator()
                || player instanceof net.minecraftforge.common.util.FakePlayer
                || !(player.containerMenu instanceof io.github.sunthemoon.advancedrocketrycommunity.rocket.menu.RocketFlightMenu menu)
                || menu.rocketEntityId() != rocketEntityId || !menu.stillValid(player))) {
            return RocketFlightRequestResult.failure(RocketFlightRequestCode.UNAUTHORIZED);
        }
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
                        requestId,
                        pad, player.getUUID()
                );
                case CANCEL -> cancel(access.rocket(), target, player, requestId);
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
        if (result.code() != RocketFlightRequestCode.JOURNAL_PENDING) { requests.finish(requestId, result.success()); }
        if (action == RocketFlightAction.LAUNCH && result.success()) {
            io.github.sunthemoon.advancedrocketrycommunity.station.pad.LandingPadIndex
                    .get(player.getServer()).forgetChoice(player.getUUID());
        }
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
        return launch(rocket, destination, actorId, operator, requestId, null, null);
    }

    /** {@code requester}: the initiating player of a C2S intent, or null for admin and release-test launches (W90b). */
    private RocketFlightRequestResult launch(RocketEntity rocket, TravelTarget destination, UUID actorId,
            boolean operator, UUID requestId,
            io.github.sunthemoon.advancedrocketrycommunity.station.pad.LandingPadReference pad, UUID requester) {
        RocketFlightData flight = launchSource(rocket.flightData().orElseThrow(), rocket.level().getGameTime()).orElse(null);
        if (flight == null) {
            io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.EscrowLaunchTrace.note(rocket, requestId, "FLIGHT_PRECONDITION", "LAUNCH_READY");
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
            io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.EscrowLaunchTrace.note(rocket, requestId, "FLIGHT_PRECONDITION", "STATION_OPERATIONAL");
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
        // ADR-044 §5: the new record carries the station's current orbit (the planner's source body).
        RocketFlightData docked = source instanceof TravelTarget.Station
                ? flight.atStationOrbit(planned.plan().sourceBody()) : flight;
        if (pad != null && !(destination instanceof TravelTarget.Station)) {
            return RocketFlightRequestResult.failure(RocketFlightRequestCode.INVALID_DESTINATION);
        }
        RocketFlightData countdown = docked.withPlan(pad == null ? planned.plan() : planned.plan().withLandingPad(pad))
                .startCountdown(rocket.level().getGameTime());
        var server = ((ServerLevel) rocket.level()).getServer();
        var journal = RocketTransferSavedData.get(server);
        var predecessor = journal.findByLogicalRocket(countdown.logicalRocketId()).orElse(null);
        return transfers.prepareLaunch(rocket, countdown, new RocketRequestCompletion(() -> {
            if (requests.outcome(requestId) != RocketOperationLedger.Outcome.ACTIVE) { return; }
            requests.finish(requestId, true);
            var player = ((ServerLevel) rocket.level()).getServer().getPlayerList().getPlayer(actorId);
            if (player != null) {
                io.github.sunthemoon.advancedrocketrycommunity.station.pad.LandingPadIndex
                        .get(player.getServer()).forgetChoice(actorId);
                report(player, rocket, RocketFlightAction.LAUNCH, destination, requestId,
                        new RocketFlightRequestResult(RocketFlightRequestCode.SUCCESS, planned.requiredFuel()));
            }
        }, launchDiscarded(requests, requestId, requester, journal, predecessor, (playerId, result) -> {
            var player = server.getPlayerList().getPlayer(playerId);
            if (player != null) { report(player, rocket, RocketFlightAction.LAUNCH, destination, requestId, result); }
        }), requester),
                () -> revalidateLaunch(rocket, countdown.plan().orElseThrow(), source, destination, actorId, operator, requester));
    }

    /** Owner-thread completion; the reporting adapter resolves the requester only when this callback runs. */
    static Runnable launchDiscarded(RocketOperationLedger requests, UUID requestId, UUID requester,
            RocketTransferSavedData journal, RocketTransferRecord predecessor,
            java.util.function.BiConsumer<UUID, RocketFlightRequestResult> report) {
        return () -> requests.finish(requestId, false);
    }

    /** I7 waits without refreshing its quote: changed permission, route or fuel calculation refuses the original request. */
    private boolean revalidateLaunch(RocketEntity rocket, io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFlightPlan expected,
            TravelTarget source, TravelTarget destination, UUID actor, boolean operator, UUID requester) {
        var server = rocket.getServer();
        // A paced I7 load can replace the Java instance, never the accepted physical or logical identity.
        if (rocket.level() instanceof ServerLevel level && level.getEntity(rocket.getUUID()) instanceof RocketEntity loaded) {
            rocket = loaded;
        }
        if (server == null || !rocket.operational()) { return false; }
        if (requester != null) {
            var player = server.getPlayerList().getPlayer(requester);
            if (player == null || !access(player, rocket.getId()).success() || !authorized(player, rocket)) { return false; }
            operator = player.hasPermissions(2);
        }
        var flight = launchSource(rocket.flightData().orElseThrow(), rocket.level().getGameTime()).orElse(null);
        var stations = StationRegistrySavedData.get(server);
        if (flight == null || !stations.operational() || !flight.currentTarget().filter(source::equals).isPresent()) { return false; }
        for (var target : List.of(source, destination)) {
            if (!(target instanceof TravelTarget.Station stationTarget)) { continue; }
            var station = stations.find(stationTarget.instanceId()).orElse(null);
            if (station == null || !stationAccess.allowed(station, actor, operator, StationAccessAction.VISIT)) { return false; }
            if (target == source && stations.findAt(rocket.blockPosition().getX(), rocket.blockPosition().getZ())
                    .filter(value -> value.stationId().equals(stationTarget.instanceId())).isEmpty()) { return false; }
        }
        var checked = plan(rocket, flight, source, destination, stations, expected.requestId(), captureCatalog());
        if (!checked.success()) { return false; }
        var candidate = checked.plan();
        return candidate.sourceBody().equals(expected.sourceBody()) && candidate.destinationBody().equals(expected.destinationBody())
                && candidate.sourceDimension().equals(expected.sourceDimension()) && candidate.destinationDimension().equals(expected.destinationDimension())
                && Objects.equals(candidate.destinationStationId(), expected.destinationStationId())
                && candidate.destinationTarget().equals(expected.destinationTarget()) && candidate.requiredFuel() == expected.requiredFuel()
                && Objects.equals(candidate.propulsionDebit(), expected.propulsionDebit());
    }

    RocketFlightRequestResult requestAdminPadFlight(RocketEntity rocket, UUID stationId,
            io.github.sunthemoon.advancedrocketrycommunity.station.pad.LandingPadReference pad, UUID requestId) {
        if (!(rocket.level() instanceof ServerLevel) || !rocket.operational()) {
            return RocketFlightRequestResult.failure(RocketFlightRequestCode.ENTITY_UNAVAILABLE);
        }
        return launch(rocket, new TravelTarget.Station(stationId), rocket.ownerId().orElseThrow(), true, requestId, pad, null);
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
            TravelTarget destination, ServerPlayer player, UUID requestId
    ) {
        RocketFlightData flight = rocket.flightData().orElseThrow();
        if (flight.state() != RocketFlightState.COUNTDOWN) {
            return RocketFlightRequestResult.failure(RocketFlightRequestCode.INVALID_STATE);
        }
        if (flight.plan().isEmpty()
                || !flight.plan().orElseThrow().destinationTarget().equals(destination)) {
            return RocketFlightRequestResult.failure(RocketFlightRequestCode.INVALID_DESTINATION);
        }
        return transfers.cancelCountdown(rocket, new RocketRequestCompletion(() -> {
            requests.finish(requestId, true);
            report(player, rocket, RocketFlightAction.CANCEL, destination, requestId,
                    new RocketFlightRequestResult(RocketFlightRequestCode.SUCCESS, flight.plan().orElseThrow().requiredFuel()));
        }, () -> requests.finish(requestId, false)));
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
            if (!source.equals(destination) && !PlanetaryFlightAdmission.discovered(
                    (ServerLevel) rocket.level(), destination, pair.celestial(), stations)) {
                return RocketFlightPlanResult.failure(
                        io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFlightPlanCode.DISCOVERY_REQUIRED, 0L);
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
        if (!passengerMutationAllowed(rocket)) {
            return RocketFlightRequestResult.failure(RocketFlightRequestCode.TRANSFER_JOURNAL_BLOCKED);
        }
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
        if (!passengerMutationAllowed(rocket)) {
            return RocketFlightRequestResult.failure(RocketFlightRequestCode.TRANSFER_JOURNAL_BLOCKED);
        }
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

    private static boolean passengerMutationAllowed(RocketEntity rocket) {
        if (!io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.RocketEscrowGate.idle(rocket)) { return false; }
        var journal = io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.persistence.RocketTransferSavedData
                .get(((ServerLevel) rocket.level()).getServer());
        return !journal.operational() || !journal.resourceMutationUnproved(rocket.assemblyTransactionId().orElseThrow());
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

    /** R55: the FUELED source of a launch, or empty (INVALID_STATE) when the stationary rocket is not launch-ready. */
    static Optional<RocketFlightData> launchSource(RocketFlightData flight, long gameTime) {
        if (!flight.launchReady()) {
            return Optional.empty();
        }
        // An escrow fill keeps ASSEMBLED with fuel; the launch applies the ordinary fuel change's promotion here.
        return Optional.of(flight.state() == RocketFlightState.FUELED ? flight : flight.withFuel(flight.fuel(), gameTime));
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
        if (result.detailKey() != null && player.connection != null) {
            player.sendSystemMessage(Component.translatable("message.advancedrocketrycommunity.pad.landing_refused",
                    Component.translatable(result.detailKey())));
        }
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

    void onRocketJoined(RocketEntity rocket) { transfers.onRocketJoined(rocket); }
    void installBayRecoveryGate(RocketTransferBayRecoveryGate gate) { transfers.installBayRecoveryGate(gate); }

    void clear() {
        requests.clear();
        rateLimiter.clear();
        transfers.clear();
        navigation.clear();
    }

    void tick(net.minecraft.server.MinecraftServer server) {
        transfers.tick(server);
    }

    void onPlayerLoggedIn(ServerPlayer player) {
        transfers.onPlayerLoggedIn(player);
    }

    void onPlayerLoggedOut(UUID playerId) {
        transfers.onPlayerLoggedOut(playerId);
        navigation.remove(playerId);
    }

    boolean stationRegionInMotion(net.minecraft.server.MinecraftServer server, int minX, int minZ,
                                  int maxX, int maxZ) {
        return transfers.stationRegionInMotion(server, minX, minZ, maxX, maxZ);
    }

    String transferJournalDiagnostics(net.minecraft.server.MinecraftServer server) {
        return transfers.journalDiagnostics(server);
    }

    int activeTransferCount(net.minecraft.server.MinecraftServer server) {
        return transfers.activeCount(server);
    }

    // BEGIN transfer service diagnostics
    String transferFailureDiagnostics(UUID transferId, boolean active) {
        return transfers.transferFailureDiagnostics(transferId, active);
    }
    // END transfer service diagnostics

    Optional<RocketTransferInspection> inspectTransfer(
            net.minecraft.server.MinecraftServer server,
            UUID transferId
    ) {
        return transfers.inspect(server, transferId);
    }

    /** W90b: a logical rocket's destination warm-up line, when no journal entry has that ID. */
    Optional<String> inspectWarmup(net.minecraft.server.MinecraftServer server, UUID logicalRocketId) {
        return transfers.warmups.inspect(server, logicalRocketId);
    }

    RocketTransferRecoveryReport recoverTransfer(
            net.minecraft.server.MinecraftServer server,
            UUID transferId
    ) {
        return transfers.recover(server, transferId);
    }

    boolean releaseLandedReservation(RocketEntity rocket) {
        return transfers.releaseLandedReservation(rocket);
    }

    io.github.sunthemoon.advancedrocketrycommunity.rocket.validation.RocketValidationCode beginLandedDisassembly(
            RocketEntity rocket, java.util.function.Supplier<Optional<
                    io.github.sunthemoon.advancedrocketrycommunity.rocket.validation.RocketValidationCode>> preflight,
            RocketLandedDisassemblies.Request request) {
        return transfers.beginLandedDisassembly(rocket, preflight, request);
    }

    boolean landedDisassemblyPending(RocketEntity rocket) {
        return transfers.landedDisassemblyPending(rocket);
    }

    /** W85 A1: the GameTest bridge reads P's inputs to a new flight from this session's transfer service. */
    RocketTransferService transfers() {
        return transfers;
    }

    boolean resourceMutationAllowed(RocketEntity rocket) {
        return transfers.resourceMutationAllowed(rocket);
    }

    void retireBeforeFill(RocketEntity rocket) { transfers.retireBeforeFill(rocket); }

    boolean escrowReconciliationBoundary(RocketEntity rocket) { return transfers.escrowReconciliationBoundary(rocket); }
    boolean escrowReconciliationBoundary(RocketEntity rocket,
            io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.EscrowGuard guard) {
        return transfers.escrowReconciliationBoundary(rocket, guard);
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
