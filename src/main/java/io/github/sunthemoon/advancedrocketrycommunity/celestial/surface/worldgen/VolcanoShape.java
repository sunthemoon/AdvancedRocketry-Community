package io.github.sunthemoon.advancedrocketrycommunity.celestial.surface.worldgen;

import java.util.Random;

/**
 * The shape of one Venus volcano (ADR-063 section 5), as plain arithmetic: a cone of radius at most 32 rising at most
 * 48 blocks above the ground, a crater in its top, and a lava core (a pipe from below the ground to a lava pool in
 * the crater). The legacy generator drew a cone of size 64 with a lava node; this keeps its proportions within the
 * stated bounds.
 */
public final class VolcanoShape {
    public static final int MAX_RADIUS = 32;
    public static final int MAX_HEIGHT = 48;
    public static final int CORE_RADIUS = 2;
    /** How far below the base the lava pipe starts. */
    public static final int CORE_DEPTH = 6;
    /** How far below the base the cone's skirt may fill down to lower ground on a slope. */
    public static final int SKIRT = 32;

    private final int radius;
    private final int height;

    public VolcanoShape(int radius, int height) {
        if (radius < 16 || radius > MAX_RADIUS || height < 16 || height > MAX_HEIGHT) {
            throw new IllegalArgumentException("volcano out of range: " + radius + "/" + height);
        }
        this.radius = radius;
        this.height = height;
    }

    public static VolcanoShape random(Random random) {
        int radius = 24 + random.nextInt(MAX_RADIUS - 24 + 1);
        int height = 32 + random.nextInt(MAX_HEIGHT - 32 + 1);
        return new VolcanoShape(radius, height);
    }

    public int radius() {
        return radius;
    }

    public int height() {
        return height;
    }

    /** The crater's radius at the top of the cone. */
    public int craterRadius() {
        return Math.max(3, radius / 6);
    }

    /** Blocks the cone rises above the base at horizontal distance {@code distance}; 0 outside the cone. */
    public int coneRise(double distance) {
        if (distance >= radius) {
            return 0;
        }
        double t = 1 - distance / radius;
        int rise = (int) Math.round(height * Math.pow(t, 1.6));
        if (distance < craterRadius()) {
            // The crater: a bowl sunk into the summit, its rim at the cone's height there.
            int rim = (int) Math.round(height * Math.pow(1 - (double) craterRadius() / radius, 1.6));
            double c = distance / craterRadius();
            rise = Math.min(rise, rim - (int) Math.round(craterDepth() * (1 - c * c)));
        }
        return Math.max(0, Math.min(height, rise));
    }

    public int craterDepth() {
        return Math.max(2, craterRadius() - 1);
    }

    /** Whether {@code distance} lies inside the lava core. */
    public boolean inCore(double distance) {
        return distance <= CORE_RADIUS;
    }

    /** Whether {@code distance} lies in the crater, where the lava pool is one block deep. */
    public boolean inCrater(double distance) {
        return distance < craterRadius() - 0.5;
    }
}
