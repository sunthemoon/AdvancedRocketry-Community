package io.github.sunthemoon.advancedrocketrycommunity.endgame.transit;

import io.github.sunthemoon.advancedrocketrycommunity.endgame.model.EndgameLimits;

/**
 * ADR-054 section 11 limits: transit records on the server (live records and stubs, plus the live outbox entries the
 * server knows of) and per owner (live records and that owner's outbox entries). COMMON values that can only lower
 * the fixed maxima of 256 and 32.
 */
public record TransitLimits(int records, int perOwner) {
    public static final int MAX_PER_OWNER = 32;
    public static final TransitLimits DEFAULTS = new TransitLimits(EndgameLimits.MAX_TRANSIT_RECORDS, MAX_PER_OWNER);

    public TransitLimits {
        if (records < 1 || records > EndgameLimits.MAX_TRANSIT_RECORDS) {
            throw new IllegalArgumentException("Transit records are outside 1.." + EndgameLimits.MAX_TRANSIT_RECORDS);
        }
        if (perOwner < 1 || perOwner > MAX_PER_OWNER) {
            throw new IllegalArgumentException("Transit records per owner are outside 1.." + MAX_PER_OWNER);
        }
    }
}
