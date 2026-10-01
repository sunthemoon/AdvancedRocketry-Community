package io.github.sunthemoon.advancedrocketrycommunity.endgame.gravity;

/**
 * ADR-058 section 4 index caps as COMMON values that can only be lowered (ADR-054 section 1): fields per chunk and
 * per owner in one chunk ({@code FIELD_DENSITY}), active fields per owner, per Level and on the server
 * ({@code ACTIVE_LIMIT}).
 */
public record GravityFieldLimits(int perChunk, int perOwnerPerChunk, int activePerOwner, int activePerLevel,
                                 int activeGlobal) {
    public static final int MAX_PER_CHUNK = 16;
    public static final int MAX_PER_OWNER_PER_CHUNK = 4;
    public static final int MAX_ACTIVE_PER_OWNER = 8;
    public static final int MAX_ACTIVE_PER_LEVEL = 256;
    public static final int MAX_ACTIVE_GLOBAL = 1024;

    public static final GravityFieldLimits DEFAULTS = new GravityFieldLimits(MAX_PER_CHUNK, MAX_PER_OWNER_PER_CHUNK,
            MAX_ACTIVE_PER_OWNER, MAX_ACTIVE_PER_LEVEL, MAX_ACTIVE_GLOBAL);

    public GravityFieldLimits {
        bounded(perChunk, MAX_PER_CHUNK, "fields per chunk");
        bounded(perOwnerPerChunk, MAX_PER_OWNER_PER_CHUNK, "fields per owner and chunk");
        bounded(activePerOwner, MAX_ACTIVE_PER_OWNER, "active fields per owner");
        bounded(activePerLevel, MAX_ACTIVE_PER_LEVEL, "active fields per Level");
        bounded(activeGlobal, MAX_ACTIVE_GLOBAL, "active fields");
    }

    private static void bounded(int value, int max, String name) {
        if (value < 1 || value > max) {
            throw new IllegalArgumentException("Gravity " + name + " is outside 1.." + max);
        }
    }
}
