package io.github.sunthemoon.advancedrocketrycommunity.satellite.mission;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import io.github.sunthemoon.advancedrocketrycommunity.progression.ResearchAccount;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.model.SatelliteLimits;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.model.SatelliteState;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

final class AccountRecoveryTest {
    @Test
    void failedRecoveryDoesNotPartiallyAddAccountsOrReservations() {
        SatelliteMissionRegistry registry = SatelliteMissionRegistry.restore(0L, 0L);
        for (int index = 0; index < SatelliteLimits.MAX_RESEARCH_ACCOUNTS - 1; index++) {
            registry.restoreAccount(ResearchAccount.empty(new UUID(1L, index)));
        }
        registry.restoreSatellite(satellite(0, new UUID(2L, 0L)));
        registry.restoreSatellite(satellite(1, new UUID(2L, 1L)));
        List<ResearchAccount> before = registry.accounts();
        long reserved = registry.reservedBytes("ACCOUNTS");
        assertThrows(IllegalArgumentException.class, registry::finishRestore);
        assertEquals(before, registry.accounts());
        assertEquals(reserved, registry.reservedBytes("ACCOUNTS"));
        assertEquals(ResearchAccount.empty(new UUID(2L, 0L)), registry.account(new UUID(2L, 0L)));
        assertThrows(IllegalArgumentException.class, registry::finishRestore);
        assertEquals(before, registry.accounts());
        assertEquals(reserved, registry.reservedBytes("ACCOUNTS"));
    }

    @Test
    void sectionBytePreflightRejectsTheEntireAddition() {
        Map<UUID, ResearchAccount> accounts = new LinkedHashMap<>();
        StorageBudget budget = new StorageBudget();
        // Isolate section arithmetic; normal 4,096 x 128 account reservations fit below this byte cap.
        budget.reserve(StorageBudget.Section.ACCOUNTS, new UUID(1L, 0L),
                (int) StorageBudget.Section.ACCOUNTS.bytes - RecordSizer.ACCOUNT_BYTES);
        long before = budget.reserved(StorageBudget.Section.ACCOUNTS);
        assertThrows(IllegalArgumentException.class, () -> AccountRecovery.restore(
                List.of(satellite(0, new UUID(2L, 0L)), satellite(1, new UUID(2L, 1L))), accounts, budget));
        assertTrue(accounts.isEmpty());
        assertEquals(before, budget.reserved(StorageBudget.Section.ACCOUNTS));
    }

    @Test
    void exactSectionByteCapacityCountsSharedOwnersOnceAndRepeatedRestoreChangesNothing() {
        Map<UUID, ResearchAccount> accounts = new LinkedHashMap<>();
        StorageBudget budget = new StorageBudget();
        budget.reserve(StorageBudget.Section.ACCOUNTS, new UUID(1L, 0L),
                (int) StorageBudget.Section.ACCOUNTS.bytes - RecordSizer.ACCOUNT_BYTES);
        UUID owner = new UUID(2L, 0L);
        List<SatelliteState> satellites = List.of(satellite(0, owner), satellite(1, owner));
        assertEquals(1, AccountRecovery.restore(satellites, accounts, budget));
        assertEquals(Map.of(owner, ResearchAccount.empty(owner)), accounts);
        assertEquals(StorageBudget.Section.ACCOUNTS.bytes, budget.reserved(StorageBudget.Section.ACCOUNTS));
        assertEquals(0, AccountRecovery.restore(satellites, accounts, budget));
        assertEquals(StorageBudget.Section.ACCOUNTS.bytes, budget.reserved(StorageBudget.Section.ACCOUNTS));
    }

    private static SatelliteState satellite(int id, UUID owner) {
        return SatelliteState.launch(new UUID(3L, id), ModIdentity.id("data_satellite"), owner, 0L);
    }
}
