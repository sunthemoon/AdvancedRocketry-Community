package io.github.sunthemoon.advancedrocketrycommunity.rocket.server;

import static io.github.sunthemoon.advancedrocketrycommunity.rocket.server.RocketTransferBayRecoveryGate.Action.*;

import io.github.sunthemoon.advancedrocketrycommunity.AdvancedRocketryCommunity;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.entity.RocketEntity;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketTransferPhase;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketTransferRecord;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.persistence.RocketTransferSavedData;
import io.github.sunthemoon.advancedrocketrycommunity.station.pad.LandingPadService;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.minecraft.server.MinecraftServer;

/** Reservation-before-mutation boundaries for the ordinary transfer service. */
final class RocketTransferContinuations {
    private final Set<UUID> live;
    private final RocketTransferBayDispatch bays;
    private final RocketOrdinaryRetirement retirements;

    RocketTransferContinuations(Set<UUID> live, RocketTransferBayDispatch bays, RocketOrdinaryRetirement retirements) {
        this.live = live; this.bays = bays; this.retirements = retirements;
    }

    boolean failBack(MinecraftServer server, RocketTransferSavedData journal, RocketTransferRecord record,
                     RocketEntity source, RocketTransferReturnReason reason, RocketRequestCompletion completion) {
        if (record.nativeTransfer().isPresent()) {
            return RocketGovernedSourceDispatcher.requestReturn(server, journal, record, source, bays, reason, completion.accepted());
        }
        if (!journal.actionable(record.logicalRocketId())
                || !RocketTransferEscrowRules.flightAllowed(server, journal, record, bays, SETTLE_SOURCE)
                || !bays.allowed(server, journal, record, REMOVE_JOURNAL)) { return false; }
        // W98: the current T by ID, not the callback's object; a refused publication's T is already DESTINATION_SPAWNED.
        var current = journal.find(record.transferId()).orElse(null);
        if (current == null || current.phase() != RocketTransferPhase.PREPARED && current.phase() != RocketTransferPhase.DESTINATION_SPAWNED
                || !current.sourceEntityId().equals(source.getUUID()) || RocketOrdinaryRetirement.destinationAdmitted(server, current)
                || !retirements.admits(current, source, List.of(), List.of())) { return false; }
        try (var held = RocketOrdinaryRetirement.hold(journal, current.logicalRocketId())) {
            if (held.token() == null) { return false; }
            var safe = RocketTransferEntities.stationarySource(current, source.level().getGameTime());
            source.updateFlightData(safe);
            RocketTransferEntities.positionAtOrigin(source, current.sourceSnapshot().sourceOrigin());
            // W96-R12: provisional; a kill before the proof can still recover the flight from T.
            RocketFlightFeedback.returnedToSource(source);
            RocketTransferEntities.remountOnlinePassengers(server, current, source, current.sourceSnapshot().sourceOrigin());
            // W98 (I1): T stays until the survivor's own slot is read back; the request completes on T's removal.
            retirements.begin(server, journal, RocketOrdinaryRetirement.Site.FAIL_BACK, current, source, List.of(),
                    reason == RocketTransferReturnReason.COUNTDOWN_CANCELLED, held, new RocketOrdinaryRetirement.Effects(() -> {
                        if (!bays.allowed(server, journal, current, REMOVE_JOURNAL)) { return false; }
                        live.remove(current.transferId());
                        LandingPadService.release(server, current);
                        reason.log(AdvancedRocketryCommunity.LOGGER, current.transferId(), current.logicalRocketId(), safe.fuel().amount());
                        completion.accepted().run(); return true;
                    }, completion.discarded()));
            return true;
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
