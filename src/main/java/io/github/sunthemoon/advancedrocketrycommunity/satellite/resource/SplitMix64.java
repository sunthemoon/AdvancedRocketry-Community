package io.github.sunthemoon.advancedrocketrycommunity.satellite.resource;

/** ADR-052 section 3: SplitMix64 with 64-bit wrapping arithmetic, and {@code bounded(n) = (next() >>> 1) % n}. */
public final class SplitMix64 {
    /** Domain constants, the ASCII of their names. */
    public static final long SURVEY01 = 0x5355525645593031L;
    public static final long ASTTYPE1 = 0x4153545459504531L;
    public static final long ASTYIELD = 0x4153545949454C44L;

    private long state;

    public SplitMix64(long seed) {
        this.state = seed;
    }

    public long next() {
        state += 0x9E3779B97F4A7C15L;
        long z = state;
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        return z ^ (z >>> 31);
    }

    /** A value in {@code [0, n)} for {@code 1 <= n <= 2^31}. */
    public long bounded(long n) {
        if (n < 1L || n > (1L << 31)) {
            throw new IllegalArgumentException("bounded(n) needs 1 <= n <= 2^31");
        }
        return (next() >>> 1) % n;
    }
}
