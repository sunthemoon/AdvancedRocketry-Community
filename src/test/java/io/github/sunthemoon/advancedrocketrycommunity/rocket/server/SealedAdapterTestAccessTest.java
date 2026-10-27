package io.github.sunthemoon.advancedrocketrycommunity.rocket.server;

import static org.junit.jupiter.api.Assertions.*;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.RocketLimits;
import org.junit.jupiter.api.Test;

class SealedAdapterTestAccessTest {
    @Test void completionBudgetIncludesEachNativeProofAndOneRetry() {
        int one = Math.toIntExact(RocketDisassemblyConfirmations.LIFETIME_TICKS)
                + 3 * (2 * RocketOrdinaryRetirement.ATTEMPT_TICKS + RocketOrdinaryRetirement.RETRY_TICKS) + 1;
        assertEquals(one, SealedAdapterTestAccess.completionTicks(4, 1, false));
        assertEquals(2 * one + 2 * RocketOrdinaryRetirement.RETRY_TICKS,
                SealedAdapterTestAccess.completionTicks(4, 2, true));
        assertEquals(RocketOrdinaryRetirement.RETRY_TICKS, SealedAdapterTestAccess.retryTicks());
    }
    @Test void rejectsUnboundedFixtureRequests() {
        assertThrows(IllegalArgumentException.class, () -> SealedAdapterTestAccess.completionTicks(0, 1, false));
        assertThrows(IllegalArgumentException.class, () -> SealedAdapterTestAccess.completionTicks(RocketLimits.MAX_BLOCKS + 1, 1, false));
        assertThrows(IllegalArgumentException.class, () -> SealedAdapterTestAccess.completionTicks(4, 3, false));
    }
}
