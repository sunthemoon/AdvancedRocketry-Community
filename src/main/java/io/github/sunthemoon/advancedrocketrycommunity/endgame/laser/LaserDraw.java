package io.github.sunthemoon.advancedrocketrycommunity.endgame.laser;

import io.github.sunthemoon.advancedrocketrycommunity.satellite.resource.SplitMix64;
import java.util.Objects;

/**
 * ADR-055 section 2 {@code laser-v1}: the n-th output of {@code SplitMix64(seed ^ LASERDRL)} (ADR-052 section 3),
 * computed without iterating, picks the first entry in file order whose cumulative weight exceeds
 * {@code (z >>> 1) % Σ weight}. The same seed and index always give the same stack.
 */
public final class LaserDraw {
    public static final String ALGORITHM = "laser-v1";
    /** The ASCII of {@code LASERDRL}. */
    public static final long LASERDRL = 0x4C4153455244524CL;
    private static final long GAMMA = 0x9E3779B97F4A7C15L;

    private LaserDraw() {
    }

    /** {@code mix64((seed ^ LASERDRL) + (n + 1) × γ)} with 64-bit wrapping arithmetic. */
    public static long output(long seed, long n) {
        // SplitMix64.next() adds γ once before mixing, so starting at base + n×γ yields mix64(base + (n+1)×γ).
        return new SplitMix64((seed ^ LASERDRL) + n * GAMMA).next();
    }

    public static LaserDrillTable.Entry draw(LaserDrillTable table, long seed, long n) {
        Objects.requireNonNull(table, "table");
        if (n < 0) {
            throw new IllegalArgumentException("The operation index is not negative");
        }
        long r = (output(seed, n) >>> 1) % table.totalWeight();
        long cumulative = 0;
        for (LaserDrillTable.Entry entry : table.entries()) {
            cumulative += entry.weight();
            if (cumulative > r) {
                return entry;
            }
        }
        throw new IllegalStateException("The cumulative weight did not reach the draw");
    }
}
