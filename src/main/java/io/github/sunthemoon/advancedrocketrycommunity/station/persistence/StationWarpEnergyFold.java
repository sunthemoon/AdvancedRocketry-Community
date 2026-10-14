package io.github.sunthemoon.advancedrocketrycommunity.station.persistence;

import io.github.sunthemoon.advancedrocketrycommunity.AdvancedRocketryCommunity;
import io.github.sunthemoon.advancedrocketrycommunity.station.model.StationLimits;
import io.github.sunthemoon.advancedrocketrycommunity.station.model.StationRegistryModel;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import java.util.function.IntSupplier;
import java.util.function.LongSupplier;

/**
 * The balance loop of one warp credit fold (ADR-044 section 2, WARP review R4), moved out of the registry adapter so
 * that it stays within its size bound (W44 section 10). The adapter validates the credits, owns the dirty flag and
 * refuses the fold while it is not readable.
 */
final class StationWarpEnergyFold {
    private StationWarpEnergyFold() {
    }

    /** Credits the validated {@code credits} in UUID order and returns the energy credited. */
    static long apply(StationRegistryModel registry, StationStorageBudget budget, Map<UUID, Integer> credits,
                      IntSupplier records, LongSupplier encodedBytes, long offered) {
        long credited = 0L;
        // After one refusal the rest of this fold is refused too, so a full registry is encoded once.
        boolean entryRoom = true;
        for (Map.Entry<UUID, Integer> credit : new TreeMap<>(credits).entrySet()) {
            boolean newEntry = registry.needsWarpEnergyEntry(credit.getKey());
            if (newEntry) {
                entryRoom = entryRoom && budget.admitsBalanceEntry(records.getAsInt(),
                        registry.warpEnergyBalances().size(), encodedBytes);
                if (!entryRoom) {
                    continue;
                }
            }
            int accepted = registry.creditWarpEnergy(credit.getKey(), credit.getValue());
            credited += accepted;
            if (newEntry && accepted > 0) {
                budget.grew(StationLimits.MAX_WARP_ENERGY_ENTRY_NBT_BYTES);
            }
        }
        if (credited < offered) {
            AdvancedRocketryCommunity.LOGGER.warn(
                    "ARCE_STATION_WARP_CREDIT_REFUSED offered={} credited={} entry_room={}",
                    offered, credited, entryRoom);
        }
        return credited;
    }
}
