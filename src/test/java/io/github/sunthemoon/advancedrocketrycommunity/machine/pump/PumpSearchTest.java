package io.github.sunthemoon.advancedrocketrycommunity.machine.pump;

import static org.junit.jupiter.api.Assertions.*;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class PumpSearchTest {
    private static final PumpSearch.Point ORIGIN = new PumpSearch.Point(100, 100, -50);
    private static final PumpSearch.Cell AIR = PumpSearch.Cell.of(PumpSearch.Kind.AIR);
    private static PumpSearch.Cell fluid(boolean source) { return new PumpSearch.Cell(PumpSearch.Kind.FLUID, "minecraft:water", source); }
    @Test void sharedResourceIdLimitIs128AtSearchAdmission() {
        String longest = "test:" + "a".repeat(123);
        assertEquals(128, longest.length());
        assertDoesNotThrow(() -> new PumpSearch.Cell(PumpSearch.Kind.FLUID, longest, true));
        assertThrows(IllegalArgumentException.class,
                () -> new PumpSearch.Cell(PumpSearch.Kind.FLUID, longest + "a", true));
        for (String invalid : new String[]{"water", "UPPER:water", "test:", "test:bad value"}) {
            assertThrows(IllegalArgumentException.class,
                    () -> new PumpSearch.Cell(PumpSearch.Kind.FLUID, invalid, true));
        }
    }
    @Test void downwardColumnVisitsExactly64ThenIdles() {
        var search = new PumpSearch(ORIGIN);
        Set<PumpSearch.Point> visited = new HashSet<>();
        var result = search.advance(point -> { assertTrue(search.inside(point)); assertTrue(visited.add(point)); return AIR; });
        assertEquals(64, result.inspected());
        assertEquals(PumpCode.SEARCHING, result.code());
        result = search.advance(point -> fail("65th downward read"));
        assertEquals(PumpCode.NO_FLUID, result.code());
        assertEquals(64, search.totalInspected());
        assertEquals(0, search.advance(point -> fail("finished search rescanned")).inspected());
    }
    @Test void firstNonAirIsTheOnlySeedAndLowestAllowedSourceIsIncluded() {
        var lowest = ORIGIN.offset(0, -64, 0);
        var search = new PumpSearch(ORIGIN);
        var result = search.advance(point -> point.equals(lowest) ? fluid(true) : AIR);
        assertEquals(PumpCode.SOURCE_READY, result.code());
        assertEquals(lowest, result.candidate());
        assertEquals(64, result.inspected());
        var blocked = new PumpSearch(ORIGIN);
        result = blocked.advance(point -> PumpSearch.Cell.of(PumpSearch.Kind.OTHER));
        assertEquals(PumpCode.NO_FLUID, result.code()); assertEquals(1, result.inspected());
    }
    @Test void connectedFlowFindsSourceButNeverCrossesDifferentFluid() {
        Map<PumpSearch.Point, PumpSearch.Cell> cells = new HashMap<>();
        var seed = ORIGIN.offset(0, -1, 0);
        var source = seed.offset(2, 0, 0);
        cells.put(seed, fluid(false)); cells.put(seed.offset(1, 0, 0), fluid(false)); cells.put(source, fluid(true));
        var search = new PumpSearch(ORIGIN);
        var result = search.advance(point -> cells.getOrDefault(point, AIR));
        assertEquals(source, result.candidate());
        assertEquals(0, search.advance(point -> fail("pending candidate inspected twice")).inspected());
        cells.put(source, AIR); search.drained();
        while (search.advance(point -> cells.getOrDefault(point, AIR)).code() == PumpCode.SEARCHING) { }
        assertTrue(search.totalInspected() <= 4096);
        var separated = new PumpSearch(ORIGIN);
        result = separated.advance(point -> point.equals(seed) ? new PumpSearch.Cell(PumpSearch.Kind.FLUID, "minecraft:lava", false)
                : point.equals(seed.offset(1, 0, 0)) ? fluid(true) : AIR);
        assertEquals(PumpCode.SEARCH_EXHAUSTED, result.code());
    }
    @Test void enormousFluidVolumeHas64PerTickAnd4096TotalIncludingProbeColumn() {
        var search = new PumpSearch(ORIGIN);
        AtomicInteger reads = new AtomicInteger();
        PumpSearch.Result result;
        do {
            int before = reads.get();
            result = search.advance(point -> {
                assertTrue(search.inside(point)); reads.incrementAndGet(); return fluid(false);
            });
            assertTrue(reads.get() - before <= 64);
            assertEquals(reads.get() - before, result.inspected());
            assertTrue(search.reserved() <= 4096);
        } while (result.code() == PumpCode.SEARCHING);
        assertEquals(PumpCode.SEARCH_LIMIT, result.code());
        assertEquals(4096, reads.get()); assertEquals(reads.get(), search.totalInspected());
    }
    @Test void pumpOriginBoxIncludesCornersAndExcludesAboveBelowAnd33rdColumn() {
        var search = new PumpSearch(ORIGIN);
        assertTrue(search.inside(ORIGIN.offset(32, -64, -32)));
        assertTrue(search.inside(ORIGIN.offset(-32, -1, 32)));
        assertFalse(search.inside(ORIGIN.offset(33, -1, 0)));
        assertFalse(search.inside(ORIGIN.offset(0, -65, 0)));
        assertFalse(search.inside(ORIGIN));
        assertFalse(search.inside(ORIGIN.offset(0, 1, 0)));
    }
    @Test void unreadableOrUnsupportedSeedStopsBeforeAnyLaterLookup() {
        for (var kind : new PumpSearch.Kind[]{PumpSearch.Kind.UNLOADED, PumpSearch.Kind.OUT_OF_BOUNDS, PumpSearch.Kind.UNSUPPORTED}) {
            var search = new PumpSearch(ORIGIN);
            var result = search.advance(point -> PumpSearch.Cell.of(kind));
            assertEquals(1, result.inspected());
            assertTrue(Set.of(PumpCode.TARGET_UNLOADED, PumpCode.TARGET_OUT_OF_BOUNDS, PumpCode.SOURCE_UNSUPPORTED).contains(result.code()));
            assertEquals(0, search.advance(point -> fail("refused search continued")).inspected());
        }
    }
    @Test void integerCoordinateEdgeRefusesWithoutOverflowedLookup() {
        var search = new PumpSearch(new PumpSearch.Point(0, Integer.MIN_VALUE, 0));
        assertEquals(PumpCode.TARGET_OUT_OF_BOUNDS, search.advance(point -> fail("overflowed lookup")).code());
        assertEquals(0, search.totalInspected());
    }
}
