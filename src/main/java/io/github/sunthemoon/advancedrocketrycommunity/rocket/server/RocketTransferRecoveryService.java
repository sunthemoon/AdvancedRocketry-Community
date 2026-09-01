package io.github.sunthemoon.advancedrocketrycommunity.rocket.server;

import io.github.sunthemoon.advancedrocketrycommunity.AdvancedRocketryCommunity;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.entity.RocketEntity;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFlightData;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFlightState;
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
    }

    private final Set<UUID> liveTransfers;
    private final Set<UUID> settledTransfers;

    RocketTransferRecoveryService(Set<UUID> liveTransfers, Set<UUID> settledTransfers) {
        this.liveTransfers = Objects.requireNonNull(liveTransfers, "liveTransfers");
        this.settledTransfers = Objects.requireNonNull(settledTransfers, "settledTransfers");
    }

    Result recoverNext(MinecraftServer server, RocketTransferSavedData journal) {
        RocketTransferRecord record = journal.entries().stream()
                .filter(candidate -> !liveTransfers.contains(candidate.transferId()))
                .filter(candidate -> !settledTransfers.contains(candidate.transferId()))
                .findFirst()
                .orElse(null);
        return record == null ? Result.notFound(null) : recover(server, journal, record);
    }

    Result recoverById(MinecraftServer server, UUID transferId) {
        Objects.requireNonNull(server, "server");
        Objects.requireNonNull(transferId, "transferId");
        RocketTransferSavedData journal = RocketTransferSavedData.get(server);
        if (!journal.operational()) {
            return Result.notFound(transferId);
        }
        RocketTransferRecord record = journal.find(transferId).orElse(null);
        return record == null ? Result.notFound(transferId) : recover(server, journal, record);
    }

    void onPlayerLoggedIn(ServerPlayer player, RocketTransferSavedData journal) {
        MinecraftServer server = player.getServer();
        if (server == null) {
            return;
        }
        for (RocketTransferRecord record : journal.entries()) {
            if (record.sourceFlightData().passengers().assignment(player.getUUID()).isEmpty()) {
                continue;
            }
            RocketEntity authority = RocketTransferEntities.authorityEntity(server, record);
            if (authority != null) {
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
                if (destination.flightData().map(RocketFlightData::state)
                        .orElse(RocketFlightState.FAILED_RECOVERABLE) == RocketFlightState.LANDED) {
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
        audit(result);
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

    private static void audit(Result result) {
        AdvancedRocketryCommunity.LOGGER.warn(
                "ARCE_TRANSFER_RECOVERY transfer={} phase={} source_count={} destination_count={} action={} status={}",
                result.transferId(),
                result.phase(),
                result.sourceCount(),
                result.destinationCount(),
                result.action(),
                result.status()
        );
    }
}
