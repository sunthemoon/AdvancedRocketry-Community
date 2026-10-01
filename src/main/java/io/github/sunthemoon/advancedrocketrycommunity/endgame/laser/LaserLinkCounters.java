package io.github.sunthemoon.advancedrocketrycommunity.endgame.laser;

/**
 * ADR-055 section 3 payment counters of one physical link. The controller keeps {@code ops_paid}, the marker
 * {@code ops_done}; they are saved in different chunks, so a crash can leave either ahead. At every contact the gap
 * is settled before a new layer runs: a debt ({@code done > paid}) is paid one layer at a time as energy arrives, a
 * credit ({@code paid > done}) runs the paid layers without a new payment, and only equal counters start a normal
 * operation (pay, run, count, all in one tick). {@link LaserPhysicalDrill} takes every step from {@link #next} and
 * every contact decision from {@link #accepts} (review C11R-L2), so these tests describe the engine.
 */
public final class LaserLinkCounters {
    private LaserLinkCounters() {
    }

    /**
     * Whether a marker accepts a link at contact: the exact link it serves; when it serves none, a link that never
     * touched it ({@code linkGeneration < 0}) or one recorded at its current generation, so a link dropped by a reset
     * never returns however many links and resets follow (review C11R-M1).
     */
    public static boolean accepts(boolean markerLinked, boolean sameLink, long linkGeneration, long markerGeneration) {
        if (markerGeneration < 0 || linkGeneration < -1) {
            throw new IllegalArgumentException("Generations are -1 (no contact yet) or more");
        }
        if (markerLinked) {
            return sameLink;
        }
        return linkGeneration < 0 || linkGeneration == markerGeneration;
    }

    public enum Step {
        /** Pay {@code cost} for one owed layer ({@code paid += 1}); no new layer meanwhile. */
        PAY_DEBT,
        /** A debt remains but the buffer cannot pay: {@code ENERGY_DEBT}. */
        WAIT_FOR_ENERGY,
        /** Run one paid layer without a payment ({@code done += 1}); only while the system and physical mode are on. */
        USE_CREDIT,
        /** The credit waits: a disabled system breaks no block. */
        CREDIT_WAITS,
        /** Pay, run and count one new layer in one tick. */
        NORMAL,
        /** Counters are equal but the buffer cannot pay a new layer. */
        NO_ENERGY
    }

    public static Step next(long paid, long done, long energy, long cost, boolean breakingAllowed) {
        if (paid < 0 || done < 0 || energy < 0 || cost < 0) {
            throw new IllegalArgumentException("Counters, energy and cost are not negative");
        }
        if (done > paid) {
            return energy >= cost ? Step.PAY_DEBT : Step.WAIT_FOR_ENERGY;
        }
        if (paid > done) {
            return breakingAllowed ? Step.USE_CREDIT : Step.CREDIT_WAITS;
        }
        return energy >= cost ? Step.NORMAL : Step.NO_ENERGY;
    }
}
