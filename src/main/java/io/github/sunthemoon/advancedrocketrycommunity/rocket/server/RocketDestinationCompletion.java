package io.github.sunthemoon.advancedrocketrycommunity.rocket.server;

import static io.github.sunthemoon.advancedrocketrycommunity.rocket.server.RocketTransferAudit.auditPhase;
import static io.github.sunthemoon.advancedrocketrycommunity.rocket.server.RocketTransferBayRecoveryGate.Action.TICK_SOURCE;

import io.github.sunthemoon.advancedrocketrycommunity.AdvancedRocketryCommunity;
import io.github.sunthemoon.advancedrocketrycommunity.progression.classic.firstevent.ClassicFirstEventDelivery;
import io.github.sunthemoon.advancedrocketrycommunity.progression.classic.firstevent.FirstEventDispositionSource;
import io.github.sunthemoon.advancedrocketrycommunity.progression.classic.firstevent.FirstEventKind;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.entity.RocketEntity;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFlightData;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFlightLimits;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFlightState;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketTransferRecord;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.persistence.RocketTransferSavedData;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;

/**
 * The COMMITTED destination tick, moved from {@code RocketTransferService} (W44 N6): descent, the descent-complete
 * step through {@link MoonLandingCompletion}, and the landed-reservation cleanup with the schema-1 migration branch.
 * The transfer-ID sets are the service's, passed by reference as the recovery service receives them.
 */
final class RocketDestinationCompletion {
    private final Set<UUID> liveTransfers;
    private final Set<UUID> settledTransfers;
    private final RocketTransferBayDispatch bays;
    private final FirstEventDispositionSource dispositions;

    RocketDestinationCompletion(Set<UUID> liveTransfers, Set<UUID> settledTransfers, RocketTransferBayDispatch bays) {
        this(liveTransfers, settledTransfers, bays, FirstEventDispositionSource.WORLD);
    }

    /** With a detached delivery's dispositions, for the isolated A1 GameTests only. */
    RocketDestinationCompletion(Set<UUID> liveTransfers, Set<UUID> settledTransfers, RocketTransferBayDispatch bays,
                                FirstEventDispositionSource dispositions) {
        this.liveTransfers = liveTransfers;
        this.settledTransfers = settledTransfers;
        this.bays = bays;
        this.dispositions = dispositions;
    }

    void tickDestination(
            MinecraftServer server,
            RocketTransferSavedData journal,
            RocketTransferRecord record
    ) {
        if (!journal.actionable(record.logicalRocketId()) || journal.find(record.transferId()).orElse(null) != record) { return; }
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
                UUID destinationId = destination.getUUID();
                var completion = MoonLandingCompletion.complete(server, journal, record, destination, bays,
                        () -> publishLanding(server, journal, record, destinationId), dispositions);
                if (completion == MoonLandingCompletion.Result.ORDINARY) {
                    land(record, destination);
                } else if (destination.flightData().orElseThrow().state() == RocketFlightState.DESCENT) {
                    // W44 section 2.3: hold the terminal pose until the exact confirmation; not LANDED, so the
                    // reservation stays. Dismounting is never cancelled.
                    RocketTransferEntities.positionAtOrigin(destination, record.destinationSnapshot().sourceOrigin());
                }
            }
        }
        cleanupLandedIfComplete(server, journal, record, destination);
    }

    /** The ordinary landing: LANDED, the terminal pose, feedback, the audit line and {@code moon_landing}. */
    private static void land(RocketTransferRecord record, RocketEntity destination) {
        RocketFlightData flight = destination.flightData().orElseThrow();
        destination.updateFlightData(flight.land(destination.level().getGameTime()));
        RocketTransferEntities.positionAtOrigin(destination, record.destinationSnapshot().sourceOrigin());
        RocketFlightFeedback.landed(destination);
        auditPhase(record, "landing_complete", destination.getUUID());
        io.github.sunthemoon.advancedrocketrycommunity.progression.classic.ClassicAdvancementEvents
                .rocketLanded((ServerLevel) destination.level(), RocketTransferEntities.aboard(destination));
    }

    /**
     * The confirmed world-first LANDED publication (W44 section 2.3, step 7). It rechecks the authoritative record, the
     * escrow and bay fence and the unique DESCENT destination immediately before LANDED; a stale continuation is
     * discarded, and the next tick projects the retained event again.
     */
    private boolean publishLanding(MinecraftServer server, RocketTransferSavedData journal,
                                   RocketTransferRecord record, UUID destinationId) {
        if (journal.find(record.transferId()).orElse(null) != record || !flightAllowed(server, journal, record)) {
            return false;
        }
        List<RocketEntity> matches = RocketTransferEntities.loadedMatches(server, record, true);
        if (matches.size() != 1 || !matches.get(0).getUUID().equals(destinationId)) {
            return false;
        }
        RocketEntity destination = matches.get(0);
        if (destination.flightData().map(RocketFlightData::state).orElse(null) != RocketFlightState.DESCENT) {
            return false;
        }
        land(record, destination);
        ClassicFirstEventDelivery.sourceChanged(server, FirstEventKind.FIRST_MOON_LANDING);
        return true;
    }

    private void cleanupLandedIfComplete(
            MinecraftServer server,
            RocketTransferSavedData journal,
            RocketTransferRecord record,
            RocketEntity destination
    ) {
        if (!flightAllowed(server, journal, record)) { return; }
        if (!RocketTransferEntities.isReplaceableLandedAuthority(destination, record)) {
            return;
        }
        if (record.schemaVersion() == 1) {
            Optional<RocketTransferRecord> migrated = RocketRuntime.migrateCommittedLegacyTransfer(
                    server,
                    record
            );
            if (migrated.isPresent()) {
                try (var reservation = journal.reserve(record.logicalRocketId()).orElse(null)) {
                    if (reservation == null) { return; }
                    record = migrated.orElseThrow();
                    journal.migrateCommitted(record);
                    RocketTransferRecord changed = record;
                    journal.afterAcknowledged(server, reservation, () -> {
                        if (!flightAllowed(server, journal, changed)) { return; }
                        liveTransfers.add(changed.transferId());
                        AdvancedRocketryCommunity.LOGGER.info("ARCE_TRANSFER_SCHEMA_MIGRATED transfer={} schema={}",
                                changed.transferId(), changed.schemaVersion());
                    });
                    return;
                }
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

    private boolean flightAllowed(MinecraftServer server, RocketTransferSavedData journal, RocketTransferRecord record) {
        return RocketTransferEscrowRules.flightAllowed(server, journal, record, bays, TICK_SOURCE);
    }

    private static long elapsed(long gameTime, long started) {
        return gameTime <= started ? 0L : gameTime - started;
    }
}
