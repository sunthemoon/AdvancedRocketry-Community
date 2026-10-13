package io.github.sunthemoon.advancedrocketrycommunity.client.beacon;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.OptionalInt;
import org.junit.jupiter.api.Test;

class BeaconFinderLayoutTest {
    @Test
    void noBossBarsKeepTheOriginalTop() {
        assertEquals(OptionalInt.of(3), BeaconFinderLayout.top(0, 240, 9));
    }

    @Test
    void vanillaBarsAndNamesCannotOverlapTheStripOrLabel() {
        // BossHealthOverlay.render: y=12, name y=y-9, bar height=5, default increment=10+9.
        for (int height : new int[] {60, 90, 120, 240, 540, 1080}) {
            long bottom = 0;
            for (int y = 12; ; y += 19) {
                bottom = BeaconFinderLayout.includeBossBar(bottom, y);
                var top = BeaconFinderLayout.top(bottom, height, 9);
                assertTrue(top.isEmpty() || top.getAsInt() - 1 >= bottom + 3,
                        "The highest strip pixel must clear every boss name and bar");
                if (y + 19 >= height / 3) {
                    break;
                }
            }
        }
        assertEquals(OptionalInt.of(21), BeaconFinderLayout.top(17, 240, 9));
        assertEquals(OptionalInt.of(40), BeaconFinderLayout.top(36, 240, 9));
    }

    @Test
    void actualBarPositionsHandleCustomIncrementsAndOutOfOrderEvents() {
        long bottom = BeaconFinderLayout.includeBossBar(0, 12);
        bottom = BeaconFinderLayout.includeBossBar(bottom, 60);
        bottom = BeaconFinderLayout.includeBossBar(bottom, 31);
        assertEquals(65, bottom);
        assertEquals(OptionalInt.of(69), BeaconFinderLayout.top(bottom, 240, 9));
    }

    @Test
    void aShortViewportOmitsTheWholeStripInsteadOfOverlappingOrClipping() {
        assertEquals(OptionalInt.of(21), BeaconFinderLayout.top(17, 39, 9));
        assertTrue(BeaconFinderLayout.top(17, 38, 9).isEmpty());
        assertTrue(BeaconFinderLayout.top(17, 39, 10).isEmpty());
        assertTrue(BeaconFinderLayout.top(0, 20, 9).isEmpty());
    }

    @Test
    void pixelArithmeticCannotWrapAtIntegerLimits() {
        long bottom = BeaconFinderLayout.includeBossBar(0, Integer.MAX_VALUE);
        assertEquals((long) Integer.MAX_VALUE + 5, bottom);
        assertTrue(BeaconFinderLayout.top(bottom, Integer.MAX_VALUE, 9).isEmpty());
    }
}
