package io.github.sunthemoon.advancedrocketrycommunity.celestial.surface.worldgen;

/**
 * The shape of one ore geode (ADR-063 section 5), as plain arithmetic: a lens-shaped hollow of radius at most 24
 * (legacy 24–48, capped for the generation budget) below the surface, lined with geode shell, with ore clusters
 * hanging from the roof and rising from the floor on a four-block grid, as the legacy generator drew them.
 */
public final class GeodeShape {
    public static final int MIN_RADIUS = 16;
    public static final int MAX_RADIUS = 24;
    /** Ore cluster length at most this many blocks. */
    public static final int MAX_CLUSTER = 3;

    private final int radius;

    public GeodeShape(int radius) {
        if (radius < MIN_RADIUS || radius > MAX_RADIUS) {
            throw new IllegalArgumentException("geode radius out of range: " + radius);
        }
        this.radius = radius;
    }

    public int radius() {
        return radius;
    }

    /** The hollow's half height at horizontal offset {@code (dx, dz)}; negative outside the lens. */
    public int halfHeight(int dx, int dz) {
        int squared = dx * dx + dz * dz;
        // Floor division: a column just outside the radius is outside the lens (negative), not a zero-height rim.
        return Math.floorDiv(radius * radius - squared, radius * 2);
    }

    /** The farthest vertical distance from the centre that the shell reaches. */
    public int verticalReach() {
        return radius / 2 + 1;
    }

    /** Whether a roof cluster hangs at this column (the legacy four-block grid). */
    public static boolean roofCluster(int dx, int dz) {
        return Math.floorMod(dx, 4) > 0 && Math.floorMod(dz, 4) > 0;
    }

    /** Whether a floor cluster rises at this column (the grid shifted by two). */
    public static boolean floorCluster(int dx, int dz) {
        return Math.floorMod(dx + 2, 4) > 0 && Math.floorMod(dz + 2, 4) > 0;
    }

    /** The length of the cluster at this column, 1..MAX_CLUSTER, and never more than leaves an open middle. */
    public int clusterLength(int dx, int dz, long salt) {
        int half = halfHeight(dx, dz);
        if (half < 3) {
            return 0;
        }
        long mixed = (dx * 341873128712L) ^ (dz * 132897987541L) ^ salt;
        int length = 1 + (int) Math.floorMod(mixed >>> 7, (long) MAX_CLUSTER);
        return Math.min(length, half - 2);
    }
}
