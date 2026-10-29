package io.github.sunthemoon.advancedrocketrycommunity.rocket.server;

import io.github.sunthemoon.advancedrocketrycommunity.rocket.entity.RocketEntity;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFlightData;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketPosition;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.transaction.RocketRegion;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.transfer.NamedLandingPadStaging;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.transfer.RocketLandingPadSelection;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.transfer.RocketLandingPadSelector;
import io.github.sunthemoon.advancedrocketrycommunity.station.persistence.StationRegistrySavedData;
import java.util.List;
import net.minecraft.server.level.ServerLevel;

/** One selection entry shared by a fresh launch and bounded terrain preparation; reservations are always supplied afresh. */
final class RocketLaunchPadSelection {
    static final String INVALID_DESTINATION = "invalid_destination";
    private final RocketLandingPadSelector pads;
    private final NamedLandingPadStaging named;

    RocketLaunchPadSelection(RocketLandingPadSelector pads, NamedLandingPadStaging named) {
        this.pads = pads; this.named = named;
    }

    RocketLandingPadSelection select(RocketEntity rocket, RocketFlightData countdown, ServerLevel target,
            List<RocketRegion> reservations, boolean governed, RocketPosition kept, boolean loadTerrain, int cursor) {
        var plan = countdown.plan().orElseThrow(); var source = rocket.snapshot().orElseThrow();
        long arrival = RocketTransferService.scheduledArrival(countdown);
        if (plan.destinationStation().isPresent()) {
            var station = StationRegistrySavedData.get(target.getServer()).find(plan.destinationStation().orElseThrow()).orElse(null);
            if (station == null) { return RocketLandingPadSelection.failure(0, 0, INVALID_DESTINATION); }
            return plan.landingPad() != null ? named.select(target, source, plan.requestId(), station, plan.landingPad(),
                    reservations, arrival, governed) : pads.selectStation(target, source, plan.requestId(), station,
                    reservations, arrival, governed, loadTerrain);
        }
        return pads.select(target, source, plan.requestId(), reservations, arrival, governed, kept, loadTerrain, cursor);
    }
}
