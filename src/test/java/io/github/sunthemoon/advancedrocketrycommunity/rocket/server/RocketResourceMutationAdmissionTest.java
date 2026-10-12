package io.github.sunthemoon.advancedrocketrycommunity.rocket.server;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketTransferPhase;
import org.junit.jupiter.api.Test;

class RocketResourceMutationAdmissionTest {
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
