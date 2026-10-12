package io.github.sunthemoon.advancedrocketrycommunity.celestial.visual;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * ADR-065 D2-A presentation: one rigid attitude from all three phases turns the stars, the sun and the orbited
 * body together, the reference pose is the ADR-047 sky, and the distance changes only the body's apparent size.
 */
final class StationAttitudeSkyTest {
    private static final double SPACE_SUN_ANGLE = Math.PI;

    @Test
    void theReferencePoseIsTheAdr047SkyForMigratedAndNewStations() {
        double[] attitude = SkyMath.stationAttitude(270, 0, 0);
        assertArrayEquals(new double[] {0, 0, 0}, attitude, 1e-12);
        assertArrayEquals(SkyMath.sunDirection(SPACE_SUN_ANGLE), SkyMath.orient(attitude,
                SkyMath.sunDirection(SPACE_SUN_ANGLE)), 1e-12);
        assertArrayEquals(SkyMath.orbitedBodyDirection(), SkyMath.orient(attitude, SkyMath.orbitedBodyDirection()), 1e-12);
        assertEquals(SkyMath.ORBITED_BODY_RADIUS_DEGREES, SkyMath.orbitedBodyRadiusDegrees(4));
    }

    @Test
    void eachAxisTurnsTheSkyAndEastActsLikeTheSunAngle() {
        double[] sun = SkyMath.sunDirection(SPACE_SUN_ANGLE);
        for (double degrees : new double[] {10, 90, 180, 359.999}) {
            // EAST rolls the sun along its own east-west path: the same as adding to the sun angle.
            assertArrayEquals(SkyMath.sunDirection(SPACE_SUN_ANGLE + Math.toRadians(degrees)),
                    SkyMath.orient(SkyMath.stationAttitude(270 + degrees, 0, 0), sun), 1e-9);
        }
        double[] body = SkyMath.orbitedBodyDirection();
        assertTrue(SkyMath.separationDegrees(body, SkyMath.orient(SkyMath.stationAttitude(270, 90, 0), body)) > 1,
                "UP turns the sky about the vertical");
        assertTrue(SkyMath.separationDegrees(body, SkyMath.orient(SkyMath.stationAttitude(270, 0, 90), body)) > 1,
                "NORTH turns the sky about the east-west axis");
    }

    @Test
    void aRigidAttitudeNeverBringsTheDiscOverTheSunOrItsHalo() {
        double[] sun = SkyMath.sunDirection(SPACE_SUN_ANGLE);
        double[] body = SkyMath.orbitedBodyDirection();
        double baseline = SkyMath.separationDegrees(sun, body);
        assertTrue(baseline >= SkyMath.ORBITED_BODY_RADIUS_DEGREES + SkyMath.MAX_SUN_HALO_DEGREES);
        for (int east = 0; east < 360; east += 7) {
            for (int up = 0; up < 360; up += 11) {
                for (int north = 0; north < 360; north += 13) {
                    double[] attitude = SkyMath.stationAttitude(east, up, north);
                    double separation = SkyMath.separationDegrees(SkyMath.orient(attitude, sun),
                            SkyMath.orient(attitude, body));
                    assertEquals(baseline, separation, 1e-6, "attitude " + east + "/" + up + "/" + north);
                }
            }
        }
    }

    @Test
    void theDiscShrinksMonotonicallyWithDistance() {
        double previous = SkyMath.orbitedBodyRadiusDegrees(4);
        for (int distance = 5; distance <= 190; distance++) {
            double radius = SkyMath.orbitedBodyRadiusDegrees(distance);
            assertTrue(radius < previous && radius > 1, distance + ": " + radius);
            previous = radius;
        }
        assertEquals(Math.toDegrees(Math.asin(900.0 / (900.0 + 179 * 200 + 100))), SkyMath.orbitedBodyRadiusDegrees(179),
                1e-12, "GEO is a small but visible disc");
    }
}
