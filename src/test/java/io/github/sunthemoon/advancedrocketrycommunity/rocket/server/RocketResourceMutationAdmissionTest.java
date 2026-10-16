package io.github.sunthemoon.advancedrocketrycommunity.rocket.server;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketTransferPhase;
import org.junit.jupiter.api.Test;

class RocketResourceMutationAdmissionTest {
    @org.junit.jupiter.api.BeforeAll static void bootstrap() {
        io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap.initialize();
    }

    @Test
    void c2NamedCommittedRecordWaitsForCurrentSessionSettlementAndNoLiveTransfer() {
        var record = RocketStationMotionRuleTest.record(new io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketPosition(80, 80, 80))
                .destinationSpawned(java.util.UUID.randomUUID()).advance(RocketTransferPhase.PASSENGERS_TRANSFERRED)
                .advance(RocketTransferPhase.SOURCE_REMOVED).advance(RocketTransferPhase.COMMITTED);
        assertFalse(RocketTransferService.reconciliationRecordAllowed(record, false, false, true, false));
        assertFalse(RocketTransferService.reconciliationRecordAllowed(record, true, true, true, false));
        assertFalse(RocketTransferService.reconciliationRecordAllowed(record, true, false, false, false));
        assertTrue(RocketTransferService.reconciliationRecordAllowed(record, true, false, true, false));
    }
    @Test
    void passengerWaitingSourceRemainsLockedEvenWhenTransientlyClassifiedSettled() {
        for (var phase : RocketTransferPhase.values()) {
            if (phase != RocketTransferPhase.COMMITTED) {
                assertFalse(RocketTransferService.resourceMutationAllowed(true, phase, true, true), phase.name());
            }
        }
    }

    @Test
    void blockedUnclassifiedOrNonAuthoritativeArrivalCannotMutate() {
        assertFalse(RocketTransferService.resourceMutationAllowed(false, null, false, false));
        assertFalse(RocketTransferService.resourceMutationAllowed(false, RocketTransferPhase.COMMITTED, true, true));
        assertFalse(RocketTransferService.resourceMutationAllowed(true, RocketTransferPhase.COMMITTED, false, true));
        assertFalse(RocketTransferService.resourceMutationAllowed(true, RocketTransferPhase.COMMITTED, true, false));
        assertTrue(RocketTransferService.resourceMutationAllowed(true, null, false, false));
        assertTrue(RocketTransferService.resourceMutationAllowed(true, RocketTransferPhase.COMMITTED, true, true));
    }
}
