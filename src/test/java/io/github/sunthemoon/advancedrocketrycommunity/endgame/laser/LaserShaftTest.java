package io.github.sunthemoon.advancedrocketrycommunity.endgame.laser;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.github.sunthemoon.advancedrocketrycommunity.endgame.model.EndgameCode;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

/** ADR-055 section 3: shaft geometry against the C10 reference vectors, and the layer classification order. */
final class LaserShaftTest {
    /** marker x, y, z, inside chunk, max depth, min build height, floor, first cell x, z, last cell x, z. */
    private static final int[][] VECTORS = {
            {8, 70, 8, 1, 64, -64, 6, 7, 7, 9, 9},
            {0, 70, 5, 0, 256, -64, -63, -1, 4, 1, 6},
            {-1, 64, -17, 0, 1, -64, 63, -2, -18, 0, -16},
            {-2, 100, -15, 0, 64, -64, 36, -3, -16, -1, -14},
            {14, 0, 1, 0, 64, -64, -63, 13, 0, 15, 2},
            {15, 0, 1, 0, 64, -64, -63, 14, 0, 16, 2},
            {2, 40, 13, 1, 64, -64, -24, 1, 12, 3, 14},
            {-3, 40, -14, 1, 64, -64, -24, -4, -15, -2, -13},
            {1, 40, 8, 0, 64, -64, -24, 0, 7, 2, 9}};

    @Test
    void geometryMatchesTheReferenceVectors() {
        for (int[] v : VECTORS) {
            BlockPos marker = new BlockPos(v[0], v[1], v[2]);
            assertEquals(v[3] == 1, LaserShaft.footprintInsideChunk(marker), marker.toShortString());
            assertEquals(v[6], LaserShaft.floor(marker, v[5], v[4]), marker.toShortString());
            List<BlockPos> cells = LaserShaft.layer(marker, LaserShaft.firstLayer(marker));
            assertEquals(9, cells.size());
            assertEquals(new BlockPos(v[7], v[1] - 1, v[8]), cells.get(0));
            assertEquals(new BlockPos(v[9], v[1] - 1, v[10]), cells.get(8));
            for (int i = 0; i < 9; i++) {
                assertEquals(new BlockPos(v[0] - 1 + i % 3, v[1] - 1, v[2] - 1 + i / 3), cells.get(i));
            }
        }
    }

    @Test
    void theFirstImmuneOrFluidCellInOrderStopsTheLayer() {
        List<LaserShaft.Cell> layer = new ArrayList<>(Collections.nCopies(9, LaserShaft.Cell.BREAKABLE));
        assertEquals(EndgameCode.OK, LaserShaft.classify(layer));
        layer.set(4, LaserShaft.Cell.AIR);
        assertEquals(EndgameCode.OK, LaserShaft.classify(layer), "air needs nothing");
        layer.set(7, LaserShaft.Cell.FLUID);
        assertEquals(EndgameCode.BLOCKED_FLUID, LaserShaft.classify(layer));
        layer.set(2, LaserShaft.Cell.IMMUNE);
        assertEquals(EndgameCode.BLOCKED_IMMUNE, LaserShaft.classify(layer), "an earlier immune cell wins");
        layer.set(1, LaserShaft.Cell.FLUID);
        assertEquals(EndgameCode.BLOCKED_FLUID, LaserShaft.classify(layer), "an earlier fluid cell wins");
        assertThrows(IllegalArgumentException.class, () -> LaserShaft.classify(layer.subList(0, 8)));
    }
}
