package io.github.sunthemoon.advancedrocketrycommunity.celestial.visual;

/** Pure display math; these values never feed server exposure or navigation. */
public final class SkyMath {
    /** ADR-047: angular radius of the orbited body's disc seen from a station. */
    public static final double ORBITED_BODY_RADIUS_DEGREES = 30;
    /**
     * ADR-047: the disc's centre is below the horizon toward +X, not at the nadir. The Space Level's
     * fixed time (18000) puts the sun at the nadir (sun angle pi), and the disc must not cover it.
     */
    public static final double ORBITED_BODY_ELEVATION_DEGREES = -25;
    /** The sun halo reaches 2.5 sun radii (SkyGeometry.sun(true)); the sun radius is at most 12 degrees. */
    public static final double MAX_SUN_HALO_DEGREES = 2.5 * 12;
    /**
     * ADR-065: the EAST phase at which a station sky equals the ADR-047 sky. It is the BASIC_SPACE sun angle,
     * so a migrated or new station (EAST phase = stored sun angle) keeps its old sky until its owner turns it.
     */
    public static final double STATION_REFERENCE_EAST_DEGREES = 270;
    /** ADR-065: a nominal body radius chosen so the initial distance 4 (900 km) keeps the 30 degree disc. */
    public static final double ORBITED_BODY_RADIUS_KM = 900;

    private SkyMath() { }

    /**
     * ADR-065 D2-A: the orbited body's apparent angular radius at a logical distance (altitude
     * {@code distance * 200 + 100} km): {@code asin(R / (R + altitude))}, exactly the ADR-047 30 degrees at the
     * initial distance and smaller beyond it.
     */
    public static double orbitedBodyRadiusDegrees(int distance) {
        if (distance <= 4) {
            return ORBITED_BODY_RADIUS_DEGREES;
        }
        double altitude = distance * 200.0D + 100.0D;
        return Math.toDegrees(Math.asin(ORBITED_BODY_RADIUS_KM / (ORBITED_BODY_RADIUS_KM + altitude)));
    }

    /**
     * ADR-065: the station attitude in radians from its EAST, UP and NORTH phases in degrees: yaw about the
     * vertical (UP), roll along the sun's east-west path relative to the reference (EAST) and pitch about the
     * east-west axis (NORTH). One rigid rotation turns the stars, the sun and the orbited body together.
     */
    public static double[] stationAttitude(double eastDegrees, double upDegrees, double northDegrees) {
        return new double[] {Math.toRadians(upDegrees), Math.toRadians(eastDegrees - STATION_REFERENCE_EAST_DEGREES),
                Math.toRadians(northDegrees)};
    }

    /**
     * A sky direction turned by {@link #stationAttitude}, as the renderer does with
     * {@code rotateY(yaw).rotateZ(roll).rotateX(pitch)} (standard right-handed rotations, applied to the
     * direction innermost first).
     */
    public static double[] orient(double[] attitude, double[] direction) {
        double[] pitched = rotateX(direction, attitude[2]);
        double[] rolled = rotateZ(pitched, attitude[1]);
        return rotateY(rolled, attitude[0]);
    }

    private static double[] rotateX(double[] v, double angle) {
        double cos = Math.cos(angle), sin = Math.sin(angle);
        return new double[] {v[0], v[1] * cos - v[2] * sin, v[1] * sin + v[2] * cos};
    }

    private static double[] rotateY(double[] v, double angle) {
        double cos = Math.cos(angle), sin = Math.sin(angle);
        return new double[] {v[0] * cos + v[2] * sin, v[1], -v[0] * sin + v[2] * cos};
    }

    private static double[] rotateZ(double[] v, double angle) {
        double cos = Math.cos(angle), sin = Math.sin(angle);
        return new double[] {v[0] * cos - v[1] * sin, v[0] * sin + v[1] * cos, v[2]};
    }

    /** Unit direction of the orbited body's disc centre: the renderer turns +Y about Z by elevation - 90. */
    public static double[] orbitedBodyDirection() {
        double elevation = Math.toRadians(ORBITED_BODY_ELEVATION_DEGREES);
        return new double[] {Math.cos(elevation), Math.sin(elevation), 0};
    }

    /** Unit direction of the sun as the renderer draws it: +Y turned by rotateY(-pi/2).rotateX(angle). */
    public static double[] sunDirection(double angle) {
        return new double[] {-Math.sin(angle), Math.cos(angle), 0};
    }

    public static double separationDegrees(double[] first, double[] second) {
        double dot = first[0] * second[0] + first[1] * second[1] + first[2] * second[2];
        return Math.toDegrees(Math.acos(clamp(dot, -1, 1)));
    }

    public static double daylight(double angle) { return clamp(Math.cos(angle) * 1.5 + 0.25, 0, 1); }

    public static double sunRadius(SkyProfile profile, double solar) {
        return clamp(profile.sunRadius() * Math.sqrt(clamp(solar, 0, 16)), 0.5, 12);
    }

    public static double stars(SkyProfile profile, double pressure, double daylight) {
        return profile.starBrightness() * (1 - clamp(daylight, 0, 1) * clamp(pressure * 50, 0, 1));
    }

    public static int skyColor(SkyProfile profile, double daylight) {
        double mix = clamp(daylight, 0, 1);
        int result = 0;
        for (int shift = 0; shift <= 16; shift += 8) {
            int night = (profile.nightColor() >> shift) & 255;
            int day = (profile.dayColor() >> shift) & 255;
            result |= (int) Math.round(night + (day - night) * mix) << shift;
        }
        return result;
    }

    /** Vanilla-like blue-white lightning tint; presentation only, clamped to a 45 percent blend. */
    public static int flashColor(int rgb, double flash) {
        double blend = clamp(flash, 0, 1) * 0.45;
        int result = 0;
        for (int shift = 0; shift <= 16; shift += 8) {
            int base = (rgb >> shift) & 255;
            int target = shift == 0 ? 255 : 204;
            result |= (int) Math.round(base + (target - base) * blend) << shift;
        }
        return result;
    }

    public static int scaleColor(int rgb, double brightness) {
        double scale = clamp(brightness, 0, 1);
        int result = 0;
        for (int shift = 0; shift <= 16; shift += 8) {
            result |= (int) Math.round(((rgb >> shift) & 255) * scale) << shift;
        }
        return result;
    }

    public static Fog fog(SkyProfile profile, float near, float far) {
        if (!Float.isFinite(near) || !Float.isFinite(far) || near >= far || far <= 0) {
            return new Fog(near, far);
        }
        float end = Math.min(far, (float) profile.fogEnd());
        return new Fog(Math.min(near, (float) (end * profile.fogStart())), end);
    }

    private static double clamp(double value, double minimum, double maximum) {
        return Double.isFinite(value) ? Math.max(minimum, Math.min(maximum, value)) : minimum;
    }

    public record Fog(float near, float far) { }
}
