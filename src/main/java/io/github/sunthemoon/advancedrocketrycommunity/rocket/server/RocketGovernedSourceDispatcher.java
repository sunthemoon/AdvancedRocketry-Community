package io.github.sunthemoon.advancedrocketrycommunity.rocket.server;

import io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.EscrowGovernedDispatch;
import io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.EscrowGovernedSourceFlight;
import io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.EscrowGovernedTerminalPassengers;
import io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.EscrowFlightTables;
import io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.EscrowRuntime;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.entity.RocketEntity;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFlightLimits;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFlightState;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketTransferPhase;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketTransferRecord;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.persistence.RocketTransferSavedData;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.transfer.RocketDiscoveryColumns;
import java.util.List;
import net.minecraft.server.MinecraftServer;

/** Fair F3-F5 dispatch. Only checked ACTIVE records may activate their bounded, recorded ends. */
final class RocketGovernedSourceDispatcher {
    static final int VISITS_PER_TICK = 8;
    private int cursor;
    private final RocketGovernedWaits waits = new RocketGovernedWaits();

    void tick(MinecraftServer server, RocketTransferSavedData journal, RocketTransferBayDispatch bays) {
        var holds = EscrowRuntime.of(server).executorHolds();
        holds.retain(journal.entries()); waits.retain(journal.entries());
        cursor = visit(journal.entries(), cursor, true, record -> {
            var boundary = bays.before(server, journal, record, RocketTransferBayRecoveryGate.Action.TICK_SOURCE);
            if (boundary == null || boundary.kind() != RocketTransferBayRecoveryGate.BoundaryKind.UNCLAIMED) {
                waits.excluded(record.transferId()); return;
            }
            var decision = EscrowRuntime.of(server).governedFlights().classify(server, journal, record);
            afterClassification(decision, () -> holds.held(record),
                    () -> waits.tickets(record.transferId(), RocketTransferEntities.refreshGovernedFlightTickets(server, record)),
                    () -> ready(server, journal, record, bays),
                    () -> dispatchLoaded(server, journal, record, bays));
            if (decision.route() != EscrowGovernedDispatch.Route.ACTIVE || holds.held(record)) { waits.excluded(record.transferId()); }
        });
    }

    /**
     * W90 (R21 item 2): a recorded end outside the checked column set is a {@code DISCOVERY_MARGIN} hold before any
     * readiness, execution or refresh. Otherwise the unchanged readiness predicate decides; an unheld visit also
     * records its wait (R21 item 6) from read-only checks.
     */
    private boolean ready(MinecraftServer server, RocketTransferSavedData journal, RocketTransferRecord record,
                          RocketTransferBayDispatch bays) {
        var holds = EscrowRuntime.of(server).executorHolds();
        if (!marginsAdmissible(record)) {
            holds.hold(server, record, io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.EscrowGovernedExecutorHolds.Reason.DISCOVERY_MARGIN);
            return false;
        }
        boolean loaded = RocketTransferEntities.recoveryEndsAlreadyLoaded(server, record);
        if (holds.held(record)) { return loaded; }
        var wait = !loaded ? RocketGovernedWaits.Wait.LOADING
                : censusAllows(server, journal, record, bays) ? RocketGovernedWaits.Wait.NONE : RocketGovernedWaits.Wait.CENSUS;
        waits.observe(record, server.getTickCount(), wait, () -> "source=" + RocketTransferEntities.marginStatus(server, record.sourceSnapshot())
                + " destination=" + RocketTransferEntities.marginStatus(server, record.destinationSnapshot()));
        return loaded;
    }

    static boolean marginsAdmissible(RocketTransferRecord record) {
        return RocketDiscoveryColumns.of(record.sourceSnapshot()).admissible()
                && RocketDiscoveryColumns.of(record.destinationSnapshot()).admissible();
    }

    /** The wait log's census, with the participants that the executors pass; read-only like the boundary itself. */
    private static boolean censusAllows(MinecraftServer server, RocketTransferSavedData journal, RocketTransferRecord record,
                                        RocketTransferBayDispatch bays) {
        var sourceLevel = RocketTransferEntities.level(server, record.sourceSnapshot().sourceDimension());
        var destinationLevel = RocketTransferEntities.level(server, record.destinationSnapshot().sourceDimension());
        if (sourceLevel == null || destinationLevel == null) { return false; }
        var sourceEntity = sourceLevel.getEntity(record.sourceEntityId());
        var arrivalEntity = record.phase() == RocketTransferPhase.PREPARED ? null
                : destinationLevel.getEntity(record.nativeTransfer().orElseThrow().armed().destination());
        if (sourceEntity != null && !(sourceEntity instanceof RocketEntity)
                || arrivalEntity != null && !(arrivalEntity instanceof RocketEntity)) { return false; }
        var source = sourceEntity instanceof RocketEntity rocket && !rocket.isRemoved() ? rocket : null;
        return arrivalEntity instanceof RocketEntity arrival
                ? RocketGovernedLaunchBoundary.allows(server, journal, record, source, arrival, bays)
                : RocketGovernedLaunchBoundary.allows(server, journal, record, source, bays);
    }

    /**
     * Inspect (R21 item 6), read-only: both recorded margins, the active hold or the current wait, and the size of the
     * set refreshed at the last visit (0 while held or not ACTIVE).
     */
    io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketTransferInspection.Readiness readiness(
            MinecraftServer server, RocketTransferRecord record) {
        var hold = EscrowRuntime.of(server).executorHolds().reason(record.transferId());
        return new io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketTransferInspection.Readiness(
                RocketTransferEntities.marginStatus(server, record.sourceSnapshot()),
                RocketTransferEntities.marginStatus(server, record.destinationSnapshot()),
                hold.map(reason -> "HOLD:" + reason).orElseGet(() -> waits.wait(record.transferId()).name()),
                waits.tickets(record.transferId()));
    }

    private static void dispatchLoaded(MinecraftServer server, RocketTransferSavedData journal, RocketTransferRecord record,
                                       RocketTransferBayDispatch bays) {
        var level = RocketTransferEntities.level(server, record.sourceSnapshot().sourceDimension());
        if (level == null) { return; }
        var entity = level.getEntity(record.sourceEntityId());
        if (entity != null && !(entity instanceof RocketEntity)) { endpointHold(server, record); return; }
        var source = (RocketEntity) entity;
        if (source != null && !source.operational()) { endpointHold(server, record); return; }
        if (source == null && record.phase().ordinal() < RocketTransferPhase.SOURCE_REMOVED.ordinal()) { endpointHold(server, record); return; }
        // At-rest passenger waits must precede destination/phase/return envelope captures.
        // This negative hint performs no action; all successful routes still repeat their final checks.
        if (passengersPending(server, record, source)) { return; }
        var returns = EscrowRuntime.of(server).returns();
        if (returns.waitingForPassengers(server, record, source)) { return; }
        if (returns.handles(record, source)) {
            returns.tickThroughTerminal(server, journal, record, source,
                    () -> RocketGovernedLaunchBoundary.allows(server, journal, record, source, bays));
            return;
        }
        if (record.phase() != RocketTransferPhase.PREPARED) {
            destination(server, journal, record, source, bays); return;
        }
        if (source == null) { return; }
        var previous = source.flightData().orElseThrow().state();
        var result = EscrowRuntime.of(server).sourceFlights().tick(server, journal, record, source,
                () -> RocketGovernedLaunchBoundary.allows(server, journal, record, source, bays));
        if (result == EscrowGovernedSourceFlight.Result.HELD) { return; }
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
    }

    private static boolean passengersPending(MinecraftServer server, RocketTransferRecord record, RocketEntity source) {
        if (source != null || record.phase() != RocketTransferPhase.COMMITTED) { return false; }
        var level = RocketTransferEntities.level(server, record.destinationSnapshot().sourceDimension());
        var entity = level == null ? null : level.getEntity(record.nativeTransfer().orElseThrow().armed().destination());
        return entity instanceof RocketEntity arrival
                && arrival.flightData().filter(flight -> flight.state() == RocketFlightState.LANDED).isPresent()
                && EscrowGovernedTerminalPassengers.hasOffline(server, record);
    }

    private static void destination(MinecraftServer server, RocketTransferSavedData journal, RocketTransferRecord record,
                                    RocketEntity source, RocketTransferBayDispatch bays) {
        if (source != null && (record.phase() == RocketTransferPhase.PREPARED || record.phase() == RocketTransferPhase.DESTINATION_SPAWNED)
                && RocketGovernedLaunchBoundary.allows(server, journal, record, source, bays) && !landingAvailable(server, record)) {
            requestReturn(server, journal, record, source, bays, RocketTransferReturnReason.DESTINATION_PAD_BLOCKED, () -> { });
            return;
        }
        // Restore a warm COMMITTED descent's planned pose before F4/F5 inspect it. This is the
        // same checked arrival movement, never landing, a passenger move or a new phase authority.
        if (source == null && record.phase() == RocketTransferPhase.COMMITTED) {
            var level = RocketTransferEntities.level(server, record.destinationSnapshot().sourceDimension());
            if (level != null && level.getEntity(record.nativeTransfer().orElseThrow().armed().destination()) instanceof RocketEntity arrival) {
                EscrowRuntime.of(server).arrivals().tick(server, journal, record, arrival,
                        () -> RocketGovernedLaunchBoundary.allows(server, journal, record, null, arrival, bays));
            }
        }
        var result = EscrowRuntime.of(server).destinations().tick(server, journal, record, source,
                (checked, from, to) -> RocketGovernedLaunchBoundary.allows(server, journal, checked, from, to, bays)
                        && !EscrowRuntime.of(server).returns().handles(checked, from)
                        && EscrowRuntime.of(server).flightPhases().allowsNative(from, to)
                        && (to != null || landingAvailable(server, checked)));
        if (result == io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.EscrowGovernedDestination.Result.CREATE_FAILED
                || result == io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.EscrowGovernedDestination.Result.SPAWN_FAILED) {
            requestReturn(server, journal, record, source, bays,
                    result == io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.EscrowGovernedDestination.Result.CREATE_FAILED
                            ? RocketTransferReturnReason.DESTINATION_ENTITY_CREATE_FAILED : RocketTransferReturnReason.DESTINATION_ENTITY_SPAWN_FAILED,
                    () -> { });
            return;
        }
        if (result == io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.EscrowGovernedDestination.Result.PRESENT) {
            var level = RocketTransferEntities.level(server, record.destinationSnapshot().sourceDimension());
            var arrival = (RocketEntity) level.getEntity(record.nativeTransfer().orElseThrow().armed().destination());
            var phase = EscrowRuntime.of(server).flightPhases().tick(server, journal, record, source, arrival,
                    () -> RocketGovernedLaunchBoundary.allows(server, journal, record,
                            source != null && source.isRemoved() ? null : source, arrival, bays)
                            && EscrowRuntime.of(server).flightPhases().allowsNative(source, arrival));
            if (phase == io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.EscrowGovernedPhases.Result.COMMITTED
                    && source == null) {
                RocketGovernedArrivalDispatcher.tick(server, journal, record, arrival, bays);
            }
        }
    }

    static boolean requestReturn(MinecraftServer server, RocketTransferSavedData journal, RocketTransferRecord record,
                                 RocketEntity source, RocketTransferBayDispatch bays, RocketTransferReturnReason reason,
                                 Runnable checkpointed) {
        return EscrowRuntime.of(server).returns().request(server, journal, record, source,
                () -> RocketGovernedLaunchBoundary.allows(server, journal, record, source, bays), () -> {
                    RocketFlightFeedback.returnedToSource(source);
                    io.github.sunthemoon.advancedrocketrycommunity.AdvancedRocketryCommunity.LOGGER.info(
                            "ARCE_GOVERNED_RETURN_CHECKPOINT transfer={} reason={}", record.transferId(), reason);
                    checkpointed.run();
                });
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

    /** Loading may bootstrap tickets. A loaded executor decides its hold before any refresh. */
    static boolean afterClassification(EscrowGovernedDispatch.Decision decision, java.util.function.BooleanSupplier held,
                                       Runnable refreshTickets, java.util.function.BooleanSupplier ready, Runnable execute) {
        if (decision.route() != EscrowGovernedDispatch.Route.ACTIVE) { return false; }
        boolean loaded = ready.getAsBoolean();
        if (loaded) { execute.run(); }
        if (!held.getAsBoolean()) { refreshTickets.run(); }
        return loaded;
    }

    private static void endpointHold(MinecraftServer server, RocketTransferRecord record) {
        EscrowRuntime.of(server).executorHolds().hold(server, record,
                io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.EscrowGovernedExecutorHolds.Reason.NATIVE_ENDPOINT);
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

    void clear() { cursor = 0; waits.clear(); }
}
