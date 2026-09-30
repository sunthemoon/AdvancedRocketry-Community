package io.github.sunthemoon.advancedrocketrycommunity.rocket.server;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketTransferRecord;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketPosition;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** WARP review R7: a record whose ends cannot be loaded does not starve the records behind it. */
final class RocketTransferRecoverySelectionTest {
    @Test
    void recoveryMovesPastARecordWhoseEndsAreNotLoaded() {
        RocketTransferRecord stuck = RocketStationMotionRuleTest.record(new RocketPosition(72, 80, 8));
        RocketTransferRecord ready = RocketStationMotionRuleTest.record(new RocketPosition(92, 80, 8));
        List<RocketTransferRecord> entries = List.of(stuck, ready);
        assertEquals(Optional.of(ready), RocketTransferRecoveryService.nextRecoverable(entries, Set.of(), Set.of(),
                record -> record == ready), "The loaded record behind a stuck one was not chosen");
        assertEquals(Optional.of(stuck), RocketTransferRecoveryService.nextRecoverable(entries, Set.of(), Set.of(),
                record -> true), "Journal order must still decide among loaded records");
        assertTrue(RocketTransferRecoveryService.nextRecoverable(entries, Set.of(), Set.of(), record -> false)
                .isEmpty(), "Nothing is recovered while no record's ends are loaded");
    }

    @Test
    void liveAndSettledRecordsAreNeverSelected() {
        RocketTransferRecord live = RocketStationMotionRuleTest.record(new RocketPosition(72, 80, 8));
        RocketTransferRecord settled = RocketStationMotionRuleTest.record(new RocketPosition(92, 80, 8));
        RocketTransferRecord open = RocketStationMotionRuleTest.record(new RocketPosition(112, 80, 8));
        Set<UUID> liveIds = Set.of(live.transferId());
        Set<UUID> settledIds = Set.of(settled.transferId());
        assertEquals(Optional.of(open), RocketTransferRecoveryService.nextRecoverable(List.of(live, settled, open),
                liveIds, settledIds, record -> true));
    }
}
