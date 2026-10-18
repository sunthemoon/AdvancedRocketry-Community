package io.github.sunthemoon.advancedrocketrycommunity.rocket.transfer;

/**
 * W90b (Root decision R22 amendment 3): the destination warm-up's operator settings. Each can only lower a fixed safety
 * ceiling. A warm-up captures the settings at its allocation, so a change applies to the next allocation and never
 * changes a live warm-up's limits or deadline.
 */
public record RocketWarmupSettings(int globalCap, int perPlayer, int lifetimeTicks) {
    /** The global cap's ceiling: eight fixed pads per surface Level and the eight-flight reference workload. */
    public static final int MAX_GLOBAL = 8;
    public static final int MAX_PER_PLAYER = 2;
    /** The lifetime's ceiling, also the warm-up ticket type's vanilla lifespan (the backstop). */
    public static final int MAX_LIFETIME_TICKS = 600;
    public static final int MIN_LIFETIME_TICKS = 200;
    public static final RocketWarmupSettings DEFAULTS = new RocketWarmupSettings(MAX_GLOBAL, MAX_PER_PLAYER, MAX_LIFETIME_TICKS);

    public RocketWarmupSettings {
        if (globalCap < 1 || globalCap > MAX_GLOBAL || perPlayer < 1 || perPlayer > MAX_PER_PLAYER
                || lifetimeTicks < MIN_LIFETIME_TICKS || lifetimeTicks > MAX_LIFETIME_TICKS) {
            throw new IllegalArgumentException("Destination warm-up settings are outside their fixed ceilings");
        }
    }
}
