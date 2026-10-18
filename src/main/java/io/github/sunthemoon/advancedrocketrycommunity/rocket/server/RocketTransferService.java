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
import io.github.sunthemoon.advancedrocketrycommunity.rocket.validation.RocketValidationCode;
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
    final RocketDestinationWarmups warmups = new RocketDestinationWarmups(); // W90b: RAM only, never saved
    private final RocketGovernedSourceDispatcher governedSources = new RocketGovernedSourceDispatcher(warmups::handover);
    private final RocketGovernedCleanupDispatcher governedCleanup = new RocketGovernedCleanupDispatcher();
    // BEGIN transfer service diagnostics
    private final TransferFailureDiagnostics diagnostics = new TransferFailureDiagnostics();
    // END transfer service diagnostics
    private final RocketTransferRecoveryService recovery = new RocketTransferRecoveryService(
            liveTransfers,
            settledTransfers
    );
    /** W44 N6: the COMMITTED destination tick and its world-first Moon completion. */
    private final RocketDestinationCompletion destinations = new RocketDestinationCompletion(
            liveTransfers, settledTransfers, recovery.bays());
    private final RocketTransferContinuations continuations = new RocketTransferContinuations(liveTransfers, recovery.bays());
    /** W85 Design M: pending landed disassemblies keep T's region and slot while T is absent from RAM. */
    private final RocketLandedDisassemblies landed = new RocketLandedDisassemblies();
    private final RocketArrivalFootprintWait arrivalFootprints = new RocketArrivalFootprintWait(liveTransfers); // W83b

    RocketFlightRequestResult prepareLaunch(RocketEntity rocket, RocketFlightData countdown) {
        return prepareLaunch(rocket, countdown, RocketRequestCompletion.NONE);
    }

    RocketFlightRequestResult prepareLaunch(RocketEntity rocket, RocketFlightData countdown, RocketRequestCompletion completion) {
        return prepare(rocket, countdown, warmups.observe(rocket, countdown, completion)); // W90b: R22 S4's discard
    }

    private RocketFlightRequestResult prepare(RocketEntity rocket, RocketFlightData countdown, RocketRequestCompletion completion) {
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
        if (previous == null && journal.entries().size() + landed.heldSlots(journal) >= RocketFlightLimits.MAX_ACTIVE_TRANSFERS) {
            return RocketFlightRequestResult.failure(RocketFlightRequestCode.TRANSFER_LIMIT_REACHED);
        }
        ServerLevel destinationLevel = RocketTransferEntities.level(server, plan.destinationDimension());
        if (destinationLevel == null) {
            return RocketFlightRequestResult.failure(RocketFlightRequestCode.INVALID_DESTINATION);
        }
        // W90: governed-only source and pad bounds. W90b: a live warm-up's starting request and its kept pad (R22 S1, S2).
        var admission = RocketGovernedAdmission.check(server, rocket, countdown, warmups);
        if (admission.refusal() != null) { return admission.refusal(); }
        List<RocketRegion> reservations = warmups.reservations(launchReservations(server, journal), countdown.logicalRocketId());
        RocketLandingPadSelection selected;
        if (plan.destinationStation().isPresent()) {
            StationState station = StationRegistrySavedData.get(server).find(plan.destinationStation().orElseThrow()).orElse(null);
            if (station == null) { return RocketFlightRequestResult.failure(RocketFlightRequestCode.INVALID_DESTINATION); }
            selected = plan.landingPad() != null ? namedPads.select(destinationLevel,
                    rocket.snapshot().orElseThrow(), plan.requestId(), station, plan.landingPad(), reservations,
                    scheduledArrival(countdown), admission.governed()) : pads.selectStation(destinationLevel,
                    rocket.snapshot().orElseThrow(), plan.requestId(), station, reservations, scheduledArrival(countdown),
                    admission.governed());
        } else {
            selected = pads.select(destinationLevel, rocket.snapshot().orElseThrow(), plan.requestId(), reservations,
                    scheduledArrival(countdown), admission.governed(), admission.kept());
        }
        if (!selected.success()) {
            auditPrepare(rocket, plan, selected, RocketFlightRequestCode.LANDING_PAD_UNAVAILABLE);
            return new RocketFlightRequestResult(
                    RocketFlightRequestCode.LANDING_PAD_UNAVAILABLE,
                    plan.requiredFuel(), selected.refusalKey()
            );
        }

        if (admission.governed()) {
            var warming = warmups.decide(server, rocket, countdown, selected.snapshot().orElseThrow(), completion); // W90b
            if (warming != null) { return warming; }
            boolean started = io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.EscrowGovernedPreparation.begin(
                    rocket, countdown, selected.snapshot().orElseThrow(),
                    candidate -> RocketGovernedLaunchBoundary.allows(server, journal, candidate, rocket, recovery.bays()),
                    acknowledged -> {
                        // The exact F2 callback already consumed START_COUNTDOWN. Never use the legacy idle exemption.
                        RocketTransferEntities.keepLoaded(sourceLevel, acknowledged.sourceSnapshot(), acknowledged.transferId());
                        RocketTransferEntities.keepDestinationLoaded(destinationLevel, acknowledged.destinationSnapshot(), acknowledged.transferId());
                        RocketFlightFeedback.countdownAccepted(rocket);
                        diagnostics.prepared(acknowledged.transferId());
                        auditPrepare(rocket, plan, selected, RocketFlightRequestCode.SUCCESS);
                        completion.accepted().run();
                    }, governedLaunchDiscard(journal, countdown.logicalRocketId(), plan.requestId(),
                            () -> resourceMutationAllowed(rocket),
                            () -> io.github.sunthemoon.advancedrocketrycommunity.station.pad.LandingPadService.release(server, plan),
                            completion.discarded()));
            if (!started) {
                io.github.sunthemoon.advancedrocketrycommunity.station.pad.LandingPadService.release(server, plan);
            }
            warmups.afterBegin(started); // W90b: bound on F1's acceptance, released on its refusal
            return new RocketFlightRequestResult(started ? RocketFlightRequestCode.JOURNAL_PENDING
                    : RocketFlightRequestCode.TRANSFER_JOURNAL_BLOCKED, plan.requiredFuel());
        }

        RocketTransferRecord record;
        // The interlock above and reserve(L) refuse every state the journal must; before the session's first
        // acknowledgement an untouched absent rocket is proved, so its launch is admitted (Root decision R30, W91-R2).
        try (var reservation = journal.reserve(countdown.logicalRocketId()).orElse(null)) {
            if (reservation == null) {
                io.github.sunthemoon.advancedrocketrycommunity.station.pad.LandingPadService.release(server, plan);
                return RocketFlightRequestResult.failure(RocketFlightRequestCode.TRANSFER_JOURNAL_BLOCKED);
            }
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
            if (!journal.afterAcknowledged(server, reservation, () -> {
                if (!RocketTransferEntities.currentEntity(rocket, record, false)
                        || !flightAllowed(server, journal, record, TICK_SOURCE)) {
                    liveTransfers.remove(record.transferId()); return false;
                }
                if (previous != null) { liveTransfers.remove(previous.transferId()); settledTransfers.remove(previous.transferId()); }
                RocketTransferEntities.keepLoaded(sourceLevel, record.sourceSnapshot(), record.transferId());
                RocketTransferEntities.keepDestinationLoaded(destinationLevel, record.destinationSnapshot(), record.transferId());
                rocket.updateFlightData(countdown.cancelCountdown(sourceLevel.getGameTime()).startCountdown(sourceLevel.getGameTime()));
                RocketFlightFeedback.countdownAccepted(rocket);
                liveTransfers.add(record.transferId()); diagnostics.prepared(record.transferId());
                auditPrepare(rocket, plan, selected, RocketFlightRequestCode.SUCCESS);
                completion.accepted().run(); return true;
            }, completion.discarded())) { return RocketFlightRequestResult.failure(RocketFlightRequestCode.TRANSFER_JOURNAL_BLOCKED); }
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

    /** prepareLaunch's input: the journal, governed pre-F2 destinations and P's regions while T is absent (W85-R6). */
    List<RocketRegion> launchReservations(MinecraftServer server, RocketTransferSavedData journal) {
        return landingReservations(journal, java.util.stream.Stream.concat(io.github.sunthemoon.advancedrocketrycommunity
                .resourceescrow.EscrowGovernedPreparation.pendingDestinations(server).stream(), landed.reservations(journal).stream()).toList());
    }

    /** The same reservation inputs feed surface, station and explicitly named station selection. */
    static List<RocketRegion> landingReservations(RocketTransferSavedData journal, List<RocketRegion> pending) {
        return java.util.stream.Stream.concat(journal.entries().stream()
                .map(RocketTransferRecord::destinationSnapshot).map(RocketRegion::fromSnapshot), pending.stream()).toList();
    }

    RocketFlightRequestResult cancelCountdown(RocketEntity rocket) {
        return cancelCountdown(rocket, RocketRequestCompletion.NONE);
    }

    RocketFlightRequestResult cancelCountdown(RocketEntity rocket, RocketRequestCompletion completion) {
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
        if (record != null && record.nativeTransfer().isPresent()) {
            if (record.phase() != RocketTransferPhase.PREPARED || !record.sourceEntityId().equals(rocket.getUUID())) {
                return RocketFlightRequestResult.failure(RocketFlightRequestCode.INVALID_STATE);
            }
            boolean queued = failBackToSource(level.getServer(), journal, record, rocket,
                    RocketTransferReturnReason.COUNTDOWN_CANCELLED, completion);
            // The request is complete only after the return's native F6 checkpoint, not merely queue admission.
            return queued ? new RocketFlightRequestResult(RocketFlightRequestCode.JOURNAL_PENDING, plan.requiredFuel())
                    : RocketFlightRequestResult.failure(RocketFlightRequestCode.TRANSFER_JOURNAL_BLOCKED);
        }
        if (!io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.RocketEscrowGate.idle(rocket)) {
            return RocketFlightRequestResult.failure(RocketFlightRequestCode.TRANSFER_JOURNAL_BLOCKED);
        }
        if (record == null
                || record.phase() != RocketTransferPhase.PREPARED
                || !record.sourceEntityId().equals(rocket.getUUID())) {
            return RocketFlightRequestResult.failure(RocketFlightRequestCode.INVALID_STATE);
        }
        if (!failBackToSource(level.getServer(), journal, record, rocket, RocketTransferReturnReason.COUNTDOWN_CANCELLED, completion)) {
            return RocketFlightRequestResult.failure(RocketFlightRequestCode.TRANSFER_JOURNAL_BLOCKED);
        }
        return new RocketFlightRequestResult(journal.actionable(record.logicalRocketId()) ? RocketFlightRequestCode.SUCCESS
                : RocketFlightRequestCode.JOURNAL_PENDING, plan.requiredFuel());
    }

    void tick(MinecraftServer server) {
        Objects.requireNonNull(server, "server");
        warmups.tick(server); // W90b: deadline, disconnect, removal and polling run whether or not the journal is blocked
        // BEGIN transfer service diagnostics
        diagnostics.enterTick(server.getTickCount());
        // END transfer service diagnostics
        RocketTransferSavedData journal = RocketTransferSavedData.get(server);
        if (!journal.operational()) {
            return;
        }
        landed.reportUnacknowledged();
        recovery.classify(server, journal);
        // W44 section 2.4: confirm one baseline whatever the transfer count, before the dispatch predicate.
        journal.tickBaseline(server);
        journal.retryChecked(server);
        recovery.recoverNext(server, journal);
        recovery.tickReconnects(server, journal);
        governedSources.tick(server, journal, recovery.bays());
        governedCleanup.tick(server, journal, recovery.bays());
        List<UUID> active = liveTransfers.stream().sorted().toList();
        for (UUID transferId : active) {
            RocketTransferRecord record = journal.find(transferId).orElse(null);
            if (record == null) {
                liveTransfers.remove(transferId);
                continue;
            }
            if (record.nativeTransfer().isPresent()) { continue; }
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
                RocketTransferEntities.loadedMatches(server, record, true).size(),
                governedSources.readiness(server, record).withWarmup(warmups.bound(server, record.transferId()))
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

    /** Reconciliation still requires the checked landed authority and an unclaimed bay boundary. */
    boolean escrowReconciliationBoundary(RocketEntity rocket) {
        if (!(rocket.level() instanceof ServerLevel level) || !level.getServer().isSameThread() || !rocket.operational()
                || rocket.flightData().orElseThrow().activeTransferId().isPresent()
                || landed.pending(rocket.assemblyTransactionId().orElse(null))) { return false; }
        var journal = RocketTransferSavedData.get(level.getServer());
        if (!journal.actionAuthorityAvailable()) { return false; }
        var record = journal.findByLogicalRocket(rocket.assemblyTransactionId().orElseThrow()).orElse(null);
        if (record == null) { return true; }
        var bay = recovery.bays().before(level.getServer(), journal, record, RocketTransferBayRecoveryGate.Action.TICK_SOURCE);
        return bay != null && bay.kind() == RocketTransferBayRecoveryGate.BoundaryKind.UNCLAIMED
                && reconciliationRecordAllowed(record, settledTransfers.contains(record.transferId()), liveTransfers.contains(record.transferId()),
                RocketTransferEntities.isReplaceableLandedAuthority(rocket, record),
                record.nativeTransfer().isPresent() && io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.RocketEscrowGate.terminalSuccessor(rocket));
    }

    static boolean reconciliationRecordAllowed(RocketTransferRecord record, boolean settled, boolean live,
                                               boolean stationaryArrival, boolean terminalSuccessor) {
        return record.nativeTransfer().isPresent() ? terminalSuccessor
                : resourceMutationAllowed(true, record.phase(), settled && !live, stationaryArrival);
    }

    /** No endpoint may mutate a balance that restart recovery can replace from its journal. */
    boolean resourceMutationAllowed(RocketEntity rocket) {
        if (!io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.RocketEscrowGate.resourcesIdle(rocket)) { return false; }
        if (landed.pending(rocket.assemblyTransactionId().orElse(null))) { return false; }
        if (!(rocket.level() instanceof ServerLevel level) || !level.getServer().isSameThread()
                || !rocket.operational() || rocket.flightData().orElseThrow().activeTransferId().isPresent()) {
            return false;
        }
        var journal = RocketTransferSavedData.get(level.getServer());
        if (journal.resourceMutationUnproved(rocket.assemblyTransactionId().orElseThrow())) { return false; }
        var record = journal.findByLogicalRocket(rocket.assemblyTransactionId().orElseThrow()).orElse(null);
        if (record != null && record.nativeTransfer().isPresent()) {
            return io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.RocketEscrowGate.terminalSuccessor(rocket);
        }
        return resourceMutationAllowed(journal.operational(), record == null ? null : record.phase(),
                record != null && settledTransfers.contains(record.transferId()) && !liveTransfers.contains(record.transferId()),
                record != null && RocketTransferEntities.isReplaceableLandedAuthority(rocket, record));
    }

    static boolean resourceMutationAllowed(boolean operational, RocketTransferPhase phase,
            boolean settled, boolean stationaryArrival) {
        return operational && (phase == null || phase == RocketTransferPhase.COMMITTED && settled && stationaryArrival);
    }

    /** The production discard callback; package-private solely to test it without starting a world. */
    static Runnable governedLaunchDiscard(RocketTransferSavedData journal, UUID logical, UUID request,
            java.util.function.BooleanSupplier resourcesAllowed, Runnable releasePad, Runnable discarded) {
        return () -> {
            // Only a definitely unarmed/idle failure can release the tentative pad.
            if (journal.actionable(logical) && journal.find(request).isEmpty() && resourcesAllowed.getAsBoolean()) {
                releasePad.run();
            }
            discarded.run();
        };
    }

    /** W44's FM12a hook only; player and release-test disassembly use {@link #beginLandedDisassembly}. */
    boolean releaseLandedReservation(RocketEntity rocket) { return RocketLandedDisassemblies.releasePlain(rocket, this::landedPort); }

    /** W85 M0 and M1; SUCCESS means that L has no landed record T, so the caller runs today's one-tick path. */
    RocketValidationCode beginLandedDisassembly(RocketEntity rocket, java.util.function.Supplier<Optional<RocketValidationCode>> preflight,
            RocketLandedDisassemblies.Request request) { return landed.begin(rocket, preflight, request, this::landedPort); }
    boolean landedDisassemblyPending(RocketEntity rocket) { return landed.pending(rocket.assemblyTransactionId().orElse(null)); }
    RocketLandedDisassemblies landedDisassemblies() { return landed; }
    RocketLandedDisassemblies.Port landedPort(MinecraftServer server, RocketTransferSavedData journal) {
        return new RocketLandedDisassemblyPort(server, journal, recovery.bays(), liveTransfers, settledTransfers);
    }

    void clear() {
        warmups.clear();
        landed.clear();
        arrivalFootprints.clear();
        liveTransfers.clear();
        settledTransfers.clear();
        recovery.clear();
        governedSources.clear();
        governedCleanup.clear();
        // BEGIN transfer service diagnostics
        diagnostics.clear();
        // END transfer service diagnostics
    }

    void tickLive(MinecraftServer server, RocketTransferSavedData journal, RocketTransferRecord record) {
        if (journal.nativeProbe() != null) {
            journal.nativeProbe().event(record.logicalRocketId(), "visit", journal.mutationSequence(), record.phase().name(),
                    journal.actionable(record.logicalRocketId()) && journal.find(record.transferId()).orElse(null) == record ? 1 : 0);
        }
        if (!journal.actionable(record.logicalRocketId()) || journal.find(record.transferId()).orElse(null) != record
                || !flightAllowed(server, journal, record, TICK_SOURCE)) { return; }
        ServerLevel sourceLevel = RocketTransferEntities.level(server, record.sourceSnapshot().sourceDimension());
        ServerLevel destinationLevel = RocketTransferEntities.level(
                server,
                record.destinationSnapshot().sourceDimension()
        );
        if (sourceLevel != null && !record.phase().destinationAuthoritative()) {
            RocketTransferEntities.keepLoaded(sourceLevel, record.sourceSnapshot(), record.transferId());
        }
        if (destinationLevel != null) {
            RocketTransferEntities.keepDestinationLoaded(destinationLevel, record.destinationSnapshot(), record.transferId());
        }
        if (journal.nativeProbe() != null) { journal.nativeProbe().event(record.logicalRocketId(), "step", journal.mutationSequence(), record.phase().name(), 0); }
        if (journal.nativeProbe() != null && record.phase() != RocketTransferPhase.PREPARED && record.phase() != RocketTransferPhase.COMMITTED) {
            journal.nativeProbe().event(record.logicalRocketId(), "due", journal.mutationSequence(), record.phase().name(), 0);
        }
        switch (record.phase()) {
            case PREPARED -> tickSource(server, journal, record);
            case DESTINATION_SPAWNED -> transferPassengers(server, journal, record);
            case PASSENGERS_TRANSFERRED -> removeSource(server, journal, record);
            case SOURCE_REMOVED -> updatePhase(server, journal, record.advance(RocketTransferPhase.COMMITTED));
            case COMMITTED -> destinations.tickDestination(server, journal, record);
        }
    }

    private void tickSource(MinecraftServer server, RocketTransferSavedData journal, RocketTransferRecord record) {
        if (!journal.actionable(record.logicalRocketId())) { return; }
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
        if (journal.nativeProbe() != null) { journal.nativeProbe().event(record.logicalRocketId(), "flight", journal.mutationSequence(), flight.state().name(), Double.doubleToLongBits(source.getY())); }
        // BEGIN transfer service diagnostics
        if (flight.state() == RocketFlightState.TRANSIT) {
            diagnostics.branch(record.transferId(), TransferFailureDiagnostics.Branch.TRANSIT_NOT_DUE);
        }
        // END transfer service diagnostics
        if (flight.state() == RocketFlightState.COUNTDOWN
                && elapsed(now, flight.stateStartedGameTime()) >= RocketFlightLimits.COUNTDOWN_TICKS) {
            if (journal.nativeProbe() != null) { journal.nativeProbe().event(record.logicalRocketId(), "due", journal.mutationSequence(), "COUNTDOWN", 0); }
            source.updateFlightData(flight.completeCountdown(now));
            if (journal.nativeProbe() != null) { journal.nativeProbe().event(record.logicalRocketId(), "work", journal.mutationSequence(), "COUNTDOWN", 0); }
            flight = source.flightData().orElseThrow();
            RocketFlightFeedback.ascentStarted(source);
            auditPhase(record, "countdown_complete", source.getUUID());
        }
        if (flight.state() == RocketFlightState.ASCENT) {
            if (journal.nativeProbe() != null) { journal.nativeProbe().event(record.logicalRocketId(), "due", journal.mutationSequence(), "ASCENT", 0); }
            long elapsed = elapsed(now, flight.stateStartedGameTime());
            RocketTransferEntities.positionAscent(source, record.sourceSnapshot().sourceOrigin(), elapsed);
            if (journal.nativeProbe() != null) { journal.nativeProbe().event(record.logicalRocketId(), "work", journal.mutationSequence(), "ASCENT", 0); }
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
            if (journal.nativeProbe() != null) { journal.nativeProbe().event(record.logicalRocketId(), "due", journal.mutationSequence(), "TRANSIT", 0); }
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

    void spawnDestination(MinecraftServer server, RocketTransferSavedData journal,
                          RocketTransferRecord record, RocketEntity source) {
        if (!journal.actionable(record.logicalRocketId()) || journal.find(record.transferId()).orElse(null) != record
                || !flightAllowed(server, journal, record, SPAWN_DESTINATION)) { return; }
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
            if (journal.nativeProbe() != null) { journal.nativeProbe().event(record.logicalRocketId(), "owned_wait", journal.mutationSequence(), "DESTINATION_CHUNK", 0); }
            // BEGIN transfer service diagnostics
            diagnostics.readinessWait(record.transferId());
            // END transfer service diagnostics
            return;
        }
        if (destinationLevel != null && !arrivalFootprints.ready(destinationLevel, record, source)) { // W83b: R32 item 1
            if (journal.nativeProbe() != null) { journal.nativeProbe().event(record.logicalRocketId(), "owned_wait", journal.mutationSequence(), "DESTINATION_ENTITIES", 0); }
            return;
        }
        var arrivalPlan = record.sourceFlightData().plan().orElseThrow();
        boolean landingAvailable = destinationLevel != null && (arrivalPlan.landingPad() == null
                ? pads.available(destinationLevel, record.destinationSnapshot(), null, false)
                : namedPads.recheck(destinationLevel, record.destinationSnapshot(), arrivalPlan.destinationStationId(),
                        arrivalPlan.landingPad(), record.transferId()));
        if (!landingAvailable) {
            if (journal.nativeProbe() != null) { journal.nativeProbe().event(record.logicalRocketId(), "owned_wait", journal.mutationSequence(), "DESTINATION_PAD", 0); }
            // BEGIN transfer service diagnostics
            diagnostics.branch(record.transferId(), TransferFailureDiagnostics.Branch.DESTINATION_UNAVAILABLE);
            // END transfer service diagnostics
            failBackToSource(server, journal, record, source, RocketTransferReturnReason.DESTINATION_PAD_BLOCKED, RocketRequestCompletion.NONE);
            return;
        }
        RocketEntity destination = ModEntities.ROCKET.get().create(destinationLevel);
        // BEGIN transfer service diagnostics
        diagnostics.branch(record.transferId(), TransferFailureDiagnostics.Branch.SPAWN_ATTEMPT);
        // END transfer service diagnostics
        if (destination == null) {
            failBackToSource(server, journal, record, source, RocketTransferReturnReason.DESTINATION_ENTITY_CREATE_FAILED, RocketRequestCompletion.NONE);
            if (journal.nativeProbe() != null) { journal.nativeProbe().event(record.logicalRocketId(), "owned_wait", journal.mutationSequence(), "ENTITY_CREATE", 0); }
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
            if (!RocketTransferEntities.currentEntity(source, record, false)
                    || destination.isRemoved() || destination.isAddedToWorld()
                    || destinationLevel.getEntity(destination.getUUID()) != null
                    || !RocketTransferEntities.destinationEntityChunkReady(destinationLevel, record.destinationSnapshot())
                    || !(arrivalPlan.landingPad() == null
                            ? pads.available(destinationLevel, record.destinationSnapshot(), null, false)
                            : namedPads.recheck(destinationLevel, record.destinationSnapshot(), arrivalPlan.destinationStationId(),
                                    arrivalPlan.landingPad(), record.transferId()))) {
                liveTransfers.remove(record.transferId()); return;
            }
            destination.updateFlightData(RocketTransferEntities.currentArrival(record, destinationLevel.getGameTime()));
            if (!destinationLevel.addFreshEntity(destination)) {
                failBackToSource(server, journal, record, source, RocketTransferReturnReason.DESTINATION_ENTITY_SPAWN_FAILED, RocketRequestCompletion.NONE);
                return;
            }
            RocketFlightFeedback.destinationSpawned(destination);
        });
    }

    private void transferPassengers(MinecraftServer server, RocketTransferSavedData journal, RocketTransferRecord record) {
        RocketEntity destination = RocketTransferEntities.findDestination(server, record);
        if (destination == null) {
            if (journal.nativeProbe() != null) { journal.nativeProbe().event(record.logicalRocketId(), "owned_wait", journal.mutationSequence(), "DESTINATION_ENTITY", 0); }
            liveTransfers.remove(record.transferId());
            return;
        }
        updatePhase(server, journal, record.advance(RocketTransferPhase.PASSENGERS_TRANSFERRED), () -> {
            if (!RocketTransferEntities.currentEntity(destination, record, true)) {
                liveTransfers.remove(record.transferId()); return;
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
        });
    }

    private void removeSource(MinecraftServer server, RocketTransferSavedData journal, RocketTransferRecord record) {
        updatePhase(server, journal, record.advance(RocketTransferPhase.SOURCE_REMOVED), () -> {
            RocketEntity source = RocketTransferEntities.findSource(server, record);
            if (source != null) { source.discard(); }
        });
    }

    boolean failBackToSource(MinecraftServer server, RocketTransferSavedData journal,
            RocketTransferRecord record, RocketEntity source, RocketTransferReturnReason reason, RocketRequestCompletion completion) {
        return continuations.failBack(server, journal, record, source, reason, completion);
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
        return continuations.update(server, journal, updated, action, effect);
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
