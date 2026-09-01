package io.github.sunthemoon.advancedrocketrycommunity.rocket.server;

import io.github.sunthemoon.advancedrocketrycommunity.AdvancedRocketryCommunity;
import io.github.sunthemoon.advancedrocketrycommunity.registry.ModEntities;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.entity.RocketEntity;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFlightData;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFlightLimits;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFlightPlan;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFlightRequestCode;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFlightRequestResult;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFlightState;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFuelMutation;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketPassengerSeat;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketTransferPhase;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketTransferInspection;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketTransferRecord;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketTransferRecoveryReport;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.persistence.RocketTransferSavedData;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketStructureSnapshot;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.transaction.RocketRegion;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.transfer.RocketLandingPadSelection;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.transfer.RocketLandingPadSelector;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/** Durable fixed-pad transfer executor and restart recovery owner. */
final class RocketTransferService {
    private final RocketLandingPadSelector pads = new RocketLandingPadSelector();
    private final Set<UUID> liveTransfers = new HashSet<>();
    private final Set<UUID> settledTransfers = new HashSet<>();
    private final RocketTransferRecoveryService recovery = new RocketTransferRecoveryService(
            liveTransfers,
            settledTransfers
    );

    RocketFlightRequestResult prepareLaunch(RocketEntity rocket, RocketFlightData countdown) {
        Objects.requireNonNull(rocket, "rocket");
        Objects.requireNonNull(countdown, "countdown");
        if (!(rocket.level() instanceof ServerLevel sourceLevel) || !rocket.operational()) {
            return RocketFlightRequestResult.failure(RocketFlightRequestCode.ENTITY_UNAVAILABLE);
        }
        RocketFlightPlan plan = countdown.plan().orElseThrow();
        MinecraftServer server = sourceLevel.getServer();
        RocketTransferSavedData journal = RocketTransferSavedData.get(server);
        if (!journal.operational()) {
            return RocketFlightRequestResult.failure(RocketFlightRequestCode.TRANSFER_JOURNAL_BLOCKED);
        }
        RocketTransferRecord previous = journal.findByLogicalRocket(countdown.logicalRocketId()).orElse(null);
        if (journal.find(plan.requestId()).isPresent()
                || (previous != null && !RocketTransferEntities.isLandedAuthority(rocket, previous))) {
            return RocketFlightRequestResult.failure(RocketFlightRequestCode.REQUEST_REPLAYED);
        }
        if (journal.entries().size() >= RocketFlightLimits.MAX_ACTIVE_TRANSFERS) {
            return RocketFlightRequestResult.failure(RocketFlightRequestCode.TRANSFER_LIMIT_REACHED);
        }
        ServerLevel destinationLevel = RocketTransferEntities.level(server, plan.destinationDimension());
        if (destinationLevel == null) {
            return RocketFlightRequestResult.failure(RocketFlightRequestCode.INVALID_DESTINATION);
        }
        List<RocketRegion> reservations = journal.entries().stream()
                .map(RocketTransferRecord::destinationSnapshot)
                .map(RocketRegion::fromSnapshot)
                .toList();
        RocketLandingPadSelection selected = pads.select(
                destinationLevel,
                rocket.snapshot().orElseThrow(),
                plan.requestId(),
                reservations,
                scheduledArrival(countdown)
        );
        if (!selected.success()) {
            auditPrepare(rocket, plan, selected, RocketFlightRequestCode.LANDING_PAD_UNAVAILABLE);
            return RocketFlightRequestResult.failure(
                    RocketFlightRequestCode.LANDING_PAD_UNAVAILABLE,
                    plan.requiredFuel()
            );
        }

        RocketTransferRecord record;
        try {
            RocketFlightData sourceTransit = scheduledTransit(countdown);
            RocketFuelMutation debit = sourceTransit.fuel().debit(plan.requestId(), plan.requiredFuel());
            if (!debit.success()) {
                return RocketFlightRequestResult.failure(
                        RocketFlightRequestCode.INSUFFICIENT_FUEL,
                        plan.requiredFuel()
                );
            }
            RocketStructureSnapshot destinationSnapshot = selected.snapshot().orElseThrow();
            RocketFlightData destinationFlight = sourceTransit.arriveAtDestination(
                    debit.state(),
                    plan.destinationBody(),
                    plan.destinationDimension(),
                    destinationSnapshot.sourceOrigin(),
                    scheduledArrival(countdown)
            );
            record = RocketTransferRecord.create(
                    plan.requestId(),
                    countdown.logicalRocketId(),
                    rocket.ownerId().orElseThrow(),
                    rocket.getUUID(),
                    rocket.snapshot().orElseThrow(),
                    destinationSnapshot,
                    sourceTransit,
                    destinationFlight,
                    plan.requiredFuel(),
                    countdown.stateStartedGameTime()
            );
            if (previous == null) {
                journal.put(record);
            } else {
                journal.replace(previous.transferId(), record);
                liveTransfers.remove(previous.transferId());
                settledTransfers.remove(previous.transferId());
            }
            journal.flush(server);
            RocketTransferEntities.keepLoaded(sourceLevel, record.sourceSnapshot(), record.transferId());
            RocketTransferEntities.keepLoaded(destinationLevel, record.destinationSnapshot(), record.transferId());
            rocket.updateFlightData(countdown);
            RocketFlightFeedback.countdownAccepted(rocket);
            liveTransfers.add(record.transferId());
        } catch (RuntimeException exception) {
            try {
                journal.remove(plan.requestId());
                journal.flush(server);
            } catch (RuntimeException cleanupException) {
                exception.addSuppressed(cleanupException);
            }
            AdvancedRocketryCommunity.LOGGER.error(
                    "ARCE_TRANSFER_PREPARE_FAILED transfer={} logical={} source={}",
                    plan.requestId(),
                    countdown.logicalRocketId(),
                    rocket.getUUID(),
                    exception
            );
            return RocketFlightRequestResult.failure(
                    RocketFlightRequestCode.TRANSFER_PREPARE_FAILED,
                    plan.requiredFuel()
            );
        }
        auditPrepare(rocket, plan, selected, RocketFlightRequestCode.SUCCESS);
        return new RocketFlightRequestResult(RocketFlightRequestCode.SUCCESS, plan.requiredFuel());
    }

    RocketFlightRequestResult cancelCountdown(RocketEntity rocket) {
        Objects.requireNonNull(rocket, "rocket");
        if (!(rocket.level() instanceof ServerLevel level) || !rocket.operational()) {
            return RocketFlightRequestResult.failure(RocketFlightRequestCode.ENTITY_UNAVAILABLE);
        }
        RocketFlightData flight = rocket.flightData().orElseThrow();
        if (flight.state() != RocketFlightState.COUNTDOWN || flight.plan().isEmpty()) {
            return RocketFlightRequestResult.failure(RocketFlightRequestCode.INVALID_STATE);
        }
        RocketFlightPlan plan = flight.plan().orElseThrow();
        RocketTransferSavedData journal = RocketTransferSavedData.get(level.getServer());
        if (!journal.operational()) {
            return RocketFlightRequestResult.failure(RocketFlightRequestCode.TRANSFER_JOURNAL_BLOCKED);
        }
        RocketTransferRecord record = journal.find(plan.requestId()).orElse(null);
        if (record == null
                || record.phase() != RocketTransferPhase.PREPARED
                || !record.sourceEntityId().equals(rocket.getUUID())) {
            return RocketFlightRequestResult.failure(RocketFlightRequestCode.INVALID_STATE);
        }
        failBackToSource(level.getServer(), journal, record, rocket, "countdown_cancelled");
        return new RocketFlightRequestResult(RocketFlightRequestCode.SUCCESS, plan.requiredFuel());
    }

    void tick(MinecraftServer server) {
        Objects.requireNonNull(server, "server");
        RocketTransferSavedData journal = RocketTransferSavedData.get(server);
        if (!journal.operational()) {
            return;
        }
        recovery.recoverNext(server, journal);
        List<UUID> active = liveTransfers.stream().sorted().toList();
        for (UUID transferId : active) {
            RocketTransferRecord record = journal.find(transferId).orElse(null);
            if (record == null) {
                liveTransfers.remove(transferId);
                continue;
            }
            try {
                tickLive(server, journal, record);
            } catch (RuntimeException exception) {
                liveTransfers.remove(transferId);
                AdvancedRocketryCommunity.LOGGER.error(
                        "ARCE_TRANSFER_TICK_FAILED transfer={} phase={}",
                        transferId,
                        record.phase(),
                        exception
                );
            }
        }
    }

    void onPlayerLoggedIn(ServerPlayer player) {
        Objects.requireNonNull(player, "player");
        MinecraftServer server = player.getServer();
        if (server == null) {
            return;
        }
        RocketTransferSavedData journal = RocketTransferSavedData.get(server);
        if (!journal.operational()) {
            return;
        }
        recovery.onPlayerLoggedIn(player, journal);
    }

    int activeCount(MinecraftServer server) {
        RocketTransferSavedData journal = RocketTransferSavedData.get(server);
        return journal.operational() ? journal.entries().size() : -1;
    }

    Optional<RocketTransferInspection> inspect(MinecraftServer server, UUID transferId) {
        Objects.requireNonNull(server, "server");
        Objects.requireNonNull(transferId, "transferId");
        RocketTransferSavedData journal = RocketTransferSavedData.get(server);
        if (!journal.operational()) {
            return Optional.empty();
        }
        return journal.find(transferId).map(record -> new RocketTransferInspection(
                record.transferId(),
                record.logicalRocketId(),
                record.phase(),
                record.sourceSnapshot().sourceDimension(),
                record.destinationSnapshot().sourceDimension(),
                record.sourceEntityId(),
                record.destinationEntityId(),
                record.sourceFlightData().fuel().amount(),
                record.destinationFlightData().fuel().amount(),
                record.sourceFlightData().passengers().assignments().size(),
                record.checksum(),
                RocketTransferEntities.findMatches(server, record, false).size(),
                RocketTransferEntities.findMatches(server, record, true).size()
        ));
    }

    RocketTransferRecoveryReport recover(MinecraftServer server, UUID transferId) {
        Objects.requireNonNull(server, "server");
        Objects.requireNonNull(transferId, "transferId");
        RocketTransferSavedData journal = RocketTransferSavedData.get(server);
        if (!journal.operational()) {
            return new RocketTransferRecoveryReport(
                    RocketTransferRecoveryReport.Status.JOURNAL_BLOCKED,
                    transferId,
                    Optional.empty(),
                    Optional.empty(),
                    0,
                    0
            );
        }
        RocketTransferRecoveryService.Result result = recovery.recoverById(server, transferId);
        return new RocketTransferRecoveryReport(
                RocketTransferRecoveryReport.Status.valueOf(result.status().name()),
                transferId,
                Optional.ofNullable(result.phase()),
                Optional.ofNullable(result.action()),
                result.sourceCount(),
                result.destinationCount()
        );
    }

    void releaseLandedReservation(RocketEntity rocket) {
        Objects.requireNonNull(rocket, "rocket");
        if (!(rocket.level() instanceof ServerLevel level) || !rocket.operational()) {
            return;
        }
        RocketTransferSavedData journal = RocketTransferSavedData.get(level.getServer());
        if (!journal.operational()) {
            return;
        }
        RocketTransferRecord record = journal.findByLogicalRocket(
                rocket.assemblyTransactionId().orElseThrow()
        ).orElse(null);
        if (record != null && RocketTransferEntities.isLandedAuthority(rocket, record)) {
            journal.remove(record.transferId());
            journal.flush(level.getServer());
            liveTransfers.remove(record.transferId());
            settledTransfers.remove(record.transferId());
            auditPhase(record, "landed_reservation_released", rocket.getUUID());
        }
    }

    void clear() {
        liveTransfers.clear();
        settledTransfers.clear();
    }

    private void tickLive(
            MinecraftServer server,
            RocketTransferSavedData journal,
            RocketTransferRecord record
    ) {
        ServerLevel sourceLevel = RocketTransferEntities.level(server, record.sourceSnapshot().sourceDimension());
        ServerLevel destinationLevel = RocketTransferEntities.level(
                server,
                record.destinationSnapshot().sourceDimension()
        );
        if (sourceLevel != null && !record.phase().destinationAuthoritative()) {
            RocketTransferEntities.keepLoaded(sourceLevel, record.sourceSnapshot(), record.transferId());
        }
        if (destinationLevel != null) {
            RocketTransferEntities.keepLoaded(destinationLevel, record.destinationSnapshot(), record.transferId());
        }
        switch (record.phase()) {
            case PREPARED -> tickSource(server, journal, record);
            case DESTINATION_SPAWNED -> transferPassengers(server, journal, record);
            case PASSENGERS_TRANSFERRED -> removeSource(server, journal, record);
            case SOURCE_REMOVED -> updatePhase(server, journal, record.advance(RocketTransferPhase.COMMITTED));
            case COMMITTED -> tickDestination(server, journal, record);
        }
    }

    private void tickSource(
            MinecraftServer server,
            RocketTransferSavedData journal,
            RocketTransferRecord record
    ) {
        RocketEntity source = RocketTransferEntities.findSource(server, record);
        if (source == null) {
            liveTransfers.remove(record.transferId());
            return;
        }
        RocketFlightData flight = source.flightData().orElseThrow();
        long now = source.level().getGameTime();
        if (flight.state() == RocketFlightState.COUNTDOWN
                && elapsed(now, flight.stateStartedGameTime()) >= RocketFlightLimits.COUNTDOWN_TICKS) {
            source.updateFlightData(flight.completeCountdown(now));
            flight = source.flightData().orElseThrow();
            RocketFlightFeedback.ascentStarted(source);
            auditPhase(record, "countdown_complete", source.getUUID());
        }
        if (flight.state() == RocketFlightState.ASCENT) {
            long elapsed = elapsed(now, flight.stateStartedGameTime());
            RocketTransferEntities.positionAscent(source, record.sourceSnapshot().sourceOrigin(), elapsed);
            RocketFlightFeedback.ascentTrail(source, elapsed);
            if (elapsed >= RocketFlightLimits.ASCENT_TICKS) {
                source.updateFlightData(record.sourceFlightData());
                RocketTransferEntities.positionAtAltitude(source, record.sourceSnapshot().sourceOrigin());
                flight = source.flightData().orElseThrow();
                RocketFlightFeedback.transitStarted(source);
                auditPhase(record, "ascent_complete", source.getUUID());
            }
        }
        if (flight.state() == RocketFlightState.TRANSIT
                && elapsed(now, flight.stateStartedGameTime()) >= RocketFlightLimits.TRANSIT_TICKS) {
            spawnDestination(server, journal, record, source);
        } else if (flight.state() != RocketFlightState.COUNTDOWN
                && flight.state() != RocketFlightState.ASCENT
                && flight.state() != RocketFlightState.TRANSIT) {
            liveTransfers.remove(record.transferId());
        }
    }

    private void spawnDestination(
            MinecraftServer server,
            RocketTransferSavedData journal,
            RocketTransferRecord record,
            RocketEntity source
    ) {
        ServerLevel destinationLevel = RocketTransferEntities.level(
                server,
                record.destinationSnapshot().sourceDimension()
        );
        if (destinationLevel == null
                || !pads.available(destinationLevel, record.destinationSnapshot(), null, false)) {
            failBackToSource(server, journal, record, source, "destination_pad_blocked");
            return;
        }
        RocketEntity destination = ModEntities.ROCKET.get().create(destinationLevel);
        if (destination == null) {
            failBackToSource(server, journal, record, source, "destination_entity_create_failed");
            return;
        }
        destination.initializeTransferred(
                record.destinationSnapshot(),
                record.logicalRocketId(),
                record.ownerId(),
                record.destinationFlightData()
        );
        RocketTransferEntities.positionAtAltitude(destination, record.destinationSnapshot().sourceOrigin());
        if (!destinationLevel.addFreshEntity(destination)) {
            failBackToSource(server, journal, record, source, "destination_entity_spawn_failed");
            return;
        }
        RocketTransferRecord spawned = record.destinationSpawned(destination.getUUID());
        try {
            updatePhase(server, journal, spawned);
            RocketFlightFeedback.destinationSpawned(destination);
        } catch (RuntimeException exception) {
            destination.discard();
            throw exception;
        }
    }

    private void transferPassengers(
            MinecraftServer server,
            RocketTransferSavedData journal,
            RocketTransferRecord record
    ) {
        RocketEntity destination = RocketTransferEntities.findDestination(server, record);
        if (destination == null) {
            liveTransfers.remove(record.transferId());
            return;
        }
        for (RocketPassengerSeat seat : record.destinationFlightData().passengers().assignments()) {
            ServerPlayer passenger = server.getPlayerList().getPlayer(seat.passengerId());
            if (passenger != null) {
                RocketTransferEntities.movePassenger(
                        passenger,
                        destination,
                        record.destinationSnapshot().sourceOrigin()
                );
            }
        }
        updatePhase(
                server,
                journal,
                record.advance(RocketTransferPhase.PASSENGERS_TRANSFERRED)
        );
    }

    private void removeSource(
            MinecraftServer server,
            RocketTransferSavedData journal,
            RocketTransferRecord record
    ) {
        RocketEntity source = RocketTransferEntities.findSource(server, record);
        if (source != null) {
            source.discard();
        }
        updatePhase(server, journal, record.advance(RocketTransferPhase.SOURCE_REMOVED));
    }

    private void tickDestination(
            MinecraftServer server,
            RocketTransferSavedData journal,
            RocketTransferRecord record
    ) {
        RocketEntity destination = RocketTransferEntities.findDestination(server, record);
        if (destination == null) {
            liveTransfers.remove(record.transferId());
            return;
        }
        RocketFlightData flight = destination.flightData().orElseThrow();
        if (flight.state() == RocketFlightState.DESCENT) {
            long elapsed = elapsed(destination.level().getGameTime(), flight.stateStartedGameTime());
            RocketTransferEntities.positionDescent(
                    destination,
                    record.destinationSnapshot().sourceOrigin(),
                    elapsed
            );
            RocketFlightFeedback.descentTrail(destination, elapsed);
            if (elapsed >= RocketFlightLimits.DESCENT_TICKS) {
                destination.updateFlightData(flight.land(destination.level().getGameTime()));
                RocketTransferEntities.positionAtOrigin(destination, record.destinationSnapshot().sourceOrigin());
                RocketFlightFeedback.landed(destination);
                auditPhase(record, "landing_complete", destination.getUUID());
            }
        }
        cleanupLandedIfComplete(server, journal, record, destination);
    }

    private void cleanupLandedIfComplete(
            MinecraftServer server,
            RocketTransferSavedData journal,
            RocketTransferRecord record,
            RocketEntity destination
    ) {
        if (destination.flightData().map(RocketFlightData::state).orElse(RocketFlightState.FAILED_RECOVERABLE)
                != RocketFlightState.LANDED) {
            return;
        }
        boolean allOnline = record.destinationFlightData().passengers().assignments().stream()
                .allMatch(seat -> server.getPlayerList().getPlayer(seat.passengerId()) != null);
        if (allOnline) {
            liveTransfers.remove(record.transferId());
            settledTransfers.add(record.transferId());
            auditPhase(record, "landed_reservation_retained", destination.getUUID());
        }
    }

    private void failBackToSource(
            MinecraftServer server,
            RocketTransferSavedData journal,
            RocketTransferRecord record,
            RocketEntity source,
            String reason
    ) {
        RocketFlightData safe = RocketTransferEntities.stationarySource(
                record,
                source.level().getGameTime()
        );
        source.updateFlightData(safe);
        RocketTransferEntities.positionAtOrigin(source, record.sourceSnapshot().sourceOrigin());
        RocketFlightFeedback.returnedToSource(source);
        RocketTransferEntities.remountOnlinePassengers(
                server,
                record,
                source,
                record.sourceSnapshot().sourceOrigin()
        );
        journal.remove(record.transferId());
        journal.flush(server);
        liveTransfers.remove(record.transferId());
        AdvancedRocketryCommunity.LOGGER.warn(
                "ARCE_TRANSFER_RETURNED_TO_SOURCE transfer={} logical={} reason={} fuel={}",
                record.transferId(),
                record.logicalRocketId(),
                reason,
                safe.fuel().amount()
        );
    }

    private void updatePhase(
            MinecraftServer server,
            RocketTransferSavedData journal,
            RocketTransferRecord updated
    ) {
        journal.put(updated);
        journal.flush(server);
        liveTransfers.add(updated.transferId());
        auditPhase(updated, updated.phase().name().toLowerCase(java.util.Locale.ROOT),
                updated.destinationEntityId().orElse(updated.sourceEntityId()));
    }

    private static RocketFlightData scheduledTransit(RocketFlightData countdown) {
        long ascentStarted = Math.addExact(
                countdown.stateStartedGameTime(),
                RocketFlightLimits.COUNTDOWN_TICKS
        );
        long transitStarted = Math.addExact(ascentStarted, RocketFlightLimits.ASCENT_TICKS);
        return countdown.completeCountdown(ascentStarted)
                .beginTransit(countdown.plan().orElseThrow().requestId(), transitStarted);
    }

    private static long scheduledArrival(RocketFlightData countdown) {
        return Math.addExact(
                scheduledTransit(countdown).stateStartedGameTime(),
                RocketFlightLimits.TRANSIT_TICKS
        );
    }

    private static long elapsed(long gameTime, long started) {
        return gameTime <= started ? 0L : gameTime - started;
    }

    private static void auditPrepare(
            RocketEntity rocket,
            RocketFlightPlan plan,
            RocketLandingPadSelection selection,
            RocketFlightRequestCode code
    ) {
        AdvancedRocketryCommunity.LOGGER.info(
                "ARCE_TRANSFER_PREPARE transfer={} logical={} source={} destination={} code={} candidates={} chunks={} detail={}",
                plan.requestId(),
                rocket.assemblyTransactionId().orElse(null),
                rocket.level().dimension().location(),
                plan.destinationDimension(),
                code,
                selection.candidatesChecked(),
                selection.chunksLoaded(),
                selection.detail()
        );
    }

    private static void auditPhase(RocketTransferRecord record, String event, UUID entityId) {
        AdvancedRocketryCommunity.LOGGER.info(
                "ARCE_TRANSFER_PHASE transfer={} logical={} phase={} event={} entity={} fuel_before={} fuel_after={} required={}",
                record.transferId(),
                record.logicalRocketId(),
                record.phase(),
                event,
                entityId,
                record.sourceFlightData().fuel().amount(),
                record.destinationFlightData().fuel().amount(),
                record.requiredFuel()
        );
    }
}
