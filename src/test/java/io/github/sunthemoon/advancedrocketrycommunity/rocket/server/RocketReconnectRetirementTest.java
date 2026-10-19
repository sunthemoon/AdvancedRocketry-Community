package io.github.sunthemoon.advancedrocketrycommunity.rocket.server;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketTransferRecord;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketPosition;
import org.junit.jupiter.api.Test;

/** W96-R2: the last-reconnect cleanup retires only recovery's settled, non-live, stationary source at its origin. */
final class RocketReconnectRetirementTest {
    private static final RocketTransferRecord RECORD = RocketStationMotionRuleTest.record(new RocketPosition(72, 80, 8));

    @Test
    void onlyASettledNonLiveStationarySourceAtItsOriginMayBeRetired() {
        var stationary = RocketTransferEntities.stationarySource(RECORD, 500L);
        assertTrue(RocketTransferRecoveryService.recoveredStationarySource(true, false, stationary, true));
        assertFalse(RocketTransferRecoveryService.recoveredStationarySource(true, true, stationary, true),
                "A live record is a flight, not a recovered source");
        assertFalse(RocketTransferRecoveryService.recoveredStationarySource(false, false, stationary, true),
                "An unclassified record has not been through recovery");
        assertFalse(RocketTransferRecoveryService.recoveredStationarySource(true, false, stationary, false),
                "A source away from its snapshot origin is not the recovered authority");
        assertFalse(RocketTransferRecoveryService.recoveredStationarySource(true, false, null, true));
    }

    @Test
    void everySourcePhaseOfAFlightIsRefused() {
        var transit = RECORD.sourceFlightData();
        assertFalse(RocketTransferRecoveryService.recoveredStationarySource(true, false, transit, true), "TRANSIT");
        var countdown = transit.markFailed(1L).recover(true, 2L).withPlan(transit.plan().orElseThrow()).startCountdown(3L);
        assertFalse(RocketTransferRecoveryService.recoveredStationarySource(true, false, countdown, true), "COUNTDOWN");
        var ascent = countdown.completeCountdown(4L);
        assertFalse(RocketTransferRecoveryService.recoveredStationarySource(true, false, ascent, true), "ASCENT");
        var planned = transit.markFailed(1L).recover(true, 2L).withPlan(transit.plan().orElseThrow());
        assertFalse(RocketTransferRecoveryService.recoveredStationarySource(true, false, planned, true),
                "A FUELED source that still holds a plan is not stationary authority");
        var landed = RECORD.destinationFlightData().land(5L);
        assertFalse(RocketTransferRecoveryService.recoveredStationarySource(true, false, landed, true), "LANDED");
    }
}
