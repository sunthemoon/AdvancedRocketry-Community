package io.github.sunthemoon.advancedrocketrycommunity.satellite.mission;

import io.github.sunthemoon.advancedrocketrycommunity.progression.ResearchAccount;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.model.SatelliteLimits;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.model.SatelliteState;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Preflight every missing owner before mutating any restored account or reservation. */
final class AccountRecovery {
    private AccountRecovery() {
    }

    static int restore(Collection<SatelliteState> satellites, Map<UUID, ResearchAccount> accounts,
                       StorageBudget budget) {
        Set<UUID> missing = new LinkedHashSet<>();
        for (SatelliteState satellite : satellites) {
            if (!accounts.containsKey(satellite.ownerId())) {
                missing.add(satellite.ownerId());
            }
        }
        if (missing.isEmpty()) {
            return 0;
        }
        if (missing.size() > SatelliteLimits.MAX_RESEARCH_ACCOUNTS - accounts.size()) {
            throw new IllegalArgumentException("Recovered research accounts exceed their fixed bound");
        }
        int bytes = Math.multiplyExact(missing.size(), RecordSizer.ACCOUNT_BYTES);
        if (!budget.fits(StorageBudget.Section.ACCOUNTS, bytes)) {
            throw new IllegalArgumentException("Recovered research accounts exceed their storage budget");
        }
        for (UUID owner : missing) {
            accounts.put(owner, ResearchAccount.empty(owner));
            budget.reserve(StorageBudget.Section.ACCOUNTS, owner, RecordSizer.ACCOUNT_BYTES);
        }
        return missing.size();
    }
}
