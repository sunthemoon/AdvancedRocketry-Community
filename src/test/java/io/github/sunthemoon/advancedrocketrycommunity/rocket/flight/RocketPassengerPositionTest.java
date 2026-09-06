package io.github.sunthemoon.advancedrocketrycommunity.rocket.flight;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class RocketPassengerPositionTest {
    @Test
    void pendingMetadataStillKeepsTheRiderAboveTheMovingRocket() {
        assertEquals(new RocketPassengerPosition(0.0D, 1.15D, 0.0D),
                RocketPassengerPosition.forSeat(0, 0));
    }

    @Test
    void primarySeatPreservesTheEstablishedHeightAtEveryCapacity() {
        for (int capacity = 1; capacity <= RocketFlightLimits.MAX_PASSENGERS; capacity++) {
            assertEquals(new RocketPassengerPosition(0.0D, 1.15D, 0.0D),
                    RocketPassengerPosition.forSeat(0, capacity));
        }
    }

    @Test
    void secondarySeatKeepsItsSlotInsteadOfUsingTheOnlinePassengerCount() {
        var offset = RocketPassengerPosition.forSeat(1, 2);
        assertEquals(-0.35D, offset.x(), 1.0E-12);
        assertEquals(1.15D, offset.y());
        assertEquals(0.0D, offset.z(), 1.0E-12);
        for (int seat = 1; seat < RocketFlightLimits.MAX_PASSENGERS; seat++) {
            var bounded = RocketPassengerPosition.forSeat(seat, RocketFlightLimits.MAX_PASSENGERS);
            assertEquals(0.35D, Math.hypot(bounded.x(), bounded.z()), 1.0E-12);
        }
    }

    @Test
    void invalidSlotAndCapacityFailBeforeComputingCoordinates() {
        for (int[] invalid : new int[][]{{-1, 2}, {2, 2}, {1, 0}, {0, -1}, {0, 17}}) {
            assertThrows(IllegalArgumentException.class,
                    () -> RocketPassengerPosition.forSeat(invalid[0], invalid[1]));
        }
    }
}
