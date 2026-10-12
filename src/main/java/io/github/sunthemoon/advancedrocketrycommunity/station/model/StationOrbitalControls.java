package io.github.sunthemoon.advancedrocketrycommunity.station.model;

import java.util.Objects;

/**
 * ADR-065 D2-A: a station's logical orbital distance and three analytic orientation phases and rates
 * (record schema 3). Nothing physically moves. A phase is fixed-point milli-degrees; a rate is signed
 * turns per {@value #RATE_PERIOD_TICKS} ticks. The phase at a tick is computed from
 * {@code (phase, rate, epoch)} with modular integer arithmetic (section 5.2.1), never by stepping.
 */
public record StationOrbitalControls(
        int distance,
        int eastPhase,
        int upPhase,
        int northPhase,
        int eastRate,
        int upRate,
        int northRate,
        long epoch
) {
    public static final int FORMAT_VERSION = 1;
    public static final int MIN_DISTANCE = 4;
    public static final int MAX_DISTANCE = 190;
    public static final int INITIAL_DISTANCE = MIN_DISTANCE;
    /** Legacy display: {@code distance * 200 + 100} km. */
    public static final int KILOMETRES_PER_DISTANCE = 200;
    public static final int KILOMETRES_OFFSET = 100;
    /** The legacy GEO gate, inclusive. */
    public static final int GEO_MIN_DISTANCE = 177;
    public static final int GEO_MAX_DISTANCE = 181;
    public static final int PHASES_PER_TURN = 360_000;
    public static final int MAX_PHASE = PHASES_PER_TURN - 1;
    public static final int MAX_RATE = 60;
    public static final int RATE_PERIOD_TICKS = 72_000;
    /** Milli-degrees per tick for a rate of one turn per period: 360,000 / 72,000. */
    public static final int PHASE_STEP_PER_RATE = PHASES_PER_TURN / RATE_PERIOD_TICKS;

    public StationOrbitalControls {
        if (distance < MIN_DISTANCE || distance > MAX_DISTANCE) {
            throw new IllegalArgumentException("Station orbital distance is outside 4..190");
        }
        requirePhase(eastPhase);
        requirePhase(upPhase);
        requirePhase(northPhase);
        requireRate(eastRate);
        requireRate(upRate);
        requireRate(northRate);
        if (epoch < 0L) {
            throw new IllegalArgumentException("Station orbital epoch cannot be negative");
        }
    }

    /**
     * Migrated and newly created records: distance 4, no rotation, epoch 0, and an EAST phase equal to the
     * stored sun angle, so the derived sun angle reproduces it exactly.
     */
    public static StationOrbitalControls initial(int solarAngleMilliDegrees) {
        return new StationOrbitalControls(INITIAL_DISTANCE, solarAngleMilliDegrees, 0, 0, 0, 0, 0, 0L);
    }

    public static boolean validPhase(int phase) {
        return phase >= 0 && phase <= MAX_PHASE;
    }

    public static boolean validRate(int rate) {
        return rate >= -MAX_RATE && rate <= MAX_RATE;
    }

    public static boolean validDistance(int distance) {
        return distance >= MIN_DISTANCE && distance <= MAX_DISTANCE;
    }

    /**
     * {@code floorMod(phase + (delta % 72000) * rate * 5, 360000)}. The reduced product is at most
     * 21,599,700 in magnitude, so no elapsed-time overflow or floating-point drift is possible.
     */
    public static int advance(int phase, int rate, long delta) {
        requirePhase(phase);
        requireRate(rate);
        if (delta < 0L) {
            throw new IllegalArgumentException("Elapsed orbital ticks cannot be negative");
        }
        long reduced = (delta % RATE_PERIOD_TICKS) * rate * PHASE_STEP_PER_RATE;
        return (int) Math.floorMod(phase + reduced, (long) PHASES_PER_TURN);
    }

    public int phase(Axis axis) {
        return switch (Objects.requireNonNull(axis, "axis")) {
            case EAST -> eastPhase;
            case UP -> upPhase;
            case NORTH -> northPhase;
        };
    }

    public int rate(Axis axis) {
        return switch (Objects.requireNonNull(axis, "axis")) {
            case EAST -> eastRate;
            case UP -> upRate;
            case NORTH -> northRate;
        };
    }

    public boolean rotating() {
        return eastRate != 0 || upRate != 0 || northRate != 0;
    }

    /** The phase at {@code tick}; a tick before the epoch is an ordering error. */
    public int phaseAt(Axis axis, long tick) {
        if (tick < epoch) {
            throw new IllegalArgumentException("Orbital tick precedes the epoch");
        }
        return advance(phase(axis), rate(axis), tick - epoch);
    }

    /** The same rotation re-based at {@code tick}: the phases at that tick become the epoch phases. */
    public StationOrbitalControls sampledAt(long tick) {
        return new StationOrbitalControls(distance, phaseAt(Axis.EAST, tick), phaseAt(Axis.UP, tick),
                phaseAt(Axis.NORTH, tick), eastRate, upRate, northRate, tick);
    }

    /** A rate change samples the current phases into a new epoch at {@code tick}, then keeps the new rates. */
    public StationOrbitalControls withRates(int east, int up, int north, long tick) {
        StationOrbitalControls sampled = sampledAt(tick);
        return new StationOrbitalControls(distance, sampled.eastPhase, sampled.upPhase, sampled.northPhase,
                east, up, north, tick);
    }

    public StationOrbitalControls stoppedAt(long tick) {
        return withRates(0, 0, 0, tick);
    }

    /** A fixed phase, chosen only while every rate is zero; its epoch is {@code tick}. */
    public StationOrbitalControls withPhases(int east, int up, int north, long tick) {
        if (rotating()) {
            throw new IllegalStateException("A fixed phase needs every rate stopped first");
        }
        if (tick < epoch) {
            throw new IllegalArgumentException("Orbital tick precedes the epoch");
        }
        return new StationOrbitalControls(distance, east, up, north, 0, 0, 0, tick);
    }

    public StationOrbitalControls withDistance(int updated) {
        return new StationOrbitalControls(updated, eastPhase, upPhase, northPhase, eastRate, upRate, northRate, epoch);
    }

    /** True when phases, rates and epoch are equal (the orientation part, not the distance). */
    public boolean sameOrientation(StationOrbitalControls other) {
        Objects.requireNonNull(other, "other");
        return eastPhase == other.eastPhase && upPhase == other.upPhase && northPhase == other.northPhase
                && eastRate == other.eastRate && upRate == other.upRate && northRate == other.northRate
                && epoch == other.epoch;
    }

    public long altitudeKilometres() {
        return (long) distance * KILOMETRES_PER_DISTANCE + KILOMETRES_OFFSET;
    }

    public boolean geostationary() {
        return distance >= GEO_MIN_DISTANCE && distance <= GEO_MAX_DISTANCE;
    }

    private static void requirePhase(int phase) {
        if (!validPhase(phase)) {
            throw new IllegalArgumentException("Station orbital phase is outside 0..359,999");
        }
    }

    private static void requireRate(int rate) {
        if (!validRate(rate)) {
            throw new IllegalArgumentException("Station orbital rate is outside -60..60");
        }
    }

    /** The three axes, in the persisted and wire order. */
    public enum Axis {
        EAST,
        UP,
        NORTH
    }
}
