package io.github.sunthemoon.advancedrocketrycommunity.progression.classic.firstevent;

/**
 * W44 section 3.3, step 4: the election disposition refreshed before each potentially first action. ALLOW requires a
 * KNOWN EMPTY source, an EMPTY RAM slot, a validated absent receipt and no hold. A known occurred source or a permanent
 * hold gives NOT_FIRST. Anything that is possibly EMPTY without proof, or an unavailable progression store, gives
 * DEFER. Nothing unavailable ever yields ALLOW.
 */
public enum FirstEventDisposition {
    ALLOW,
    NOT_FIRST,
    DEFER
}
