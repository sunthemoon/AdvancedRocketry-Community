package io.github.sunthemoon.advancedrocketrycommunity.rocket.flight;

import static org.junit.jupiter.api.Assertions.*;

import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketBounds;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketPosition;
import java.util.List;
import org.junit.jupiter.api.Test;

class RocketEntityExtentTest {
    private static RocketBounds bounds(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
        return new RocketBounds(new RocketPosition(minX, minY, minZ), new RocketPosition(maxX, maxY, maxZ));
    }

    @Test void enclosesEveryStructureBlockAroundTheOriginBlockCenter() {
        // The entity stands at the origin block's bottom center, so block x spans [x - 0.5, x + 0.5].
        var extent = RocketEntityExtent.of(bounds(-1, -2, 0, 2, 9, 3), List.of());
        assertEquals(new RocketEntityExtent(-1.5D, -2.0D, -0.5D, 2.5D, 10.0D, 3.5D), extent);
    }

    @Test void aTopSeatAddsTheRidersHeadroomAndAnInnerSeatAddsNothing() {
        var structure = bounds(0, 0, 0, 1, 9, 0);
        // W278 round 7: the seat is the top block, 9 above the origin; the rider's eyes are at 9 + 0.15 + 1.62.
        var top = RocketEntityExtent.of(structure, List.of(new RocketPosition(0, 9, 0)));
        assertEquals(11.0D, top.maxY());
        assertTrue(top.maxY() > 9 + 0.15D + 1.8D, "A seated player's head must stay inside the box");
        var inner = RocketEntityExtent.of(structure, List.of(new RocketPosition(1, 7, 0)));
        assertEquals(10.0D, inner.maxY());
        assertEquals(1.5D, inner.maxX());
    }

    @Test void anAnchorOutsideTheBoundsUsesTheFallbackSeatAndAddsNoHeadroom() {
        var extent = RocketEntityExtent.of(bounds(0, 0, 0, 0, 3, 0), List.of(new RocketPosition(0, 40, 0)));
        assertEquals(4.0D, extent.maxY());
    }

    @Test void anEmptyExtentIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> new RocketEntityExtent(0, 0, 0, 0, 1, 1));
    }
}
