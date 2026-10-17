package io.github.sunthemoon.advancedrocketrycommunity.rocket.server;

import static io.github.sunthemoon.advancedrocketrycommunity.rocket.server.RocketTransferBayRecoveryGate.Action.*;

import io.github.sunthemoon.advancedrocketrycommunity.AdvancedRocketryCommunity;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.entity.RocketEntity;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketTransferPhase;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketTransferRecord;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.persistence.RocketTransferSavedData;
import io.github.sunthemoon.advancedrocketrycommunity.station.pad.LandingPadService;
import java.util.Set;
import java.util.UUID;
import net.minecraft.server.MinecraftServer;

/** Reservation-before-mutation boundaries for the ordinary transfer service. */
final class RocketTransferContinuations {
    private final Set<UUID> live;
    private final RocketTransferBayDispatch bays;

    RocketTransferContinuations(Set<UUID> live, RocketTransferBayDispatch bays) {
        this.live = live; this.bays = bays;
    }

    boolean failBack(MinecraftServer server, RocketTransferSavedData journal, RocketTransferRecord record,
                     RocketEntity source, RocketTransferReturnReason reason, RocketRequestCompletion completion) {
        if (record.nativeTransfer().isPresent()) {
            return RocketGovernedSourceDispatcher.requestReturn(server, journal, record, source, bays, reason, completion.accepted());
        }
        if (!journal.actionable(record.logicalRocketId())
                || !RocketTransferEscrowRules.flightAllowed(server, journal, record, bays, SETTLE_SOURCE)
                || !bays.allowed(server, journal, record, REMOVE_JOURNAL)) { return false; }
        try (var reservation = journal.reserve(record.logicalRocketId()).orElse(null)) {
            if (reservation == null) { return false; }
            var safe = RocketTransferEntities.stationarySource(record, source.level().getGameTime());
            source.updateFlightData(safe);
            RocketTransferEntities.positionAtOrigin(source, record.sourceSnapshot().sourceOrigin());
            RocketFlightFeedback.returnedToSource(source);
            RocketTransferEntities.remountOnlinePassengers(server, record, source, record.sourceSnapshot().sourceOrigin());
            journal.remove(record.transferId());
            return journal.afterAcknowledged(server, reservation, () -> {
                if (!bays.allowed(server, journal, record, REMOVE_JOURNAL)) { return false; }
                live.remove(record.transferId());
                LandingPadService.release(server, record);
                reason.log(AdvancedRocketryCommunity.LOGGER, record.transferId(), record.logicalRocketId(), safe.fuel().amount());
                completion.accepted().run(); return true;
            }, completion.discarded());
        }
    }

    boolean update(MinecraftServer server, RocketTransferSavedData journal, RocketTransferRecord updated,
                   RocketTransferBayRecoveryGate.Action action, Runnable effect) {
        if (!journal.actionable(updated.logicalRocketId())
                || !RocketTransferEscrowRules.flightAllowed(server, journal, updated, bays, action)) { return false; }
        try (var reservation = journal.reserve(updated.logicalRocketId()).orElse(null)) {
            if (reservation == null) { return false; }
            journal.put(updated);
            return journal.afterAcknowledged(server, reservation, () -> {
                if (!RocketTransferEscrowRules.flightAllowed(server, journal, updated, bays, action)) { return; }
                live.add(updated.transferId());
                if (updated.phase() == RocketTransferPhase.COMMITTED) { LandingPadService.release(server, updated); }
                RocketTransferAudit.auditPhase(updated, updated.phase().name().toLowerCase(java.util.Locale.ROOT),
                        updated.destinationEntityId().orElse(updated.sourceEntityId()));
                effect.run();
            });
        }
    }
}
