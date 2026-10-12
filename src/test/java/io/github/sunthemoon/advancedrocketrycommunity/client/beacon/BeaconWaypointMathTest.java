package io.github.sunthemoon.advancedrocketrycommunity.client.beacon;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** Waypoint bearing in Minecraft's yaw convention, strip placement and distance text. */
class BeaconWaypointMathTest {
    private static final double EPSILON = 1.0E-9;

    @Test
    void bearingIsRelativeToTheViewAndPositiveToTheRight() {
        // Facing south (+Z): ahead, west on the right, east on the left, north behind.
        assertEquals(0.0, BeaconWaypointMath.relativeYaw(0, 10, 0), EPSILON);
        assertEquals(90.0, BeaconWaypointMath.relativeYaw(-10, 0, 0), EPSILON);
        assertEquals(-90.0, BeaconWaypointMath.relativeYaw(10, 0, 0), EPSILON);
        assertEquals(-180.0, BeaconWaypointMath.relativeYaw(0, -10, 0), EPSILON);
        // Facing east (yaw -90): a target to the east is ahead, one to the south is on the right.
        assertEquals(0.0, BeaconWaypointMath.relativeYaw(10, 0, -90), EPSILON);
        assertEquals(90.0, BeaconWaypointMath.relativeYaw(0, 10, -90), EPSILON);
        // Unwrapped view yaws behave like their wrapped equivalents.
        assertEquals(BeaconWaypointMath.relativeYaw(3, 7, 30), BeaconWaypointMath.relativeYaw(3, 7, 30 + 720), EPSILON);
        assertEquals(BeaconWaypointMath.relativeYaw(3, 7, 30), BeaconWaypointMath.relativeYaw(3, 7, 30 - 1080), EPSILON);
    }

    @Test
    void wrappingStaysInHalfOpenRange() {
        for (double degrees = -1000; degrees <= 1000; degrees += 7.5) {
            double wrapped = BeaconWaypointMath.wrap(degrees);
            assertTrue(wrapped >= -180.0 && wrapped < 180.0, Double.toString(degrees));
            assertEquals(0.0, Math.IEEEremainder(wrapped - degrees, 360.0), EPSILON);
        }
    }

    @Test
    void theStripClampsBeyondNinetyDegrees() {
        assertEquals(0, BeaconWaypointMath.stripOffset(0, 91));
        assertEquals(91, BeaconWaypointMath.stripOffset(90, 91));
        assertEquals(-91, BeaconWaypointMath.stripOffset(-90, 91));
        assertEquals(91, BeaconWaypointMath.stripOffset(170, 91));
        assertEquals(-91, BeaconWaypointMath.stripOffset(-180, 91));
        assertEquals(46, BeaconWaypointMath.stripOffset(45, 91));
    }

    @Test
    void distancesUseMetresThenKilometres() {
        assertEquals(new BeaconWaypointMath.Distance("0", false), BeaconWaypointMath.distance(0.2));
        assertEquals(new BeaconWaypointMath.Distance("999", false), BeaconWaypointMath.distance(999.4));
        assertEquals(new BeaconWaypointMath.Distance("1.0", true), BeaconWaypointMath.distance(999.5));
        assertEquals(new BeaconWaypointMath.Distance("42.4", true), BeaconWaypointMath.distance(42_426.0));
        assertFalse(BeaconWaypointMath.distance(12).kilometres());
    }
}
