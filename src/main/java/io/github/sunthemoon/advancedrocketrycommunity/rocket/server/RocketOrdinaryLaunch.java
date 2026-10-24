package io.github.sunthemoon.advancedrocketrycommunity.rocket.server;

import io.github.sunthemoon.advancedrocketrycommunity.AdvancedRocketryCommunity;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.entity.RocketEntity;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFlightData;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFlightRequestCode;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFlightRequestResult;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketTransferRecord;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.persistence.RocketTransferSavedData;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.transaction.RocketRegion;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.transfer.NamedLandingPadStaging;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.transfer.RocketLandingPadSelection;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.transfer.RocketLandingPadSelector;
import io.github.sunthemoon.advancedrocketrycommunity.station.pad.LandingPadService;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Supplier;
import net.minecraft.server.level.ServerLevel;

/** Ordinary launch publication; a fresh launch is unchanged, I7 delegates its replacement to the shared proof owner. */
final class RocketOrdinaryLaunch {
    private final Set<UUID> live, settled;
    private final RocketTransferRecoveryService recovery;

    RocketOrdinaryLaunch(Set<UUID> live, Set<UUID> settled, RocketTransferRecoveryService recovery) {
        this.live = live; this.settled = settled; this.recovery = recovery;
    }

    RocketFlightRequestResult prepare(RocketEntity rocket, RocketFlightData countdown, RocketLandingPadSelection selected,
            RocketTransferRecord previous, RocketRequestCompletion completion, BooleanSupplier authorized,
            Supplier<List<RocketRegion>> otherReservations, Consumer<RocketTransferRecord> prepared) {
        var source = (ServerLevel) rocket.level(); var server = source.getServer();
        var journal = RocketTransferSavedData.get(server); var plan = countdown.plan().orElseThrow();
        try (var held = RocketOrdinaryRetirement.hold(journal, countdown.logicalRocketId())) {
            var token = held.token();
            if (token == null) { LandingPadService.release(server, plan); return blocked(); }
            var transit = RocketTransferService.scheduledTransit(countdown); var debit = transit.fuel().debit(plan);
            if (!debit.success()) {
                LandingPadService.release(server, plan);
                return RocketFlightRequestResult.failure(RocketFlightRequestCode.INSUFFICIENT_FUEL, plan.requiredFuel());
            }
            var snapshot = selected.snapshot().orElseThrow();
            var arrival = transit.arriveAtDestination(debit.state(), plan.destinationBody(), plan.destinationDimension(),
                    snapshot.sourceOrigin(), RocketTransferService.scheduledArrival(countdown));
            var record = RocketTransferRecord.create(plan.requestId(), countdown.logicalRocketId(), rocket.ownerId().orElseThrow(),
                    rocket.getUUID(), rocket.snapshot().orElseThrow(), snapshot, transit, arrival, plan.requiredFuel(), countdown.stateStartedGameTime());
            BooleanSupplier acknowledged = () -> {
                var current = previous == null ? rocket : source.getEntity(record.sourceEntityId()) instanceof RocketEntity found ? found : null;
                if (!RocketTransferEntities.currentEntity(current, record, false)
                        || !RocketTransferEscrowRules.flightAllowed(server, journal, record, recovery.bays(),
                        RocketTransferBayRecoveryGate.Action.TICK_SOURCE)) {
                    io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.EscrowLaunchDiscard.note("PREPARED_CALLBACK",
                            () -> "logical=" + record.logicalRocketId() + " transfer=" + record.transferId() + " reason=SOURCE_OR_ACTION_BOUNDARY");
                    live.remove(record.transferId()); return false;
                }
                if (previous != null) {
                    live.remove(previous.transferId()); settled.remove(previous.transferId());
                    LandingPadService.release(server, previous);
                }
                RocketTransferEntities.keepLoaded(source, record.sourceSnapshot(), record.transferId());
                RocketTransferEntities.keepDestinationLoaded(RocketTransferEntities.level(server, plan.destinationDimension()),
                        record.destinationSnapshot(), record.transferId());
                current.updateFlightData(countdown.cancelCountdown(source.getGameTime()).startCountdown(source.getGameTime()));
                RocketFlightFeedback.countdownAccepted(current); live.add(record.transferId());
                prepared.accept(record); completion.accepted().run(); return true;
            };
            if (previous == null) {
                journal.put(record);
                if (!journal.afterAcknowledged(server, token, acknowledged, completion.discarded())) { return blocked(); }
            } else {
                var retirements = recovery.retirements();
                if (previous.nativeTransfer().isPresent() || !settled.contains(previous.transferId()) || live.contains(previous.transferId())
                        || retirements.pending(record.logicalRocketId()) || retirements.size() >= RocketOrdinaryRetirement.MAX_PENDING) {
                    LandingPadService.release(server, plan); return blocked();
                }
                var port = recovery.replacementPort(server, journal, rocket, previous, record, authorized,
                        () -> destinationAvailable(rocket, record, otherReservations.get()));
                if (!port.requestCurrent() || !authorized.getAsBoolean() || !port.escrowAllows(journal, previous, token)) {
                    LandingPadService.release(server, plan); return blocked();
                }
                Runnable discarded = () -> {
                    // Once replacement is submitted, its candidate (including UNKNOWN) owns the new pad, not this request.
                    if (journal.find(previous.transferId()).orElse(null) == previous && journal.find(record.transferId()).isEmpty()) {
                        LandingPadService.release(server, plan);
                    }
                    completion.discarded().run();
                };
                retirements.begin(port, journal, RocketOrdinaryRetirement.Site.BEFORE_REPLACE, previous, rocket.getUUID(),
                        List.of(), false, held, new RocketOrdinaryRetirement.Effects(acknowledged, discarded));
            }
        } catch (RuntimeException exception) {
            // A retained successor is not rollback permission, even if acknowledgement or a callback threw.
            if (journal.find(plan.requestId()).isEmpty()) { LandingPadService.release(server, plan); }
            live.remove(plan.requestId());
            AdvancedRocketryCommunity.LOGGER.error("ARCE_TRANSFER_PREPARE_FAILED transfer={} logical={} source={}",
                    plan.requestId(), countdown.logicalRocketId(), rocket.getUUID(), exception);
            return RocketFlightRequestResult.failure(RocketFlightRequestCode.TRANSFER_PREPARE_FAILED, plan.requiredFuel());
        }
        return new RocketFlightRequestResult(RocketFlightRequestCode.JOURNAL_PENDING, plan.requiredFuel());
    }

    private static boolean destinationAvailable(RocketEntity rocket, RocketTransferRecord record, List<RocketRegion> reservations) {
        var server = rocket.getServer(); var plan = record.sourceFlightData().plan().orElseThrow();
        var destination = RocketTransferEntities.level(server, plan.destinationDimension());
        var region = RocketRegion.fromSnapshot(record.destinationSnapshot());
        if (destination == null || reservations.stream().anyMatch(region::overlaps)) { return false; }
        return plan.landingPad() == null ? new RocketLandingPadSelector().availableLoaded(destination, record.destinationSnapshot())
                : new NamedLandingPadStaging().recheck(destination, record.destinationSnapshot(), plan.destinationStationId(),
                        plan.landingPad(), record.transferId());
    }

    private static RocketFlightRequestResult blocked() {
        return RocketFlightRequestResult.failure(RocketFlightRequestCode.TRANSFER_JOURNAL_BLOCKED);
    }
}
