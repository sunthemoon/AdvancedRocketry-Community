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

/** Fair, loaded-only F3 dispatch. Neither missing sources nor destination work enter legacy recovery. */
final class RocketGovernedSourceDispatcher {
    static final int VISITS_PER_TICK = 8;
    private int cursor;

    void tick(MinecraftServer server, RocketTransferSavedData journal, RocketTransferBayDispatch bays) {
        cursor = visit(journal.entries(), cursor, record -> {
            var boundary = bays.before(server, journal, record, RocketTransferBayRecoveryGate.Action.TICK_SOURCE);
            if (boundary == null || boundary.kind() != RocketTransferBayRecoveryGate.BoundaryKind.UNCLAIMED
                    || EscrowRuntime.of(server).governedFlights().classify(server, journal, record).route()
                    != EscrowGovernedDispatch.Route.ACTIVE) { return; }
            var level = RocketTransferEntities.level(server, record.sourceSnapshot().sourceDimension());
            if (level == null || !(level.getEntity(record.sourceEntityId()) instanceof RocketEntity source)
                    || !source.operational()) { return; }
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
            // DESTINATION_DUE remains ARMED/PREPARED until the separate F4 publication executor is connected.
        });
    }

    static int visit(List<RocketTransferRecord> records, int cursor, java.util.function.Consumer<RocketTransferRecord> action) {
        var sources = records.stream().filter(record -> record.nativeTransfer().isPresent()
                && record.phase() == RocketTransferPhase.PREPARED).limit(RocketFlightLimits.MAX_ACTIVE_TRANSFERS).toList();
        if (sources.isEmpty()) { return 0; }
        int count = Math.min(VISITS_PER_TICK, sources.size());
        int start = Math.floorMod(cursor, sources.size());
        for (int index = 0; index < count; index++) { action.accept(sources.get((start + index) % sources.size())); }
        return (start + count) % sources.size();
    }

    void clear() { cursor = 0; }
}
