package io.github.sunthemoon.advancedrocketrycommunity.client.beacon;

import java.util.Locale;

/** Pure display arithmetic of the finder waypoints: bearing relative to the view, strip offset and distance text. */
public final class BeaconWaypointMath {
    /** Beyond this many degrees from the view, a marker sits at the strip's edge. */
    public static final double STRIP_DEGREES = 90.0;

    private BeaconWaypointMath() {
    }

    /**
     * The bearing of a horizontal offset relative to a view yaw, in degrees in {@code [-180, 180)}: zero straight ahead,
     * positive to the right. Yaw follows Minecraft: 0 faces +Z (south) and 90 faces -X (west).
     */
    public static double relativeYaw(double dx, double dz, double viewYaw) {
        double target = Math.toDegrees(Math.atan2(-dx, dz));
        return wrap(target - viewYaw);
    }

    public static double wrap(double degrees) {
        double wrapped = degrees % 360.0;
        if (wrapped >= 180.0) {
            wrapped -= 360.0;
        } else if (wrapped < -180.0) {
            wrapped += 360.0;
        }
        return wrapped;
    }

    /** The marker's offset from the strip's centre, clamped to {@code [-half, half]}. */
    public static int stripOffset(double relative, int half) {
        double clamped = Math.max(-STRIP_DEGREES, Math.min(STRIP_DEGREES, relative));
        return (int) Math.round(clamped / STRIP_DEGREES * half);
    }

    /** Whole metres below 1,000, else kilometres with one decimal; the unit comes from the language file. */
    public static Distance distance(double metres) {
        if (metres < 999.5) {
            return new Distance(Long.toString(Math.round(Math.max(0.0, metres))), false);
        }
        return new Distance(String.format(Locale.ROOT, "%.1f", metres / 1000.0), true);
    }

    public record Distance(String value, boolean kilometres) {
    }
}
