package io.github.sunthemoon.advancedrocketrycommunity.rocket.server;

import io.github.sunthemoon.advancedrocketrycommunity.progression.classic.ClassicAdvancementEvents;
import io.github.sunthemoon.advancedrocketrycommunity.progression.classic.firstevent.ClassicFirstEventDelivery;
import io.github.sunthemoon.advancedrocketrycommunity.progression.classic.firstevent.FirstEventDispositionSource;
import io.github.sunthemoon.advancedrocketrycommunity.progression.classic.firstevent.FirstEventKind;
import io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.EscrowGovernedArrival;
import io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.EscrowRuntime;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.entity.RocketEntity;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketTransferRecord;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.persistence.RocketTransferSavedData;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;

/** Landing retains the world-first acknowledgement boundary and never runs legacy release/cleanup. */
final class RocketGovernedArrivalDispatcher {
    private RocketGovernedArrivalDispatcher() { }

    /** Stack-local result of the pre-F4/F5 computation. Final actions still acquire their own authority. */
    record Visit(RocketTransferRecord record, RocketEntity arrival, long observation, int tick, long levelTime,
                 Object binding, EscrowGovernedArrival.Result result) { }

    static EscrowGovernedArrival.Result result(EscrowGovernedArrival.Result first, boolean sameRecord,
                                               boolean sameArrival, boolean sameTick, boolean sameObservation,
                                               boolean sameBinding,
                                               java.util.function.Supplier<EscrowGovernedArrival.Result> compute) {
        return first != null && first != EscrowGovernedArrival.Result.HELD && sameRecord && sameArrival
                && sameTick && sameObservation && sameBinding ? first : compute.get();
    }

    static void tick(MinecraftServer server, RocketTransferSavedData journal, RocketTransferRecord record,
                     RocketEntity arrival, RocketTransferBayDispatch bays, Visit first,
                     java.util.function.Supplier<EscrowGovernedArrival.Result> compute) {
        var result = result(first == null ? null : first.result(), first != null && first.record() == record,
                first != null && first.arrival() == arrival,
                first != null && first.tick() == server.getTickCount() && first.levelTime() == arrival.level().getGameTime(),
                first != null && first.observation() == arrival.governedObservation(),
                first != null && first.binding() != null
                        && first.binding() == EscrowRuntime.of(server).arrivals().reuseBinding(record, arrival), compute);
        if (result == EscrowGovernedArrival.Result.DESCENDING) {
            RocketFlightFeedback.descentTrail(arrival, Math.max(0, arrival.level().getGameTime()
                    - arrival.flightData().orElseThrow().stateStartedGameTime()));
        } else if (result == EscrowGovernedArrival.Result.READY_TO_LAND) {
            var completion = MoonLandingCompletion.complete(server, journal, record, arrival, bays,
                    () -> land(server, journal, record, arrival, bays), FirstEventDispositionSource.WORLD);
            if (completion == MoonLandingCompletion.Result.ORDINARY) { land(server, journal, record, arrival, bays); }
        } else if (result == EscrowGovernedArrival.Result.LANDED) {
            EscrowRuntime.of(server).terminal().tick(server, journal, record, arrival,
                    io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.EscrowFlightTables.Outcome.HANDED_OFF,
                    () -> RocketGovernedLaunchBoundary.allows(server, journal, record, null, arrival, bays));
        }
    }

    private static boolean land(MinecraftServer server, RocketTransferSavedData journal, RocketTransferRecord record,
                                RocketEntity arrival, RocketTransferBayDispatch bays) {
        if (!EscrowRuntime.of(server).arrivals().land(server, journal, record, arrival,
                () -> RocketGovernedLaunchBoundary.allows(server, journal, record, null, arrival, bays))) { return false; }
        RocketFlightFeedback.landed(arrival);
        RocketTransferAudit.auditPhase(record, "landing_complete", arrival.getUUID());
        ClassicAdvancementEvents.rocketLanded((ServerLevel) arrival.level(), RocketTransferEntities.aboard(arrival));
        ClassicFirstEventDelivery.sourceChanged(server, FirstEventKind.FIRST_MOON_LANDING);
        return true;
    }
}
