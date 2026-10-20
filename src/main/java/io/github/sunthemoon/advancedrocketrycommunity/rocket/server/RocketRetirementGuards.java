package io.github.sunthemoon.advancedrocketrycommunity.rocket.server;

import io.github.sunthemoon.advancedrocketrycommunity.rocket.entity.RocketEntity;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFlightData;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFlightState;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketTransferPhase;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketTransferRecord;

/** W116 (P1-N3 leaf N3-A2): the ordinary source retirement's static guards over live rockets, apart from its coordinator. */
final class RocketRetirementGuards {
    private RocketRetirementGuards() { }

    /**
     * W98-R6 (R34's R3 reason): a PREPARED record's recovered source never left its pad when its loaded image, read
     * before recovery changes it, is COUNTDOWN or stationary at its snapshot origin. Every crew save was then taken at
     * the pad, so an offline seat need not hold the return, as for a countdown cancellation.
     */
    static boolean neverLeftPad(RocketTransferRecord record, RocketEntity image) {
        var origin = record.sourceSnapshot().sourceOrigin();
        return neverLeftPad(record.phase(), image.flightData().orElse(null),
                image.getX() == origin.x() + 0.5D && image.getY() == origin.y() && image.getZ() == origin.z() + 0.5D);
    }

    static boolean neverLeftPad(RocketTransferPhase phase, RocketFlightData image, boolean atOrigin) {
        return phase == RocketTransferPhase.PREPARED && image != null && (image.state() == RocketFlightState.COUNTDOWN
                || RocketTransferRecoveryService.recoveredStationarySource(true, false, image, atOrigin));
    }
}
