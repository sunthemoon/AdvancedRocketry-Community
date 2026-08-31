package io.github.sunthemoon.advancedrocketrycommunity.atmosphere.life;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.sunthemoon.advancedrocketrycommunity.atmosphere.AtmosphereLimits;
import org.junit.jupiter.api.Test;

class OxygenTransferTest {
    @Test
    void wholeCanisterTransferIsAtomic() {
        OxygenTransferResult accepted = OxygenTransfer.fillOneCanister(
                500,
                AtmosphereLimits.SUIT_OXYGEN_CAPACITY
        );
        OxygenTransferResult rejected = OxygenTransfer.fillOneCanister(
                1_001,
                AtmosphereLimits.SUIT_OXYGEN_CAPACITY
        );

        assertTrue(accepted.accepted());
        assertEquals(1_500, accepted.oxygenUnits());
        assertEquals(1, accepted.canistersConsumed());
        assertFalse(rejected.accepted());
        assertEquals(1_001, rejected.oxygenUnits());
        assertEquals(0, rejected.canistersConsumed());
    }

    @Test
    void invalidCapacityOrStoredAmountIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> OxygenTransfer.fillOneCanister(-1, 2_000));
        assertThrows(IllegalArgumentException.class, () -> OxygenTransfer.fillOneCanister(0, 0));
        assertThrows(IllegalArgumentException.class, () -> OxygenTransfer.fillOneCanister(2_001, 2_000));
    }
}
