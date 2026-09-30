package io.github.sunthemoon.advancedrocketrycommunity.satellite.scan;

/**
 * ADR-049 section 8 server limits. The defaults are also the hard maxima for the job counts and the read
 * budget; the cooldown can only be made longer.
 */
public record ScanSettings(int jobLimit, int cooldownTicks, int readsPerTick) {
    public static final int MAX_JOBS = 4;
    public static final int MIN_COOLDOWN_TICKS = 100;
    public static final int MAX_COOLDOWN_TICKS = 72_000;
    public static final int MAX_READS_PER_TICK = 16_384;
    public static final ScanSettings DEFAULTS = new ScanSettings(MAX_JOBS, MIN_COOLDOWN_TICKS, MAX_READS_PER_TICK);

    public ScanSettings {
        if (jobLimit < 1 || jobLimit > MAX_JOBS
                || cooldownTicks < MIN_COOLDOWN_TICKS || cooldownTicks > MAX_COOLDOWN_TICKS
                || readsPerTick < 1 || readsPerTick > MAX_READS_PER_TICK) {
            throw new IllegalArgumentException("Survey scan settings are outside their bounds");
        }
    }
}
