package io.github.sunthemoon.advancedrocketrycommunity.atmosphere.instrument;

import java.util.Objects;

/** Scalar measurement only: no world, player, held data or volume identity is retained. */
record SealDetectorReading(Outcome outcome, Boundary boundary, Supply supply) {
    enum Outcome { READING, DISABLED, UNAVAILABLE }
    enum Boundary { SEALED, OPEN, UNAVAILABLE }
    enum Supply { SUPPLIED, NOT_KNOWN_SUPPLIED, PENDING, UNAVAILABLE }

    SealDetectorReading {
        Objects.requireNonNull(outcome, "outcome");
        Objects.requireNonNull(boundary, "boundary");
        Objects.requireNonNull(supply, "supply");
        if (outcome == Outcome.READING && boundary == Boundary.UNAVAILABLE) {
            throw new IllegalArgumentException("A reading requires a known boundary");
        }
        if (outcome != Outcome.READING && (boundary != Boundary.UNAVAILABLE || supply != Supply.UNAVAILABLE)) {
            throw new IllegalArgumentException("A non-reading has no measured fields");
        }
    }

    static SealDetectorReading disabled() {
        return new SealDetectorReading(Outcome.DISABLED, Boundary.UNAVAILABLE, Supply.UNAVAILABLE);
    }

    static SealDetectorReading unavailable() {
        return new SealDetectorReading(Outcome.UNAVAILABLE, Boundary.UNAVAILABLE, Supply.UNAVAILABLE);
    }

    static SealDetectorReading measured(Boundary boundary, Supply supply) {
        return new SealDetectorReading(Outcome.READING, boundary, supply);
    }
}
