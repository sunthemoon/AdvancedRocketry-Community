package io.github.sunthemoon.advancedrocketrycommunity.rocket.server;

import io.github.sunthemoon.advancedrocketrycommunity.AdvancedRocketryCommunity;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.entity.RocketEntity;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFlightData;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFlightState;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketPassengerReconnectQueue;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketTransferPhase;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketTransferPresence;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketTransferRecord;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketTransferRecoveryAction;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketTransferRecoveryDecision;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.persistence.RocketTransferSavedData;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;

/** Reconciles the bounded four-case transfer matrix and reconnects recorded passengers. */
final class RocketTransferRecoveryService {
    enum Status {
        RECOVERED,
        WAITING_FOR_PASSENGERS,
        RETRY_LATER,
        NOT_FOUND
    }

    record Result(
            Status status,
            UUID transferId,
            RocketTransferPhase phase,
            RocketTransferRecoveryAction action,
            int sourceCount,
            int destinationCount
    ) {
        Result {
            Objects.requireNonNull(status, "status");
        }

        static Result notFound(UUID transferId) {
            return new Result(Status.NOT_FOUND, transferId, null, null, 0, 0);
        }

        void log(Logger logger, boolean destinationSettled) {
            String message = "ARCE_TRANSFER_RECOVERY transfer={} phase={} source_count={} destination_count={} action={} status={}";
            Object[] arguments = {transferId, phase, sourceCount, destinationCount, action, status};
            if (destinationSettled && status == Status.RECOVERED && phase == RocketTransferPhase.COMMITTED
                    && action == RocketTransferRecoveryAction.KEEP_DESTINATION
                    && sourceCount == 0 && destinationCount == 1) {
                logger.info(message, arguments);
            } else {
                logger.warn(message, arguments);
            }
        }
    }

    private final Set<UUID> liveTransfers;
    private final Set<UUID> settledTransfers;
    private final RocketPassengerReconnectQueue reconnects = new RocketPassengerReconnectQueue();
    private final RocketTransferFences fences = new RocketTransferFences();
    private final RocketTransferBayDispatch bays;
    /** W98 (N3-A1): I1 to I3 remove an ordinary source's record only after its survivor is proved on disk. */
    private final RocketOrdinaryRetirement retirements;
    private int recoveryCursor;
    private final java.util.Map<UUID, Result> completedResults = new java.util.LinkedHashMap<>();
    private final java.util.Map<UUID, RocketTransferRecord> completedRecords = new java.util.HashMap<>();

    RocketTransferRecoveryService(Set<UUID> liveTransfers, Set<UUID> settledTransfers) {
        this.liveTransfers = Objects.requireNonNull(liveTransfers, "liveTransfers");
        this.settledTransfers = Objects.requireNonNull(settledTransfers, "settledTransfers");
        bays = new RocketTransferBayDispatch(liveTransfers, settledTransfers, fences);
        fences.bays(bays);
        retirements = new RocketOrdinaryRetirement(liveTransfers, settledTransfers, fences::ids, server -> new RocketRetirementWorld(server, bays));
    }

    Result recoverNext(MinecraftServer server, RocketTransferSavedData journal) {
        classify(server, journal);
        List<RocketTransferRecord> entries = journal.entries();
        if (entries.isEmpty()) { return Result.notFound(null); }
        journal.retryChecked(server);
        var promoted = fences.reevaluateOne(server, journal, liveTransfers, settledTransfers);
        // W98-R8: a return the ordinary coordinator holds is that coordinator's; recovery never runs it again.
        if (promoted != null && !retirements.pending(promoted.logicalRocketId())
                && !RocketReleaseAccess.fenced(server, promoted.logicalRocketId())) { return recover(server, journal, promoted, true); }
        Step step = step(entries.stream().filter(candidate -> journal.actionable(candidate.logicalRocketId())
                        && !RocketReleaseAccess.fenced(server, candidate.logicalRocketId())
                        && !retirements.pending(candidate.logicalRocketId())).toList(),
                liveTransfers, settledTransfers, bays.excluded(), recoveryCursor,
                candidate -> RocketTransferEntities.recoveryEntityChunksLoaded(server, candidate));
        recoveryCursor = step.cursor();
        if (step.candidate() == null) {
            return Result.notFound(null);
        }
        return step.ready() ? recover(server, journal, step.candidate(), false) : retryLater(step.candidate());
    }

    /**
     * WARP review R7 and final review A1: one unclassified record per call, chosen round-robin by a
     * cursor. Its ends are checked with {@code loaded}, which may add a chunk ticket and load them, so
     * only one record is checked per call, as before R7. A record whose ends cannot be loaded (for
     * example, its Level is gone) advances the cursor, so it never starves the records behind it.
     */
    static Step step(List<RocketTransferRecord> entries, Set<UUID> live, Set<UUID> settled, int cursor,
                     java.util.function.Predicate<RocketTransferRecord> loaded) {
        return step(entries, live, settled, Set.of(), cursor, loaded);
    }

    static Step step(List<RocketTransferRecord> entries, Set<UUID> live, Set<UUID> settled, Set<UUID> fenced, int cursor,
                     java.util.function.Predicate<RocketTransferRecord> loaded) {
        List<RocketTransferRecord> unclassified = entries.stream()
                .filter(candidate -> candidate.nativeTransfer().isEmpty())
                .filter(candidate -> !live.contains(candidate.transferId()))
                .filter(candidate -> !settled.contains(candidate.transferId()))
                .filter(candidate -> !fenced.contains(candidate.transferId()))
                .toList();
        if (unclassified.isEmpty()) {
            return new Step(null, false, 0);
        }
        int index = Math.floorMod(cursor, unclassified.size());
        RocketTransferRecord candidate = unclassified.get(index);
        return loaded.test(candidate) ? new Step(candidate, true, index) : new Step(candidate, false, index + 1);
    }

    /** The record this call handles, whether its ends are loaded, and the cursor for the next call. */
    record Step(RocketTransferRecord candidate, boolean ready, int cursor) {
    }

    Result recoverById(MinecraftServer server, UUID transferId) {
        Objects.requireNonNull(server, "server");
        Objects.requireNonNull(transferId, "transferId");
        RocketTransferSavedData journal = RocketTransferSavedData.get(server);
        return recoverById(server, journal, transferId);
    }

    Result recoverById(MinecraftServer server, RocketTransferSavedData journal, UUID transferId) {
        if (!journal.operational()) {
            return Result.notFound(transferId);
        }
        classify(server, journal);
        RocketTransferRecord record = journal.find(transferId).orElse(null);
        if (record != null && retirements.replacing(record.logicalRocketId())) { return retryLater(record); }
        if (record == null) {
            var authority = completedRecords.get(transferId);
            if (completedResults.containsKey(transferId) && authority != null) {
                if (!bays.allowed(server, journal, authority, RocketTransferBayRecoveryGate.Action.RECOVER_BY_ID)
                        || !journal.actionable(authority.logicalRocketId())
                        || journal.findByLogicalRocket(authority.logicalRocketId()).isPresent()
                        || RocketReleaseAccess.fenced(server, authority.logicalRocketId())) { return retryLater(authority); }
                return takeCompleted(transferId);
            }
            return Result.notFound(transferId);
        }
        if (!bays.allowed(server, journal, record, RocketTransferBayRecoveryGate.Action.RECOVER_BY_ID)
                || bays.source(transferId) != null) { return retryLater(record); }
        if (RocketReleaseAccess.fenced(server, record.logicalRocketId())) { return retryLater(record); }
        journal.retryChecked(server);
        if (journal.find(transferId).orElse(null) != record) { return retryLater(record); }
        if (journal.actionable(record.logicalRocketId()) && completedResults.containsKey(transferId)) {
            var authority = completedRecords.get(transferId);
            Result completed = takeCompleted(transferId);
            if (authority == record) { return completed; }
        }
        boolean fenced = fences.ids().contains(transferId);
        if (!journal.actionable(record.logicalRocketId())
                || !fences.permit(server, journal, record, liveTransfers, settledTransfers)) { return retryLater(record); }
        if (fenced ? !RocketTransferEntities.recoveryEndsAlreadyLoaded(server, record)
                : !RocketTransferEntities.recoveryEntityChunksLoaded(server, record)) {
            return retryLater(record);
        }
        return recover(server, journal, record, fenced);
    }

    void onPlayerLoggedIn(ServerPlayer player, RocketTransferSavedData journal) {
        if (tryReconnect(player, journal)) {
            reconnects.complete(player.getUUID());
        } else if (!reconnects.offer(player.getUUID(), player.getServer().overworld().getGameTime())) {
            AdvancedRocketryCommunity.LOGGER.warn("ARCE_PASSENGER_RECONNECT_QUEUE_FULL player={}", player.getUUID());
        }
    }

    void tickReconnects(MinecraftServer server, RocketTransferSavedData journal) {
        var batch = reconnects.next(server.overworld().getGameTime());
        for (UUID playerId : batch.expired()) {
            AdvancedRocketryCommunity.LOGGER.warn("ARCE_PASSENGER_RECONNECT_EXPIRED player={}", playerId);
        }
        for (UUID playerId : batch.pending()) {
            ServerPlayer player = server.getPlayerList().getPlayer(playerId);
            if (player == null || tryReconnect(player, journal)) {
                reconnects.complete(playerId);
            }
        }
    }

    void onPlayerLoggedOut(UUID playerId) {
        // Cancel the session's transient work, not its durable passenger assignment.
        reconnects.complete(playerId);
    }

    void clear() {
        retirements.clear();
        completedResults.clear();
        completedRecords.clear();
        reconnects.clear();
        fences.clear();
        bays.clear();
    }

    void classify(MinecraftServer server, RocketTransferSavedData journal) {
        bays.prune(journal); fences.classify(server, journal, liveTransfers, settledTransfers);
    }
    RocketTransferBayDispatch bays() { return bays; }
    RocketOrdinaryRetirement retirements() { return retirements; }
    RocketReplacementRetirement replacementPort(MinecraftServer server, RocketTransferSavedData journal, RocketEntity rocket,
            RocketTransferRecord previous, RocketTransferRecord successor, java.util.function.BooleanSupplier authorized,
            java.util.function.BooleanSupplier targetAvailable) {
        return new RocketReplacementRetirement(server, journal, bays, fences, liveTransfers, settledTransfers,
                rocket, previous, successor, authorized, targetAvailable);
    }
    /** R52 (b2): a loader's fill attempt only queues its demand; the first attempt starts from the coordinator's tick (W130-R4). */
    void retireBeforeFill(RocketEntity rocket) { retirements.fills().demand(rocket.assemblyTransactionId().orElseThrow(), rocket.getUUID()); }
    private void beginBeforeFill(MinecraftServer server, UUID logical, UUID survivor) {
        var journal = RocketTransferSavedData.get(server);
        if (retirements.pending(logical) || !retirements.pendingRoom(logical)) { return; }
        var record = journal.findByLogicalRocket(logical).orElse(null);
        if (record == null || record.nativeTransfer().isPresent() || !journal.actionable(logical)
                || !settledTransfers.contains(record.transferId()) || liveTransfers.contains(record.transferId())) { return; }
        var port = new RocketDestinationRetirement(server, journal, bays, fences, liveTransfers, settledTransfers);
        // A rocket that is no longer loaded, for example after the entry's own tickets were released, is no demand.
        if (port.projection(record, survivor) == null || !port.escrowAllows(journal, record, null)) { return; }
        try (var held = RocketOrdinaryRetirement.hold(journal, logical)) {
            if (held.token() == null) { return; }
            retirements.begin(port, journal, RocketOrdinaryRetirement.Site.BEFORE_FILL, record, survivor, List.of(), false, held,
                    RocketOrdinaryRetirement.Effects.of(() -> {
                        io.github.sunthemoon.advancedrocketrycommunity.station.pad.LandingPadService.release(server, record);
                        return true;
                    }));
        }
    }
    void tickRetirements(MinecraftServer server) {
        retirements.fills().drain(RocketOrdinaryRetirement.MAX_ATTEMPTS, (logical, survivor) -> beginBeforeFill(server, logical, survivor));
        retirements.tick();
    }
    Set<UUID> fencedIds() { return fences.ids(); }
    String fenceReason(UUID id) { return fences.reason(id); }
    String fencedDiagnostics() { return fences.ids().stream().sorted().limit(4)
            .map(id -> " [fenced_transfer=" + id + " reason=" + fences.reason(id) + " repair=offline-whole-world]")
            .collect(java.util.stream.Collectors.joining()); }
    void onRocketJoined(RocketEntity rocket) {
        if (rocket.level() instanceof net.minecraft.server.level.ServerLevel level && rocket.assemblyTransactionId().isPresent()) {
            fences.joined(level.getServer(), rocket.assemblyTransactionId().orElseThrow(), RocketTransferFences.state(rocket).protectedResources(),
                    RocketTransferSavedData.get(level.getServer()), liveTransfers, settledTransfers);
        }
    }

    private boolean tryReconnect(ServerPlayer player, RocketTransferSavedData journal) {
        if (io.github.sunthemoon.advancedrocketrycommunity.rocket.rejoin.RocketRejoinProtection.required(player.getServer(), player.getUUID())) { return true; }
        MinecraftServer server = player.getServer();
        if (server == null || journal.entries().isEmpty()) {
            return true;
        }
        classify(server, journal);
        journal.retryChecked(server);
        if (!player.serverLevel().areEntitiesLoaded(player.chunkPosition().toLong())) {
            return false;
        }
        for (RocketTransferRecord record : journal.entries()) {
            if (record.nativeTransfer().isPresent()) { continue; }
            if (retirements.replacing(record.logicalRocketId())) { continue; }
            // A fenced assignment is intentionally finished for this login; it remains durably assigned.
            if (fences.ids().contains(record.transferId())) { continue; }
            if (!bays.allowed(server, journal, record, RocketTransferBayRecoveryGate.Action.RECONNECT)
                    || bays.source(record.transferId()) != null) { continue; }
            RocketEntity settled = RocketTransferEntities.loadedSettledAuthority(server, record);
            if (settled == null && RocketTransferEntities.nearbyArrivalStillLoading(player, record)) {
                // A newly boarded passenger may be in the adjacent, already loaded chunk.
                // Wait for the nearby entity's current manifest without activating that chunk.
                return false;
            }
            var passengers = settled == null ? record.sourceFlightData().passengers()
                    : settled.flightData().orElseThrow().passengers();
            passengers = RocketReleaseAccess.filter(server, record.logicalRocketId(), passengers);
            if (passengers.assignment(player.getUUID()).isEmpty()) {
                continue;
            }
            if (RocketReleaseAccess.fenced(server, record.logicalRocketId())) { return false; }
            if (!journal.actionable(record.logicalRocketId())) { return false; }
            if (!RocketTransferEntities.authorityEntityChunkLoaded(server, record)) {
                return false;
            }
            List<RocketEntity> candidates = RocketTransferEntities.findMatches(
                    server, record, record.phase().destinationAuthoritative());
            var action = record.phase().destinationAuthoritative() ? RocketTransferRecoveryAction.KEEP_DESTINATION : RocketTransferRecoveryAction.KEEP_SOURCE;
            if (!fences.actionAllowed(server, journal, record, action,
                    record.phase().destinationAuthoritative() ? List.of() : candidates,
                    record.phase().destinationAuthoritative() ? candidates : List.of(), liveTransfers, settledTransfers)) { continue; }
            if (candidates.isEmpty()) {
                recoverById(server, record.transferId());
                return false;
            }
            if (record.phase() == RocketTransferPhase.COMMITTED
                    && record.destinationEntityId().filter(candidates.get(0).getUUID()::equals).isEmpty()) {
                // Rebind through durable recovery, never adopt an unconfirmed login copy.
                recoverById(server, record.transferId());
                return false;
            }
            RocketEntity authority = candidates.isEmpty() ? null : RocketTransferEntities.keepOne(candidates);
            if (authority != null) {
                if (candidates.size() > 1) {
                    RocketTransferEntities.remountOnlinePassengers(server, record, authority,
                            record.phase().destinationAuthoritative()
                                    ? record.destinationSnapshot().sourceOrigin()
                                    : record.sourceSnapshot().sourceOrigin());
                    AdvancedRocketryCommunity.LOGGER.warn(
                            "ARCE_TRANSFER_LOGIN_RECONCILED transfer={} logical={} authority={} removed={}",
                            record.transferId(), record.logicalRocketId(), authority.getUUID(), candidates.size() - 1);
                }
                if (RocketTransferEntities.isReplaceableLandedAuthority(authority, record)
                        && authority.flightData().orElseThrow().passengers().assignment(player.getUUID()).isEmpty()) {
                    return true;
                }
                RocketTransferEntities.movePassenger(
                        player,
                        authority,
                        record.phase().destinationAuthoritative()
                                ? record.destinationSnapshot().sourceOrigin()
                                : record.sourceSnapshot().sourceOrigin()
                );
                finishSourceSettlementIfComplete(server, journal, record, authority, candidates.subList(1, candidates.size()));
            }
            break;
        }
        return true;
    }

    private Result recover(
            MinecraftServer server,
            RocketTransferSavedData journal,
            RocketTransferRecord record,
            boolean loadedOnly
    ) {
        if (RocketReleaseAccess.fenced(server, record.logicalRocketId())) { return retryLater(record); }
        if (!journal.actionable(record.logicalRocketId()) || journal.find(record.transferId()).orElse(null) != record) {
            return retryLater(record);
        }
        if (!bays.allowed(server, journal, record, RocketTransferBayRecoveryGate.Action.RECOVER_AUTO)
                || bays.source(record.transferId()) != null) { return retryLater(record); }
        List<RocketEntity> sources = loadedOnly ? RocketTransferEntities.loadedMatches(server, record, false) : RocketTransferEntities.findMatches(server, record, false);
        List<RocketEntity> destinations = loadedOnly ? RocketTransferEntities.loadedMatches(server, record, true) : RocketTransferEntities.findMatches(server, record, true);
        RocketTransferRecoveryAction action = RocketTransferRecoveryDecision.decide(
                record.phase(),
                new RocketTransferPresence(!sources.isEmpty(), !destinations.isEmpty())
        );
        if (!fences.actionAllowed(server, journal, record, action, sources, destinations, liveTransfers, settledTransfers)) { return retryLater(record); }
        // W98 (R4): a source return needs a free entry and every candidate on its end's origin column, before any change.
        boolean sourceAction = action == RocketTransferRecoveryAction.KEEP_SOURCE
                || action == RocketTransferRecoveryAction.REMOVE_DESTINATION_KEEP_SOURCE || action == RocketTransferRecoveryAction.REBUILD_SOURCE;
        if (sourceAction && !retirements.admits(record, null, sources, destinations)) { return retryLater(record); }
        try (var held = RocketOrdinaryRetirement.hold(journal, record.logicalRocketId())) {
            var reservation = held.token();
            if (reservation == null) { return retryLater(record); }
            Status status;
            switch (action) {
                case KEEP_SOURCE, REMOVE_DESTINATION_KEEP_SOURCE -> {
                    RocketTransferEntities.discardAll(destinations);
                    RocketEntity source = sources.isEmpty()
                            ? RocketTransferEntities.rebuildSource(server, record)
                            : RocketTransferEntities.keepOne(sources);
                    if (source == null) {
                        return result(Status.RETRY_LATER, record, action, sources, destinations);
                    }
                    var discarded = new java.util.ArrayList<>(destinations);
                    if (sources.size() > 1) { discarded.addAll(sources.subList(1, sources.size())); }
                    source.updateFlightData(RocketTransferEntities.stationarySource(
                            record,
                            source.level().getGameTime()
                    ));
                    RocketTransferEntities.positionAtOrigin(source, record.sourceSnapshot().sourceOrigin());
                    RocketTransferEntities.remountOnlinePassengers(
                            server,
                            record,
                            source,
                            record.sourceSnapshot().sourceOrigin()
                    );
                    status = settleRecoveredSource(server, journal, record, held, source, discarded,
                            () -> completedSource(result(Status.RECOVERED, record, action, sources, destinations), record));
                }
                case REBUILD_SOURCE -> {
                    RocketEntity source = RocketTransferEntities.rebuildSource(server, record);
                    if (source == null) {
                        return result(Status.RETRY_LATER, record, action, sources, destinations);
                    }
                    RocketTransferEntities.remountOnlinePassengers(
                            server,
                            record,
                            source,
                            record.sourceSnapshot().sourceOrigin()
                    );
                    status = settleRecoveredSource(server, journal, record, held, source, List.of(),
                            () -> completedSource(result(Status.RECOVERED, record, action, sources, destinations), record));
                }
                case KEEP_DESTINATION, REMOVE_SOURCE_KEEP_DESTINATION, REBUILD_DESTINATION -> {
                    RocketEntity destination = destinations.isEmpty()
                            ? RocketTransferEntities.createDestination(server, record)
                            : destinations.get(0);
                    if (destination == null) {
                        return result(Status.RETRY_LATER, record, action, sources, destinations);
                    }
                    // A rejected spawn must retry the already acknowledged identity, including after restart.
                    if (destinations.isEmpty()) {
                        record.destinationEntityId().ifPresent(destination::setUUID);
                    }
                    RocketTransferRecord resumed = record.phase() == RocketTransferPhase.PREPARED
                            ? record.destinationSpawned(destination.getUUID())
                            : record.destinationEntityId().filter(destination.getUUID()::equals).isPresent()
                                    ? record : record.rebindDestination(destination.getUUID());
                    if (!bays.allowed(server, journal, record, RocketTransferBayRecoveryGate.Action.RECOVER_AUTO)) { return retryLater(record); }
                    if (resumed != record) { journal.put(resumed); }
                    journal.afterAcknowledged(server, reservation, () -> {
                        if (!bays.allowed(server, journal, resumed, RocketTransferBayRecoveryGate.Action.RECOVER_AUTO)) { return; }
                        if (sources.stream().anyMatch(source -> !RocketTransferEntities.loadedEntity(source))) {
                            liveTransfers.remove(resumed.transferId()); return;
                        }
                        var currentSources = RocketTransferEntities.loadedMatches(server, resumed, false);
                        var currentDestinations = RocketTransferEntities.loadedMatches(server, resumed, true);
                        if (!fences.actionAllowed(server, journal, resumed, action, currentSources, currentDestinations,
                                liveTransfers, settledTransfers)) { return; }
                        if (destinations.isEmpty()) {
                            if (!currentDestinations.isEmpty() || destination.isRemoved() || destination.isAddedToWorld()
                                    || !((net.minecraft.server.level.ServerLevel) destination.level()).areEntitiesLoaded(destination.chunkPosition().toLong())
                                    || !((net.minecraft.server.level.ServerLevel) destination.level()).addFreshEntity(destination)) {
                                liveTransfers.remove(resumed.transferId()); return;
                            }
                        } else if (!RocketTransferEntities.currentEntity(destination, resumed, true)) {
                            liveTransfers.remove(resumed.transferId()); return;
                        }
                        RocketTransferEntities.discardAll(currentSources);
                        currentDestinations.stream().filter(other -> other != destination).forEach(RocketEntity::discard);
                        RocketTransferEntities.remountOnlinePassengers(
                                server, resumed, destination, resumed.destinationSnapshot().sourceOrigin());
                        if (RocketTransferEntities.isReplaceableLandedAuthority(destination, resumed)) {
                            settledTransfers.add(resumed.transferId());
                            liveTransfers.remove(resumed.transferId());
                        } else {
                            liveTransfers.add(resumed.transferId());
                            settledTransfers.remove(resumed.transferId());
                        }
                        io.github.sunthemoon.advancedrocketrycommunity.station.pad.LandingPadService.release(server, resumed);
                        // Report the observation that selected this action, not the post-rebind phase.
                        Result recovered = result(Status.RECOVERED, record, action, sources, destinations);
                        recovered.log(AdvancedRocketryCommunity.LOGGER,
                                RocketTransferEntities.isReplaceableLandedAuthority(destination, resumed));
                        completed(recovered, resumed);
                    });
                    return retryLater(record);
                }
                default -> throw new IllegalStateException("Unhandled transfer recovery action " + action);
            }
            if (status == Status.RETRY_LATER) { return retryLater(record); }
            io.github.sunthemoon.advancedrocketrycommunity.station.pad.LandingPadService.release(server, record);
            Result result = result(status, record, action, sources, destinations);
            result.log(AdvancedRocketryCommunity.LOGGER,
                    destinations.size() == 1
                            && RocketTransferEntities.isReplaceableLandedAuthority(destinations.get(0), record));
            return result;
        }
    }

    /**
     * W98 (I2): the record stays until the survivor's own slot, and every discarded copy's absence, are proved on disk.
     * A crew that is not yet placed (R3) waits as before, settled, and the last reconnect's effects follow the removal.
     */
    private Status settleRecoveredSource(
            MinecraftServer server,
            RocketTransferSavedData journal,
            RocketTransferRecord record,
            RocketOrdinaryRetirement.Held held,
            RocketEntity survivor,
            List<RocketEntity> discarded,
            Runnable recovered
    ) {
        if (RocketReleaseAccess.fenced(server, record.logicalRocketId())) { return Status.RETRY_LATER; }
        if (!bays.allowed(server, journal, record, RocketTransferBayRecoveryGate.Action.SETTLE_SOURCE)) { return Status.RETRY_LATER; }
        liveTransfers.remove(record.transferId());
        boolean ready = RocketRetirementGuards.crewReady(server, record, survivor, false);
        if (ready && !bays.allowed(server, journal, record, RocketTransferBayRecoveryGate.Action.REMOVE_JOURNAL)) { return Status.RETRY_LATER; }
        settledTransfers.add(record.transferId());
        retirements.begin(server, journal, RocketOrdinaryRetirement.Site.RECOVERED_SOURCE, record, survivor, discarded, false, held,
                !ready ? settlementReleased(server, journal, record, survivor) : RocketOrdinaryRetirement.Effects.of(() -> {
                    if (!bays.allowed(server, journal, record, RocketTransferBayRecoveryGate.Action.REMOVE_JOURNAL)) { return false; }
                    settledTransfers.remove(record.transferId());
                    io.github.sunthemoon.advancedrocketrycommunity.station.pad.LandingPadService.release(server, record);
                    recovered.run(); return true;
                }));
        return ready ? Status.RETRY_LATER : Status.WAITING_FOR_PASSENGERS;
    }

    /** The last reconnect's release of a settled source record, after its removal is acknowledged. */
    private RocketOrdinaryRetirement.Effects settlementReleased(MinecraftServer server, RocketTransferSavedData journal,
                                                               RocketTransferRecord record, RocketEntity authority) {
        return RocketOrdinaryRetirement.Effects.of(() -> {
            if (!bays.allowed(server, journal, record, RocketTransferBayRecoveryGate.Action.REMOVE_JOURNAL)) { return false; }
            liveTransfers.remove(record.transferId()); settledTransfers.remove(record.transferId());
            AdvancedRocketryCommunity.LOGGER.info(
                    "ARCE_TRANSFER_SOURCE_SETTLEMENT_RELEASED transfer={} logical={} entity={}",
                    record.transferId(),
                    record.logicalRocketId(),
                    authority.getUUID()
            );
            return true;
        });
    }

    private void completedSource(Result result, RocketTransferRecord record) {
        result.log(AdvancedRocketryCommunity.LOGGER, false);
        completed(result, record);
    }

    void completed(Result result, RocketTransferRecord record) {
        completed(result); completedRecords.put(result.transferId(), record);
    }

    void completed(Result result) {
        if (!completedResults.containsKey(result.transferId())
                && completedResults.size() == io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFlightLimits.MAX_ACTIVE_TRANSFERS) {
            UUID oldest = completedResults.keySet().iterator().next();
            completedResults.remove(oldest); completedRecords.remove(oldest);
        }
        completedResults.put(result.transferId(), result);
    }

    Result takeCompleted(UUID transferId) { completedRecords.remove(transferId); return completedResults.remove(transferId); }

    private void finishSourceSettlementIfComplete(
            MinecraftServer server,
            RocketTransferSavedData journal,
            RocketTransferRecord record,
            RocketEntity authority,
            List<RocketEntity> discarded
    ) {
        if (RocketReleaseAccess.fenced(server, record.logicalRocketId())) { return; }
        if (!journal.actionable(record.logicalRocketId())
                || !bays.allowed(server, journal, record, RocketTransferBayRecoveryGate.Action.SETTLE_SOURCE)
                || bays.source(record.transferId()) != null) { return; }
        // W96-R2: a reconnect during a live source phase only remounts; it never retires the flight's record.
        if (record.phase().destinationAuthoritative()
                || !recoveredStationarySource(record, authority, settledTransfers.contains(record.transferId()),
                        liveTransfers.contains(record.transferId()))) {
            return;
        }
        if (!bays.allowed(server, journal, record, RocketTransferBayRecoveryGate.Action.REMOVE_JOURNAL)) { return; }
        // W98 (I3): a waiting return is nudged; otherwise the survivor's own slot is proved before the record goes.
        try (var held = RocketOrdinaryRetirement.hold(journal, record.logicalRocketId())) {
            if (held.token() == null) { return; }
            retirements.begin(server, journal, RocketOrdinaryRetirement.Site.RECONNECT, record, authority, discarded, false, held,
                    settlementReleased(server, journal, record, authority));
        }
    }

    /**
     * W96-R2: the reconnect cleanup's authority is recovery's settled, non-live source: FUELED or ASSEMBLED, with no
     * plan and no active transfer, at its snapshot origin.
     */
    static boolean recoveredStationarySource(RocketTransferRecord record, RocketEntity authority, boolean settled, boolean live) {
        var origin = record.sourceSnapshot().sourceOrigin();
        return recoveredStationarySource(settled, live, authority.flightData().orElse(null),
                authority.getX() == origin.x() + 0.5D && authority.getY() == origin.y() && authority.getZ() == origin.z() + 0.5D);
    }

    static boolean recoveredStationarySource(boolean settled, boolean live, RocketFlightData flight, boolean atOrigin) {
        return settled && !live && atOrigin && flight != null
                && (flight.state() == RocketFlightState.FUELED || flight.state() == RocketFlightState.ASSEMBLED)
                && flight.plan().isEmpty() && flight.activeTransferId().isEmpty();
    }

    private static Result result(
            Status status,
            RocketTransferRecord record,
            RocketTransferRecoveryAction action,
            List<RocketEntity> sources,
            List<RocketEntity> destinations
    ) {
        return new Result(
                status,
                record.transferId(),
                record.phase(),
                action,
                sources.size(),
                destinations.size()
        );
    }

    private static Result retryLater(RocketTransferRecord record) {
        return new Result(
                Status.RETRY_LATER,
                record.transferId(),
                record.phase(),
                null,
                0,
                0
        );
    }

}
