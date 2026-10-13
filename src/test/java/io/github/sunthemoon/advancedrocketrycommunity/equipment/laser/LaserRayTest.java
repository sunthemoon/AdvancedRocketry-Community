package io.github.sunthemoon.advancedrocketrycommunity.equipment.laser;

import static org.junit.jupiter.api.Assertions.*;
import java.util.Random;
import org.junit.jupiter.api.Test;

class LaserRayTest {
    private static LaserRay.Point p(double x, double y, double z) { return new LaserRay.Point(x, y, z); }
    @Test void axisSegmentsAreContinuousAndNeverExceedFiftyBlocks() {
        var cells = LaserRay.cells(p(0.5, 2.5, 0.5), p(10, 0, 0), 50);
        assertEquals(51, cells.size()); assertEquals(0, cells.get(0).enter()); assertEquals(0.5, cells.get(0).exit());
        assertEquals(50, cells.get(50).exit()); assertEquals(50, cells.get(50).x());
        for (int i = 1; i < cells.size(); i++) { assertEquals(cells.get(i - 1).exit(), cells.get(i).enter()); }
    }
    @Test void negativeChunkArithmeticAndExactBoundaryUseFloorNotTruncation() {
        var cells = LaserRay.cells(p(0, 2.5, -16), p(-1, 0, 0), 2);
        assertEquals(0, cells.get(0).exit()); assertEquals(-1, cells.get(1).chunkX());
        assertEquals(-1, cells.get(1).x()); assertEquals(-1, cells.get(1).chunkZ());
        assertEquals(-2, new LaserRay.Cell(-17, 0, -17, 0, 0).chunkX());
    }
    @Test void tiedDiagonalVisitsBoundaryCellsConservatively() {
        var cells = LaserRay.cells(p(15.5, 2.5, 15.5), p(1, 0, 1), 2);
        assertEquals(0, cells.get(0).chunkX()); assertEquals(0, cells.get(0).chunkZ());
        assertEquals(1, cells.get(1).chunkX()); assertEquals(0, cells.get(1).chunkZ());
        assertEquals(cells.get(1).enter(), cells.get(1).exit());
        assertEquals(1, cells.get(2).chunkZ());
    }
    @Test void invalidAndOversizedInputsFailClosed() {
        for (double value : new double[]{Double.NaN, Double.POSITIVE_INFINITY, -1, 0, 50.01}) {
            assertTrue(LaserRay.cells(p(0, 0, 0), p(1, 0, 0), value).isEmpty());
        }
        assertTrue(LaserRay.cells(p(Double.NaN, 0, 0), p(1, 0, 0), 50).isEmpty());
        assertTrue(LaserRay.cells(p(0, 0, 0), p(0, 0, 0), 50).isEmpty());
        assertTrue(LaserRay.cells(p(0, 0, 0), p(Double.MAX_VALUE, Double.MAX_VALUE, 0), 50).isEmpty());
    }
    @Test void seededRaysStayBoundedAndCoverTheWholeLength() {
        Random random = new Random(20);
        for (int trial = 0; trial < 10000; trial++) {
            var cells = LaserRay.cells(p(random.nextDouble() * 40 - 20, random.nextDouble() * 40 - 20,
                    random.nextDouble() * 40 - 20), p(random.nextDouble() - 0.5, random.nextDouble() - 0.5,
                    random.nextDouble() - 0.5), 50);
            assertFalse(cells.isEmpty()); assertTrue(cells.size() <= LaserRay.MAX_CELLS);
            assertEquals(50, cells.get(cells.size() - 1).exit());
            for (int i = 0; i < cells.size(); i++) {
                assertTrue(cells.get(i).exit() >= cells.get(i).enter());
                if (i > 0) { assertEquals(cells.get(i - 1).exit(), cells.get(i).enter()); }
            }
        }
    }
}
