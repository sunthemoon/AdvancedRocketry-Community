package io.github.sunthemoon.advancedrocketrycommunity.celestial.surface.worldgen;

import java.util.Arrays;
import java.util.Random;

/**
 * The shape of one impact crater (ADR-063 section 5), as plain arithmetic: a paraboloid bowl whose edge wobbles with
 * a few sine bulges, and a raised rim that falls off outside the edge. The legacy generator favoured small craters
 * and threw up a bank proportional to the radius; this keeps both, within fixed bounds.
 *
 * <p>Bounds: the edge stays within {@code radius × (1 ± MAX_BULGE)}, the rim within {@code RIM_WIDTH} edge radii
 * outside it, so nothing is written farther than {@link #reach()} from the centre.
 */
public final class CraterShape {
    public static final int MIN_RADIUS = 8;
    public static final int MAX_RADIUS = 48;
    /** Total relative wobble of the edge: four sine terms, each at most {@code 5 × 0.0075}. */
    public static final double MAX_BULGE = 0.15;
    public static final double RIM_WIDTH = 0.4;
    private static final double BULGE_STEP = 0.0075;
    private static final int TERMS = 4;

    private final int radius;
    private final int depth;
    private final int rimHeight;
    private final int[] bulges;

    public CraterShape(int radius, int depth, int rimHeight, int[] bulges) {
        if (radius < MIN_RADIUS || radius > MAX_RADIUS) {
            throw new IllegalArgumentException("crater radius out of range: " + radius);
        }
        if (depth < 1 || depth > 15 || rimHeight < 0 || rimHeight > 8) {
            throw new IllegalArgumentException("crater depth or rim out of range");
        }
        if (bulges.length != TERMS || Arrays.stream(bulges).anyMatch(value -> value < 0 || value > 5)) {
            throw new IllegalArgumentException("crater bulges out of range");
        }
        this.radius = radius;
        this.depth = depth;
        this.rimHeight = rimHeight;
        this.bulges = bulges.clone();
    }

    /**
     * A crater drawn with the legacy weighting towards small radii (cubed uniform variable). Depth follows the radius
     * up to the legacy caps of 11 (small) and 15 (large, radius above 32); the rim rises one block per eight of
     * radius.
     */
    public static CraterShape random(Random random, int minRadius, int maxRadius) {
        if (minRadius < MIN_RADIUS || maxRadius > MAX_RADIUS || minRadius > maxRadius) {
            throw new IllegalArgumentException("crater radius range out of bounds");
        }
        double u = random.nextDouble();
        int radius = minRadius + (int) Math.floor((maxRadius - minRadius + 1) * u * u * u);
        radius = Math.min(maxRadius, radius);
        int depth = Math.max(1, Math.min(radius > 32 ? 15 : 11, radius / 3));
        int rimHeight = Math.max(1, Math.min(8, Math.round(radius / 8.0F)));
        int[] bulges = new int[TERMS];
        for (int i = 0; i < TERMS; i++) {
            bulges[i] = random.nextInt(6);
        }
        return new CraterShape(radius, depth, rimHeight, bulges);
    }

    public int radius() {
        return radius;
    }

    public int depth() {
        return depth;
    }

    public int rimHeight() {
        return rimHeight;
    }

    public int[] bulges() {
        return bulges.clone();
    }

    /** The farthest horizontal distance (in blocks, either axis) any write can reach from the centre. */
    public int reach() {
        return (int) Math.ceil(radius * (1 + MAX_BULGE) * (1 + RIM_WIDTH)) + 1;
    }

    /** The bowl's edge radius in the direction of {@code (dx, dz)}. */
    public double edge(double dx, double dz) {
        double angle = Math.atan2(dx, dz);
        double wobble = 0;
        for (int i = 0; i < TERMS; i++) {
            wobble += bulges[i] * BULGE_STEP * Math.sin((i + 2) * angle);
        }
        return radius * (1 + wobble);
    }

    /** Blocks removed below the crater's base height at {@code (dx, dz)}; zero outside the bowl. */
    public int bowlDepth(int dx, int dz) {
        double distance = Math.sqrt((double) dx * dx + (double) dz * dz);
        double edge = edge(dx, dz);
        if (distance >= edge) {
            return 0;
        }
        double t = distance / edge;
        return (int) Math.round(depth * (1 - t * t));
    }

    /** Whether {@code (dx, dz)} lies inside the bowl. */
    public boolean inBowl(int dx, int dz) {
        return Math.sqrt((double) dx * dx + (double) dz * dz) < edge(dx, dz);
    }

    /** Blocks the rim adds above the ground at {@code (dx, dz)}; zero inside the bowl and beyond the rim. */
    public int rimRise(int dx, int dz) {
        double distance = Math.sqrt((double) dx * dx + (double) dz * dz);
        double edge = edge(dx, dz);
        double width = edge * RIM_WIDTH;
        if (distance < edge || distance >= edge + width) {
            return 0;
        }
        double t = (distance - edge) / width;
        return (int) Math.round(rimHeight * (1 - t) * (1 - t));
    }
}
