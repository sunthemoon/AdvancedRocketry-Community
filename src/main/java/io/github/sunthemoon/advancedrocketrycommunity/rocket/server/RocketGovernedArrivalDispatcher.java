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

    static void tick(MinecraftServer server, RocketTransferSavedData journal, RocketTransferRecord record,
                     RocketEntity arrival, RocketTransferBayDispatch bays) {
        var result = EscrowRuntime.of(server).arrivals().tick(server, journal, record, arrival,
                () -> RocketGovernedLaunchBoundary.allows(server, journal, record, null, arrival, bays));
        if (result == EscrowGovernedArrival.Result.DESCENDING) {
            RocketFlightFeedback.descentTrail(arrival, Math.max(0, arrival.level().getGameTime()
                    - arrival.flightData().orElseThrow().stateStartedGameTime()));
        } else if (result == EscrowGovernedArrival.Result.READY_TO_LAND) {
            var completion = MoonLandingCompletion.complete(server, journal, record, arrival, bays,
                    () -> land(server, journal, record, arrival, bays), FirstEventDispositionSource.WORLD);
            if (completion == MoonLandingCompletion.Result.ORDINARY) { land(server, journal, record, arrival, bays); }
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
