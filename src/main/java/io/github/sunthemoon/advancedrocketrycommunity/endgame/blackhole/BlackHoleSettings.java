package io.github.sunthemoon.advancedrocketrycommunity.endgame.blackhole;

/**
 * ADR-057 sections 4 and 5: the output percent (10..400, so at most 8,192 FE per tick per generator) and the burning
 * generators per owner (≤ 4) and on the server (≤ 64), read at each use.
 */
public record BlackHoleSettings(int energyPercent, int activePerOwner, int activeGlobal) {
    public static final int MIN_PERCENT = 10;
    public static final int MAX_PERCENT = 400;
    public static final int MAX_ACTIVE_PER_OWNER = 4;
    public static final int MAX_ACTIVE_GLOBAL = 64;
    public static final BlackHoleSettings DEFAULTS = new BlackHoleSettings(100, MAX_ACTIVE_PER_OWNER, MAX_ACTIVE_GLOBAL);

    public BlackHoleSettings {
        if (energyPercent < MIN_PERCENT || energyPercent > MAX_PERCENT || activePerOwner < 1
                || activePerOwner > MAX_ACTIVE_PER_OWNER || activeGlobal < 1 || activeGlobal > MAX_ACTIVE_GLOBAL) {
            throw new IllegalArgumentException("Black-hole generator settings are outside their bounds");
        }
    }
}
