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
    private int recoveryCursor;

    RocketTransferRecoveryService(Set<UUID> liveTransfers, Set<UUID> settledTransfers) {
        this.liveTransfers = Objects.requireNonNull(liveTransfers, "liveTransfers");
        this.settledTransfers = Objects.requireNonNull(settledTransfers, "settledTransfers");
    }

    Result recoverNext(MinecraftServer server, RocketTransferSavedData journal) {
        List<RocketTransferRecord> entries = journal.entries();
        Step step = step(entries, liveTransfers, settledTransfers, recoveryCursor,
                candidate -> RocketTransferEntities.recoveryEntityChunksLoaded(server, candidate));
        recoveryCursor = step.cursor();
        if (step.candidate() == null) {
            return Result.notFound(null);
        }
        return step.ready() ? recover(server, journal, step.candidate()) : retryLater(step.candidate());
    }

    /**
     * WARP review R7 and final review A1: one unclassified record per call, chosen round-robin by a
     * cursor. Its ends are checked with {@code loaded}, which may add a chunk ticket and load them, so
     * only one record is checked per call, as before R7. A record whose ends cannot be loaded (for
     * example, its Level is gone) advances the cursor, so it never starves the records behind it.
     */
    static Step step(List<RocketTransferRecord> entries, Set<UUID> live, Set<UUID> settled, int cursor,
                     java.util.function.Predicate<RocketTransferRecord> loaded) {
        List<RocketTransferRecord> unclassified = entries.stream()
                .filter(candidate -> !live.contains(candidate.transferId()))
                .filter(candidate -> !settled.contains(candidate.transferId()))
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
        if (!journal.operational()) {
            return Result.notFound(transferId);
        }
        RocketTransferRecord record = journal.find(transferId).orElse(null);
        if (record == null) {
            return Result.notFound(transferId);
        }
        if (!RocketTransferEntities.recoveryEntityChunksLoaded(server, record)) {
            return retryLater(record);
        }
        return recover(server, journal, record);
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
        reconnects.clear();
    }

    private boolean tryReconnect(ServerPlayer player, RocketTransferSavedData journal) {
        MinecraftServer server = player.getServer();
        if (server == null || journal.entries().isEmpty()) {
            return true;
        }
        if (!player.serverLevel().areEntitiesLoaded(player.chunkPosition().toLong())) {
            return false;
        }
        for (RocketTransferRecord record : journal.entries()) {
            RocketEntity settled = RocketTransferEntities.loadedSettledAuthority(server, record);
            if (settled == null && RocketTransferEntities.nearbyArrivalStillLoading(player, record)) {
                // A newly boarded passenger may be in the adjacent, already loaded chunk.
                // Wait for the nearby entity's current manifest without activating that chunk.
                return false;
            }
            var passengers = settled == null ? record.sourceFlightData().passengers()
                    : settled.flightData().orElseThrow().passengers();
            if (passengers.assignment(player.getUUID()).isEmpty()) {
                continue;
            }
            if (!RocketTransferEntities.authorityEntityChunkLoaded(server, record)) {
                return false;
            }
            List<RocketEntity> candidates = RocketTransferEntities.findMatches(
                    server, record, record.phase().destinationAuthoritative());
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
                finishSourceSettlementIfComplete(server, journal, record, authority);
            }
            break;
        }
        return true;
    }

    private Result recover(
            MinecraftServer server,
            RocketTransferSavedData journal,
            RocketTransferRecord record
    ) {
        List<RocketEntity> sources = RocketTransferEntities.findMatches(server, record, false);
        List<RocketEntity> destinations = RocketTransferEntities.findMatches(server, record, true);
        RocketTransferRecoveryAction action = RocketTransferRecoveryDecision.decide(
                record.phase(),
                new RocketTransferPresence(!sources.isEmpty(), !destinations.isEmpty())
        );
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
                status = settleRecoveredSource(server, journal, record);
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
                status = settleRecoveredSource(server, journal, record);
            }
            case KEEP_DESTINATION, REMOVE_SOURCE_KEEP_DESTINATION, REBUILD_DESTINATION -> {
                RocketTransferEntities.discardAll(sources);
                RocketEntity destination = destinations.isEmpty()
                        ? RocketTransferEntities.rebuildDestination(server, record)
                        : RocketTransferEntities.keepOne(destinations);
                if (destination == null) {
                    return result(Status.RETRY_LATER, record, action, sources, destinations);
                }
                RocketTransferRecord resumed = record.phase() == RocketTransferPhase.PREPARED
                        ? record.destinationSpawned(destination.getUUID())
                        : record.rebindDestination(destination.getUUID());
                journal.put(resumed);
                journal.flush(server);
                RocketTransferEntities.remountOnlinePassengers(
                        server,
                        resumed,
                        destination,
                        resumed.destinationSnapshot().sourceOrigin()
                );
                if (RocketTransferEntities.isReplaceableLandedAuthority(destination, resumed)) {
                    settledTransfers.add(resumed.transferId());
                    liveTransfers.remove(resumed.transferId());
                } else {
                    liveTransfers.add(resumed.transferId());
                    settledTransfers.remove(resumed.transferId());
                }
                status = Status.RECOVERED;
            }
            default -> throw new IllegalStateException("Unhandled transfer recovery action " + action);
        }
        Result result = result(status, record, action, sources, destinations);
        result.log(AdvancedRocketryCommunity.LOGGER,
                destinations.size() == 1
                        && RocketTransferEntities.isReplaceableLandedAuthority(destinations.get(0), record));
        return result;
    }

    private Status settleRecoveredSource(
            MinecraftServer server,
            RocketTransferSavedData journal,
            RocketTransferRecord record
    ) {
        liveTransfers.remove(record.transferId());
        if (allPassengersOnline(server, record)) {
            journal.remove(record.transferId());
            journal.flush(server);
            settledTransfers.remove(record.transferId());
            return Status.RECOVERED;
        }
        settledTransfers.add(record.transferId());
        return Status.WAITING_FOR_PASSENGERS;
    }

    private void finishSourceSettlementIfComplete(
            MinecraftServer server,
            RocketTransferSavedData journal,
            RocketTransferRecord record,
            RocketEntity authority
    ) {
        if (record.phase().destinationAuthoritative()
                || authority.flightData().map(RocketFlightData::state)
                        .orElse(RocketFlightState.FAILED_RECOVERABLE) == RocketFlightState.LANDED
                || !allPassengersOnline(server, record)) {
            return;
        }
        journal.remove(record.transferId());
        journal.flush(server);
        liveTransfers.remove(record.transferId());
        settledTransfers.remove(record.transferId());
        AdvancedRocketryCommunity.LOGGER.info(
                "ARCE_TRANSFER_SOURCE_SETTLEMENT_RELEASED transfer={} logical={} entity={}",
                record.transferId(),
                record.logicalRocketId(),
                authority.getUUID()
        );
    }

    private static boolean allPassengersOnline(MinecraftServer server, RocketTransferRecord record) {
        return record.sourceFlightData().passengers().assignments().stream()
                .allMatch(seat -> server.getPlayerList().getPlayer(seat.passengerId()) != null);
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
