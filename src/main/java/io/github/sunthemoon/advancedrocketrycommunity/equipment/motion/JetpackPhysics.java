package io.github.sunthemoon.advancedrocketrycommunity.equipment.motion;

import java.util.Objects;

/**
 * ADR-066 section 5.2 proposal arithmetic, shared by the paying server and the receiving client. The server plans
 * one bounded increment from its own motion estimate; both sides add it with the same caps. Nothing here reads a
 * velocity or height from the client.
 */
public final class JetpackPhysics {
    public static final double BASE_INCREMENT = 0.08;
    public static final double MIN_FORCE = 0.05;
    public static final double MAX_FORCE = 4.0;
    public static final double VERTICAL_CAP = 0.6;
    public static final double HORIZONTAL_CAP = 0.8;
    /** {@code flight_speed_upgrade}: 20% of the paid increment, horizontally. */
    public static final double SPEED_ASSIST = 0.2;
    /** HOVER aims for this fraction of the distance below the reference height per tick. */
    public static final double HOVER_GAIN = 0.25;
    public static final double MAX_INCREMENT = BASE_INCREMENT * MAX_FORCE;
    public static final double MAX_ASSIST = SPEED_ASSIST * MAX_INCREMENT;
    /** Float wire rounding allowance for the bounds above. */
    static final double WIRE_TOLERANCE = 1.0E-6;
    private static final double MOVING = 1.0E-4;

    public record Motion(double x, double y, double z) {
        public boolean finite() { return Double.isFinite(x) && Double.isFinite(y) && Double.isFinite(z); }
        public double horizontal() { return Math.hypot(x, z); }
    }

    /** One paid velocity increment. {@link #ZERO} means no correction and no payment. */
    public record Impulse(double x, double y, double z) {
        public static final Impulse ZERO = new Impulse(0, 0, 0);
        public boolean isZero() { return x == 0 && y == 0 && z == 0; }
        public boolean bounded() {
            return Double.isFinite(x) && Double.isFinite(y) && Double.isFinite(z) && y >= 0
                    && y <= MAX_INCREMENT + WIRE_TOLERANCE && Math.hypot(x, z) <= MAX_ASSIST + WIRE_TOLERANCE;
        }
    }

    public static double increment(double force) {
        if (!Double.isFinite(force) || force < MIN_FORCE || force > MAX_FORCE) {
            throw new IllegalArgumentException("Jetpack force is outside 0.05..4: " + force);
        }
        return BASE_INCREMENT * force;
    }

    /**
     * Plans one correction. The vertical target is the cap in NORMAL mode and a bounded pull toward the reference
     * height in HOVER mode; the thrust never pushes down, so gravity above one increment still wins.
     */
    public static Impulse plan(JetpackMode mode, Motion estimate, double force, double belowReference,
                               boolean speedAssist) {
        Objects.requireNonNull(mode, "mode");
        Objects.requireNonNull(estimate, "estimate");
        double step = increment(force);
        if (!estimate.finite() || !Double.isFinite(belowReference)) { return Impulse.ZERO; }
        double target = mode == JetpackMode.NORMAL ? VERTICAL_CAP
                : Math.max(-VERTICAL_CAP, Math.min(VERTICAL_CAP, HOVER_GAIN * belowReference));
        double y = Math.max(0, Math.min(step, target - estimate.y()));
        double x = 0;
        double z = 0;
        double horizontal = estimate.horizontal();
        if (speedAssist && horizontal > MOVING && horizontal < HORIZONTAL_CAP) {
            double assist = Math.min(SPEED_ASSIST * step, HORIZONTAL_CAP - horizontal);
            x = estimate.x() / horizontal * assist;
            z = estimate.z() / horizontal * assist;
        }
        return y == 0 && x == 0 && z == 0 ? Impulse.ZERO : new Impulse(x, y, z);
    }

    /** Adds a paid increment without raising a speed above its cap or lowering a faster existing speed. */
    public static Motion apply(Motion current, Impulse impulse) {
        Objects.requireNonNull(current, "current");
        Objects.requireNonNull(impulse, "impulse");
        if (!current.finite() || !impulse.bounded()) { return current; }
        double y = current.y() >= VERTICAL_CAP ? current.y() : Math.min(current.y() + impulse.y(), VERTICAL_CAP);
        double x = current.x() + impulse.x();
        double z = current.z() + impulse.z();
        double limit = Math.max(current.horizontal(), HORIZONTAL_CAP);
        double after = Math.hypot(x, z);
        if (after > limit) {
            x = x / after * limit;
            z = z / after * limit;
        }
        return new Motion(x, y, z);
    }

    private JetpackPhysics() { }
}
