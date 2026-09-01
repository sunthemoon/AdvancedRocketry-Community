package io.github.sunthemoon.advancedrocketrycommunity.progression;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.github.sunthemoon.advancedrocketrycommunity.satellite.model.SatelliteLimits;
import java.util.UUID;
import org.junit.jupiter.api.Test;

final class ResearchAccountTest {
    @Test
    void missionYieldAndDiscoveryCostShareOneBoundedMutation() {
        ResearchAccount account = ResearchAccount.empty(UUID.randomUUID())
                .creditAndSpend(120, 100);

        assertEquals(20, account.balance());
        assertEquals(120L, account.lifetimeEarned());
        assertEquals(100L, account.lifetimeSpent());
    }

    @Test
    void insufficientOrOverflowingMutationsFailClosed() {
        ResearchAccount account = ResearchAccount.empty(UUID.randomUUID());
        assertThrows(IllegalArgumentException.class, () -> account.creditAndSpend(10, 11));

        ResearchAccount full = new ResearchAccount(
                SatelliteLimits.RESEARCH_ACCOUNT_SCHEMA_VERSION,
                UUID.randomUUID(),
                SatelliteLimits.MAX_RESEARCH_BALANCE,
                SatelliteLimits.MAX_RESEARCH_BALANCE,
                0L
        );
        assertThrows(IllegalStateException.class, () -> full.creditAndSpend(1, 0));
    }
}
