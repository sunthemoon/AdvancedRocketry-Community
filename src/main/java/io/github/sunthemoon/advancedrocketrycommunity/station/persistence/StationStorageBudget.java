package io.github.sunthemoon.advancedrocketrycommunity.station.persistence;

import io.github.sunthemoon.advancedrocketrycommunity.station.model.StationLimits;
import java.util.function.LongSupplier;

/**
 * Growth admission for the station registry's fixed NBT bound (review F2, ADR-044 §2). Every growth
 * other than a warp balance stops at the bound minus the balance headroom. The headroom holds one
 * entry of at most 64 bytes for each of the 4,096 stations, so it belongs to balances: a new balance
 * entry is admitted up to the bound itself (WARP review R4), and ordinary saves never fail on size.
 *
 * <p>A running upper bound avoids encoding; the registry is encoded only when that bound would cross
 * a limit. It grows only after a mutation succeeded (WARP review R13).
 */
final class StationStorageBudget {
    static final long GROWTH_LIMIT =
            (long) StationLimits.MAX_REGISTRY_NBT_BYTES - StationLimits.WARP_ENERGY_HEADROOM_NBT_BYTES;
    private static final long ROOT_BYTES = 1_024L;

    private long encodedUpperBound = -1L;

    /** Growth other than a balance entry: stations, reservations, team entries and longer orbit IDs. */
    boolean admitsGrowth(int records, int balances, LongSupplier encodedBytes, long growthBytes) {
        return fits(records, balances, encodedBytes, growthBytes, GROWTH_LIMIT);
    }

    /** One new warp balance entry, admitted inside the headroom up to the bound itself. */
    boolean admitsBalanceEntry(int records, int balances, LongSupplier encodedBytes) {
        return fits(records, balances, encodedBytes, StationLimits.MAX_WARP_ENERGY_ENTRY_NBT_BYTES,
                StationLimits.MAX_REGISTRY_NBT_BYTES);
    }

    /** Records growth that has happened. */
    void grew(long growthBytes) {
        if (encodedUpperBound >= 0L) {
            encodedUpperBound += growthBytes;
        }
    }

    private boolean fits(int records, int balances, LongSupplier encodedBytes, long growthBytes, long limit) {
        long recordBound = ROOT_BYTES + (long) records * StationLimits.MAX_STATION_RECORD_NBT_BYTES
                + (long) balances * StationLimits.MAX_WARP_ENERGY_ENTRY_NBT_BYTES;
        if (recordBound + growthBytes <= limit
                || (encodedUpperBound >= 0L && encodedUpperBound + growthBytes <= limit)) {
            return true;
        }
        encodedUpperBound = encodedBytes.getAsLong();
        return encodedUpperBound + growthBytes <= limit;
    }
}
