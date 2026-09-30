package io.github.sunthemoon.advancedrocketrycommunity.station.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.sunthemoon.advancedrocketrycommunity.station.model.StationLimits;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.LongSupplier;
import org.junit.jupiter.api.Test;

/** WARP review R4 and R13: the balance headroom belongs to balances, and only real growth moves the bound. */
final class StationStorageBudgetTest {
    /** Enough records that the cheap per-record bound always fails and the measured size decides. */
    private static final int RECORDS = StationLimits.MAX_STATIONS;
    private static final long ENTRY = StationLimits.MAX_WARP_ENERGY_ENTRY_NBT_BYTES;
    private static final long BOUND = StationLimits.MAX_REGISTRY_NBT_BYTES;

    @Test
    void theHeadroomHoldsOneBalanceEntryForEveryStation() {
        assertTrue((long) StationLimits.MAX_WARP_ENERGY_ENTRIES * ENTRY
                <= StationLimits.WARP_ENERGY_HEADROOM_NBT_BYTES);
        assertEquals(BOUND - StationLimits.WARP_ENERGY_HEADROOM_NBT_BYTES, StationStorageBudget.GROWTH_LIMIT);
    }

    @Test
    void aBalanceEntryIsAdmittedUpToTheBoundAndOtherGrowthUpToTheHeadroom() {
        assertTrue(new StationStorageBudget().admitsBalanceEntry(RECORDS, 0, () -> BOUND - ENTRY));
        assertFalse(new StationStorageBudget().admitsBalanceEntry(RECORDS, 0, () -> BOUND - ENTRY + 1));
        long growthLimit = StationStorageBudget.GROWTH_LIMIT;
        assertTrue(new StationStorageBudget().admitsGrowth(RECORDS, 0, () -> growthLimit - ENTRY, ENTRY));
        assertFalse(new StationStorageBudget().admitsGrowth(RECORDS, 0, () -> growthLimit - ENTRY + 1, ENTRY));
        // Inside the headroom a balance entry still fits where every other growth is refused.
        assertTrue(new StationStorageBudget().admitsBalanceEntry(RECORDS, 0, () -> growthLimit + 1));
    }

    @Test
    void smallRegistriesAreNeverEncoded() {
        LongSupplier never = () -> {
            throw new AssertionError("A registry far below its bound was encoded");
        };
        assertTrue(new StationStorageBudget().admitsGrowth(4, 4, never, StationLimits.MAX_STATION_RECORD_NBT_BYTES));
        assertTrue(new StationStorageBudget().admitsBalanceEntry(4, 4, never));
    }

    @Test
    void checksDoNotInflateTheBoundAndRecordedGrowthDoes() {
        StationStorageBudget budget = new StationStorageBudget();
        AtomicInteger encodes = new AtomicInteger();
        LongSupplier measured = () -> {
            encodes.incrementAndGet();
            return StationStorageBudget.GROWTH_LIMIT - 1_000L;
        };
        assertTrue(budget.admitsGrowth(RECORDS, 0, measured, ENTRY));
        assertEquals(1, encodes.get(), "The first check near the bound measures once");
        // Checks for mutations that are then refused or fail record nothing (R13).
        for (int attempt = 0; attempt < 100; attempt++) {
            assertTrue(budget.admitsGrowth(RECORDS, 0, measured, ENTRY));
        }
        assertEquals(1, encodes.get(), "Checks alone moved the running bound");
        budget.grew(960L);
        assertTrue(budget.admitsGrowth(RECORDS, 0, measured, 40L));
        assertEquals(1, encodes.get(), "Recorded growth that still fits needs no measurement");
        assertTrue(budget.admitsGrowth(RECORDS, 0, measured, ENTRY));
        assertEquals(2, encodes.get(), "Crossing the running bound measures again");
    }
}
