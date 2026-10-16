package io.github.sunthemoon.advancedrocketrycommunity.rocket.server;

import io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.EscrowGovernedDispatch;
import io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.EscrowGovernedSourceFlight;
import io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.EscrowRuntime;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.entity.RocketEntity;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFlightLimits;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketTransferPhase;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketTransferRecord;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.persistence.RocketTransferSavedData;
import java.util.List;
import net.minecraft.server.MinecraftServer;

/** Fair, loaded-only F3-F5 dispatch. Missing sources and destination work never enter legacy recovery. */
final class RocketGovernedSourceDispatcher {
    static final int VISITS_PER_TICK = 8;
    private int cursor;

    void tick(MinecraftServer server, RocketTransferSavedData journal, RocketTransferBayDispatch bays) {
        cursor = visit(journal.entries(), cursor, true, record -> {
            var boundary = bays.before(server, journal, record, RocketTransferBayRecoveryGate.Action.TICK_SOURCE);
            if (boundary == null || boundary.kind() != RocketTransferBayRecoveryGate.BoundaryKind.UNCLAIMED
                    || EscrowRuntime.of(server).governedFlights().classify(server, journal, record).route()
                    != EscrowGovernedDispatch.Route.ACTIVE) { return; }
            var level = RocketTransferEntities.level(server, record.sourceSnapshot().sourceDimension());
            if (level == null) { return; }
            var entity = level.getEntity(record.sourceEntityId());
            if (entity != null && !(entity instanceof RocketEntity)) { return; }
            var source = (RocketEntity) entity;
            if (source != null && !source.operational()) { return; }
            if (record.phase() != RocketTransferPhase.PREPARED) {
                destination(server, journal, record, source, bays); return;
            }
            if (source == null) { return; }
            var previous = source.flightData().orElseThrow().state();
            var result = EscrowRuntime.of(server).sourceFlights().tick(server, journal, record, source,
                    () -> RocketGovernedLaunchBoundary.allows(server, journal, record, source, bays));
            if (result == EscrowGovernedSourceFlight.Result.HELD) { return; }
            // Positive complete-source validation precedes even these recorded-end tickets.
            RocketTransferEntities.keepLoaded(level, record.sourceSnapshot(), record.transferId());
            var destination = RocketTransferEntities.level(server, record.destinationSnapshot().sourceDimension());
            RocketTransferEntities.keepLoaded(destination, record.destinationSnapshot(), record.transferId());
            var state = source.flightData().orElseThrow().state();
            if (state != previous) {
                switch (state) {
                    case COUNTDOWN -> RocketFlightFeedback.countdownAccepted(source);
                    case ASCENT -> RocketFlightFeedback.ascentStarted(source);
                    case TRANSIT -> RocketFlightFeedback.transitStarted(source);
                    default -> { }
                }
            } else if (result == EscrowGovernedSourceFlight.Result.ASCENT) {
                RocketFlightFeedback.ascentTrail(source, level.getGameTime() - source.flightData().orElseThrow().stateStartedGameTime());
            }
            if (result == EscrowGovernedSourceFlight.Result.DESTINATION_DUE) {
                destination(server, journal, record, source, bays);
            }
        });
    }

    private static void destination(MinecraftServer server, RocketTransferSavedData journal, RocketTransferRecord record,
                                    RocketEntity source, RocketTransferBayDispatch bays) {
        var result = EscrowRuntime.of(server).destinations().tick(server, journal, record, source,
                (checked, from, to) -> RocketGovernedLaunchBoundary.allows(server, journal, checked, from, to, bays)
                        && EscrowRuntime.of(server).flightPhases().allowsNative(from, to)
                        && (to != null || landingAvailable(server, checked)));
        if (result != io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.EscrowGovernedDestination.Result.HELD) {
            // A positive checked-source/candidate boundary precedes these existing recorded-end tickets.
            RocketTransferEntities.keepLoaded(RocketTransferEntities.level(server, record.sourceSnapshot().sourceDimension()),
                    record.sourceSnapshot(), record.transferId());
            RocketTransferEntities.keepLoaded(RocketTransferEntities.level(server, record.destinationSnapshot().sourceDimension()),
                    record.destinationSnapshot(), record.transferId());
        }
        if (result == io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.EscrowGovernedDestination.Result.PRESENT) {
            var level = RocketTransferEntities.level(server, record.destinationSnapshot().sourceDimension());
            var arrival = (RocketEntity) level.getEntity(record.nativeTransfer().orElseThrow().armed().destination());
            EscrowRuntime.of(server).flightPhases().tick(server, journal, record, source, arrival,
                    () -> RocketGovernedLaunchBoundary.allows(server, journal, record,
                            source != null && source.isRemoved() ? null : source, arrival, bays)
                            && EscrowRuntime.of(server).flightPhases().allowsNative(source, arrival));
        }
    }

    private static boolean landingAvailable(MinecraftServer server, RocketTransferRecord record) {
        var level = RocketTransferEntities.level(server, record.destinationSnapshot().sourceDimension());
        if (level == null || !RocketTransferEntities.destinationEntityChunkReady(level, record.destinationSnapshot())) { return false; }
        var plan = record.sourceFlightData().plan().orElseThrow();
        return plan.landingPad() == null
                ? new io.github.sunthemoon.advancedrocketrycommunity.rocket.transfer.RocketLandingPadSelector()
                        .availableLoaded(level, record.destinationSnapshot())
                : new io.github.sunthemoon.advancedrocketrycommunity.rocket.transfer.NamedLandingPadStaging()
                        .recheck(level, record.destinationSnapshot(), plan.destinationStationId(), plan.landingPad(), record.transferId());
    }

    static int visit(List<RocketTransferRecord> records, int cursor, java.util.function.Consumer<RocketTransferRecord> action) {
        return visit(records, cursor, false, action);
    }

    static int visit(List<RocketTransferRecord> records, int cursor, boolean destinations,
                     java.util.function.Consumer<RocketTransferRecord> action) {
        var sources = records.stream().filter(record -> record.nativeTransfer().isPresent()
                && (record.phase() == RocketTransferPhase.PREPARED || destinations))
                .limit(RocketFlightLimits.MAX_ACTIVE_TRANSFERS).toList();
        if (sources.isEmpty()) { return 0; }
        int count = Math.min(VISITS_PER_TICK, sources.size());
        int start = Math.floorMod(cursor, sources.size());
        for (int index = 0; index < count; index++) { action.accept(sources.get((start + index) % sources.size())); }
        return (start + count) % sources.size();
    }

    void clear() { cursor = 0; }
}
