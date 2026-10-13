package io.github.sunthemoon.advancedrocketrycommunity.rocket.server;

import static io.github.sunthemoon.advancedrocketrycommunity.rocket.server.RocketTransferAudit.auditPhase;
import static io.github.sunthemoon.advancedrocketrycommunity.rocket.server.RocketTransferAudit.auditPrepare;
import static io.github.sunthemoon.advancedrocketrycommunity.rocket.server.RocketTransferBayRecoveryGate.Action.*;

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
import io.github.sunthemoon.advancedrocketrycommunity.station.model.StationState;
import io.github.sunthemoon.advancedrocketrycommunity.station.persistence.StationRegistrySavedData;
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
    private final io.github.sunthemoon.advancedrocketrycommunity.rocket.transfer.NamedLandingPadStaging namedPads =
            new io.github.sunthemoon.advancedrocketrycommunity.rocket.transfer.NamedLandingPadStaging();
    private final Set<UUID> liveTransfers = new HashSet<>();
    private final Set<UUID> settledTransfers = new HashSet<>();
    // BEGIN transfer service diagnostics
    private final TransferFailureDiagnostics diagnostics = new TransferFailureDiagnostics();
    // END transfer service diagnostics
    private final RocketTransferRecoveryService recovery = new RocketTransferRecoveryService(
            liveTransfers,
            settledTransfers
    );

    RocketFlightRequestResult prepareLaunch(RocketEntity rocket, RocketFlightData countdown) {
        return prepareLaunch(rocket, countdown, () -> { });
    }

    RocketFlightRequestResult prepareLaunch(RocketEntity rocket, RocketFlightData countdown, Runnable accepted) {
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
        if (!resourceMutationAllowed(rocket)) {
            return RocketFlightRequestResult.failure(RocketFlightRequestCode.TRANSFER_JOURNAL_BLOCKED);
        }
        RocketTransferRecord previous = journal.findByLogicalRocket(countdown.logicalRocketId()).orElse(null);
        if (journal.find(plan.requestId()).isPresent()
                || (previous != null
                && !RocketTransferEntities.isReplaceableLandedAuthority(rocket, previous))) {
            return RocketFlightRequestResult.failure(RocketFlightRequestCode.REQUEST_REPLAYED);
        }
        if (previous == null && journal.entries().size() >= RocketFlightLimits.MAX_ACTIVE_TRANSFERS) {
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
        RocketLandingPadSelection selected;
        if (plan.destinationStation().isPresent()) {
            StationState station = StationRegistrySavedData.get(server)
                    .find(plan.destinationStation().orElseThrow())
                    .orElse(null);
            if (station == null) {
                return RocketFlightRequestResult.failure(RocketFlightRequestCode.INVALID_DESTINATION);
            }
            selected = plan.landingPad() != null ? namedPads.select(destinationLevel,
                    rocket.snapshot().orElseThrow(), plan.requestId(), station, plan.landingPad(), reservations,
                    scheduledArrival(countdown)) : pads.selectStation(
                    destinationLevel,
                    rocket.snapshot().orElseThrow(),
                    plan.requestId(),
                    station,
                    reservations,
                    scheduledArrival(countdown)
            );
        } else {
            selected = pads.select(
                    destinationLevel,
                    rocket.snapshot().orElseThrow(),
                    plan.requestId(),
                    reservations,
                    scheduledArrival(countdown)
            );
        }
        if (!selected.success()) {
            auditPrepare(rocket, plan, selected, RocketFlightRequestCode.LANDING_PAD_UNAVAILABLE);
            return new RocketFlightRequestResult(
                    RocketFlightRequestCode.LANDING_PAD_UNAVAILABLE,
                    plan.requiredFuel(), selected.refusalKey()
            );
        }

        RocketTransferRecord record;
        try {
            RocketFlightData sourceTransit = scheduledTransit(countdown);
            RocketFuelMutation debit = sourceTransit.fuel().debit(plan);
            if (!debit.success()) {
                io.github.sunthemoon.advancedrocketrycommunity.station.pad.LandingPadService.release(server, plan);
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
            }
            if (!journal.afterAcknowledged(server, record.logicalRocketId(), () -> {
                if (!rocket.isAlive() || !flightAllowed(server, journal, record, TICK_SOURCE)) { return; }
                if (previous != null) { liveTransfers.remove(previous.transferId()); settledTransfers.remove(previous.transferId()); }
                RocketTransferEntities.keepLoaded(sourceLevel, record.sourceSnapshot(), record.transferId());
                RocketTransferEntities.keepLoaded(destinationLevel, record.destinationSnapshot(), record.transferId());
                rocket.updateFlightData(countdown.cancelCountdown(sourceLevel.getGameTime()).startCountdown(sourceLevel.getGameTime()));
                RocketFlightFeedback.countdownAccepted(rocket);
                liveTransfers.add(record.transferId()); diagnostics.prepared(record.transferId());
                auditPrepare(rocket, plan, selected, RocketFlightRequestCode.SUCCESS);
                accepted.run();
            })) { return RocketFlightRequestResult.failure(RocketFlightRequestCode.TRANSFER_JOURNAL_BLOCKED); }
        } catch (RuntimeException exception) {
            io.github.sunthemoon.advancedrocketrycommunity.station.pad.LandingPadService.release(server, plan);
            // Retain any captured authority, including uncertain replacement. Recovery may retry it.
            liveTransfers.remove(plan.requestId());
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
        return new RocketFlightRequestResult(RocketFlightRequestCode.JOURNAL_PENDING, plan.requiredFuel());
    }

    RocketFlightRequestResult cancelCountdown(RocketEntity rocket) {
        return cancelCountdown(rocket, () -> { });
    }

    RocketFlightRequestResult cancelCountdown(RocketEntity rocket, Runnable accepted) {
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
        if (!io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.RocketEscrowGate.idle(rocket)) {
            return RocketFlightRequestResult.failure(RocketFlightRequestCode.TRANSFER_JOURNAL_BLOCKED);
        }
        if (record == null
                || record.phase() != RocketTransferPhase.PREPARED
                || !record.sourceEntityId().equals(rocket.getUUID())) {
            return RocketFlightRequestResult.failure(RocketFlightRequestCode.INVALID_STATE);
        }
        failBackToSource(level.getServer(), journal, record, rocket, RocketTransferReturnReason.COUNTDOWN_CANCELLED, accepted);
        return new RocketFlightRequestResult(journal.acknowledgedCurrent() ? RocketFlightRequestCode.SUCCESS
                : RocketFlightRequestCode.JOURNAL_PENDING, plan.requiredFuel());
    }

    void tick(MinecraftServer server) {
        Objects.requireNonNull(server, "server");
        // BEGIN transfer service diagnostics
        diagnostics.enterTick(server.getTickCount());
        // END transfer service diagnostics
        RocketTransferSavedData journal = RocketTransferSavedData.get(server);
        if (!journal.operational()) {
            return;
        }
        recovery.classify(server, journal);
        if ((journal.isDirty() || !journal.entries().isEmpty()) && !journal.retryChecked(server)) { return; }
        recovery.recoverNext(server, journal);
        recovery.tickReconnects(server, journal);
        List<UUID> active = liveTransfers.stream().sorted().toList();
        for (UUID transferId : active) {
            RocketTransferRecord record = journal.find(transferId).orElse(null);
            if (record == null) {
                liveTransfers.remove(transferId);
                continue;
            }
            try {
                // BEGIN transfer service diagnostics
                diagnostics.dispatch(transferId);
                // END transfer service diagnostics
                tickLive(server, journal, record);
            } catch (RuntimeException exception) {
                // BEGIN transfer service diagnostics
                diagnostics.branch(transferId, TransferFailureDiagnostics.Branch.TICK_EXCEPTION);
                // END transfer service diagnostics
                liveTransfers.remove(transferId);
                AdvancedRocketryCommunity.LOGGER.error(
                        "ARCE_TRANSFER_TICK_FAILED transfer={} phase={}",
                        transferId,
                        record.phase(),
                        exception
                );
            }
        }
        // BEGIN transfer service diagnostics
        diagnostics.completeTick();
        // END transfer service diagnostics
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

    void onPlayerLoggedOut(UUID playerId) {
        recovery.onPlayerLoggedOut(playerId);
    }

    /**
     * ADR-044 §5 in-motion rule for one station region in the Space Level: journal records plus this
     * session's classification (live launches and descents, settled sources and landings).
     */
    boolean stationRegionInMotion(MinecraftServer server, int minX, int minZ, int maxX, int maxZ) {
        Objects.requireNonNull(server, "server");
        recovery.classify(server, RocketTransferSavedData.get(server));
        Set<UUID> moving = recovery.bays().moving();
        return stationRegionInMotion(RocketTransferSavedData.get(server), moving, settledTransfers,
                server.overworld().getGameTime(),
                io.github.sunthemoon.advancedrocketrycommunity.celestial.CelestialIds.SPACE_LEVEL.location(),
                minX, minZ, maxX, maxZ);
    }

    /**
     * The wiring of {@link RocketStationMotionRule}: classified means live or settled this session, and
     * only live records count as moving {@code PREPARED} records. Package-private for tests.
     */
    static boolean stationRegionInMotion(RocketTransferSavedData journal, Set<UUID> live, Set<UUID> settled,
                                         long gameTime, net.minecraft.resources.ResourceLocation dimension,
                                         int minX, int minZ, int maxX, int maxZ) {
        return RocketStationMotionRule.blocks(
                journal.operational(),
                journal.entries(),
                transferId -> live.contains(transferId) || settled.contains(transferId),
                live::contains,
                gameTime,
                new RocketStationMotionRule.Region(dimension, minX, minZ, maxX, maxZ)
        );
    }

    /** Operator line for the warp in-motion rule: journal state and this session's classification. */
    String journalDiagnostics(MinecraftServer server) {
        recovery.classify(server, RocketTransferSavedData.get(server));
        Set<UUID> classifiedLive = recovery.bays().moving();
        classifiedLive.addAll(settledTransfers);
        return RocketTransferSessionDiagnostics.summary(RocketTransferSavedData.get(server), liveTransfers, settledTransfers, classifiedLive)
                + " fenced=" + recovery.fencedIds().size() + recovery.fencedDiagnostics()
                + recovery.bays().diagnostics(RocketTransferSavedData.get(server));
    }

    void onRocketJoined(RocketEntity rocket) { recovery.onRocketJoined(rocket); }
    void installBayRecoveryGate(RocketTransferBayRecoveryGate gate) { recovery.bays().install(gate); }
    void classifyBayRecovery(MinecraftServer server, RocketTransferSavedData journal) { recovery.classify(server, journal); }

    // BEGIN transfer service diagnostics
    String transferFailureDiagnostics(UUID transferId, boolean active) {
        return diagnostics.snapshot(transferId, active,
                liveTransfers.contains(transferId), settledTransfers.contains(transferId));
    }
    // END transfer service diagnostics

    /**
     * One bounded line. Unclassified records (which block every warp, ADR-044 §5 rule 2) are listed, at
     * most four, with both ends, so an operator can load one end and run {@code /arce rocket recover}.
     */
    static String journalDiagnostics(RocketTransferSavedData journal, Set<UUID> live, Set<UUID> settled) {
        return RocketTransferSessionDiagnostics.summary(journal, live, settled);
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
                RocketTransferEntities.loadedMatches(server, record, false).size(),
                RocketTransferEntities.loadedMatches(server, record, true).size()
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

    /** No endpoint may mutate a balance that restart recovery can replace from its journal. */
    boolean resourceMutationAllowed(RocketEntity rocket) {
        if (!io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.RocketEscrowGate.idle(rocket)) { return false; }
        if (!(rocket.level() instanceof ServerLevel level) || !level.getServer().isSameThread()
                || !rocket.operational() || rocket.flightData().orElseThrow().activeTransferId().isPresent()) {
            return false;
        }
        var journal = RocketTransferSavedData.get(level.getServer());
        if (!journal.canQueueEffect() || journal.resourceMutationUnproved(rocket.assemblyTransactionId().orElseThrow())) { return false; }
        var record = journal.findByLogicalRocket(rocket.assemblyTransactionId().orElseThrow()).orElse(null);
        return resourceMutationAllowed(journal.operational(), record == null ? null : record.phase(),
                record != null && settledTransfers.contains(record.transferId()) && !liveTransfers.contains(record.transferId()),
                record != null && RocketTransferEntities.isReplaceableLandedAuthority(rocket, record));
    }

    static boolean resourceMutationAllowed(boolean operational, RocketTransferPhase phase,
            boolean settled, boolean stationaryArrival) {
        return operational && (phase == null || phase == RocketTransferPhase.COMMITTED && settled && stationaryArrival);
    }

    boolean releaseLandedReservation(RocketEntity rocket) {
        Objects.requireNonNull(rocket, "rocket");
        if (!(rocket.level() instanceof ServerLevel level) || !rocket.operational()) {
            return false;
        }
        RocketTransferSavedData journal = RocketTransferSavedData.get(level.getServer());
        if (!journal.operational()) {
            return false;
        }
        RocketTransferRecord record = journal.findByLogicalRocket(
                rocket.assemblyTransactionId().orElseThrow()
        ).orElse(null);
        if (record != null && RocketTransferEntities.isReplaceableLandedAuthority(rocket, record)) {
            if (!recovery.bays().allowed(level.getServer(), journal, record, REMOVE_JOURNAL)) { return false; }
            journal.remove(record.transferId());
            journal.afterAcknowledged(level.getServer(), record.logicalRocketId(), () -> {
                if (!recovery.bays().allowed(level.getServer(), journal, record, REMOVE_JOURNAL)) { return; }
                liveTransfers.remove(record.transferId()); settledTransfers.remove(record.transferId());
                auditPhase(record, "landed_reservation_released", rocket.getUUID());
            });
            return false;
        }
        return !journal.resourceMutationUnproved(rocket.assemblyTransactionId().orElseThrow());
    }

    void clear() {
        liveTransfers.clear();
        settledTransfers.clear();
        recovery.clear();
        // BEGIN transfer service diagnostics
        diagnostics.clear();
        // END transfer service diagnostics
    }

    void tickLive(
            MinecraftServer server,
            RocketTransferSavedData journal,
            RocketTransferRecord record
    ) {
        if (!flightAllowed(server, journal, record, TICK_SOURCE) || !journal.acknowledgedCurrent()) { return; }
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
        UUID readySource = recovery.bays().source(record.transferId());
        RocketEntity source = readySource == null ? RocketTransferEntities.findSource(server, record)
                : RocketTransferEntities.loadedMatches(server, record, false).stream()
                .filter(candidate -> readySource.equals(candidate.getUUID())).findFirst().orElse(null);
        if (source == null) {
            // BEGIN transfer service diagnostics
            diagnostics.branch(record.transferId(), TransferFailureDiagnostics.Branch.SOURCE_UNAVAILABLE);
            // END transfer service diagnostics
            liveTransfers.remove(record.transferId());
            return;
        }
        RocketFlightData flight = source.flightData().orElseThrow();
        long now = source.level().getGameTime();
        // BEGIN transfer service diagnostics
        if (flight.state() == RocketFlightState.TRANSIT) {
            diagnostics.branch(record.transferId(), TransferFailureDiagnostics.Branch.TRANSIT_NOT_DUE);
        }
        // END transfer service diagnostics
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
                source.updateFlightData(flight.beginTransit(record.transferId(), now));
                RocketTransferEntities.positionAtAltitude(source, record.sourceSnapshot().sourceOrigin());
                flight = source.flightData().orElseThrow();
                RocketFlightFeedback.transitStarted(source);
                auditPhase(record, "ascent_complete", source.getUUID());
                recovery.classify(server, journal);
            }
        }
        if (flight.state() == RocketFlightState.TRANSIT
                && elapsed(now, flight.stateStartedGameTime()) >= RocketFlightLimits.TRANSIT_TICKS) {
            spawnDestination(server, journal, record, source);
        } else if (flight.state() != RocketFlightState.COUNTDOWN
                && flight.state() != RocketFlightState.ASCENT
                && flight.state() != RocketFlightState.TRANSIT) {
            // BEGIN transfer service diagnostics
            diagnostics.branch(record.transferId(), TransferFailureDiagnostics.Branch.SOURCE_STATE_UNEXPECTED);
            // END transfer service diagnostics
            liveTransfers.remove(record.transferId());
        }
    }

    void spawnDestination(
            MinecraftServer server,
            RocketTransferSavedData journal,
            RocketTransferRecord record,
            RocketEntity source
    ) {
        if (!flightAllowed(server, journal, record, SPAWN_DESTINATION) || !journal.acknowledgedCurrent()) { return; }
        ServerLevel destinationLevel = RocketTransferEntities.level(
                server,
                record.destinationSnapshot().sourceDimension()
        );
        // Block availability precedes entity visibility/ticking during an asynchronous chunk load.
        // Retain PREPARED source authority until the exact destination origin is ready.
        // BEGIN transfer service diagnostics
        diagnostics.branch(record.transferId(), TransferFailureDiagnostics.Branch.DESTINATION_CHECK);
        // END transfer service diagnostics
        if (destinationLevel != null
                && !RocketTransferEntities.destinationEntityChunkReady(
                        destinationLevel, record.destinationSnapshot())) {
            // BEGIN transfer service diagnostics
            diagnostics.readinessWait(record.transferId());
            // END transfer service diagnostics
            return;
        }
        var arrivalPlan = record.sourceFlightData().plan().orElseThrow();
        boolean landingAvailable = destinationLevel != null && (arrivalPlan.landingPad() == null
                ? pads.available(destinationLevel, record.destinationSnapshot(), null, false)
                : namedPads.recheck(destinationLevel, record.destinationSnapshot(), arrivalPlan.destinationStationId(),
                        arrivalPlan.landingPad(), record.transferId()));
        if (!landingAvailable) {
            // BEGIN transfer service diagnostics
            diagnostics.branch(record.transferId(), TransferFailureDiagnostics.Branch.DESTINATION_UNAVAILABLE);
            // END transfer service diagnostics
            failBackToSource(server, journal, record, source, RocketTransferReturnReason.DESTINATION_PAD_BLOCKED, () -> { });
            return;
        }
        RocketEntity destination = ModEntities.ROCKET.get().create(destinationLevel);
        // BEGIN transfer service diagnostics
        diagnostics.branch(record.transferId(), TransferFailureDiagnostics.Branch.SPAWN_ATTEMPT);
        // END transfer service diagnostics
        if (destination == null) {
            failBackToSource(server, journal, record, source, RocketTransferReturnReason.DESTINATION_ENTITY_CREATE_FAILED, () -> { });
            return;
        }
        destination.initializeTransferred(
                record.destinationSnapshot(),
                record.logicalRocketId(),
                record.ownerId(),
                record.destinationFlightData()
        );
        RocketTransferEntities.positionAtAltitude(destination, record.destinationSnapshot().sourceOrigin());
        RocketTransferRecord spawned = record.destinationSpawned(destination.getUUID());
        updatePhase(server, journal, spawned, SPAWN_DESTINATION, () -> {
            destination.updateFlightData(RocketTransferEntities.currentArrival(record, destinationLevel.getGameTime()));
            if (!destinationLevel.addFreshEntity(destination)) {
                failBackToSource(server, journal, record, source, RocketTransferReturnReason.DESTINATION_ENTITY_SPAWN_FAILED, () -> { });
                return;
            }
            RocketFlightFeedback.destinationSpawned(destination);
        });
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
        updatePhase(server, journal, record.advance(RocketTransferPhase.PASSENGERS_TRANSFERRED), () -> {
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
        });
    }

    private void removeSource(
            MinecraftServer server,
            RocketTransferSavedData journal,
            RocketTransferRecord record
    ) {
        updatePhase(server, journal, record.advance(RocketTransferPhase.SOURCE_REMOVED), () -> {
            RocketEntity source = RocketTransferEntities.findSource(server, record);
            if (source != null) { source.discard(); }
        });
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
                io.github.sunthemoon.advancedrocketrycommunity.progression.classic.ClassicAdvancementEvents
                        .rocketLanded((ServerLevel) destination.level(), RocketTransferEntities.aboard(destination));
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
        if (!flightAllowed(server, journal, record, TICK_SOURCE)) { return; }
        if (!RocketTransferEntities.isReplaceableLandedAuthority(destination, record)) {
            return;
        }
        if (record.schemaVersion() == 1) {
            Optional<RocketTransferRecord> migrated = RocketRuntime.migrateCommittedLegacyTransfer(
                    server,
                    record
            );
            if (migrated.isPresent()) {
                record = migrated.orElseThrow();
                journal.migrateCommitted(record);
                RocketTransferRecord changed = record;
                journal.afterAcknowledged(server, changed.logicalRocketId(), () -> {
                    if (!flightAllowed(server, journal, changed, TICK_SOURCE)) { return; }
                    liveTransfers.add(changed.transferId());
                    AdvancedRocketryCommunity.LOGGER.info("ARCE_TRANSFER_SCHEMA_MIGRATED transfer={} schema={}",
                            changed.transferId(), changed.schemaVersion());
                });
                return;
            } else {
                AdvancedRocketryCommunity.LOGGER.warn(
                        "ARCE_TRANSFER_SCHEMA_MIGRATION_BLOCKED transfer={} phase={}",
                        record.transferId(),
                        record.phase()
                );
            }
        }
        boolean allOnline = destination.flightData().orElseThrow().passengers().assignments().stream()
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
            RocketTransferReturnReason reason, Runnable accepted
    ) {
        if (!flightAllowed(server, journal, record, SETTLE_SOURCE)
                || !recovery.bays().allowed(server, journal, record, REMOVE_JOURNAL)) { return; }
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
        journal.afterAcknowledged(server, record.logicalRocketId(), () -> {
            if (!recovery.bays().allowed(server, journal, record, REMOVE_JOURNAL)) { return; }
            liveTransfers.remove(record.transferId());
            io.github.sunthemoon.advancedrocketrycommunity.station.pad.LandingPadService.release(server, record);
            reason.log(
                AdvancedRocketryCommunity.LOGGER,
                record.transferId(),
                record.logicalRocketId(),
                safe.fuel().amount()
            );
            accepted.run();
        });
    }

    private boolean updatePhase(
            MinecraftServer server,
            RocketTransferSavedData journal,
            RocketTransferRecord updated
    ) {
        return updatePhase(server, journal, updated, () -> { });
    }

    private boolean updatePhase(MinecraftServer server, RocketTransferSavedData journal,
                                RocketTransferRecord updated, Runnable effect) {
        return updatePhase(server, journal, updated, TICK_SOURCE, effect);
    }

    private boolean updatePhase(MinecraftServer server, RocketTransferSavedData journal,
            RocketTransferRecord updated, RocketTransferBayRecoveryGate.Action action, Runnable effect) {
        if (!flightAllowed(server, journal, updated, action)) { return false; }
        journal.put(updated);
        return journal.afterAcknowledged(server, updated.logicalRocketId(), () -> {
            if (!flightAllowed(server, journal, updated, action)) { return; }
            liveTransfers.add(updated.transferId());
            if (updated.phase() == RocketTransferPhase.COMMITTED) {
                io.github.sunthemoon.advancedrocketrycommunity.station.pad.LandingPadService.release(server, updated);
            }
            auditPhase(updated, updated.phase().name().toLowerCase(java.util.Locale.ROOT),
                updated.destinationEntityId().orElse(updated.sourceEntityId()));
            effect.run();
        });
    }

    private boolean flightAllowed(MinecraftServer server, RocketTransferSavedData journal, RocketTransferRecord record,
            RocketTransferBayRecoveryGate.Action action) {
        return RocketTransferEscrowRules.flightAllowed(server, journal, record, recovery.bays(), action);
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

}
