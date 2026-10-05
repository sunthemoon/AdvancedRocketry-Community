package io.github.sunthemoon.advancedrocketrycommunity.machine.solar;

/** Pure single-collector accounting. Weather display rounding never feeds the FE calculation. */
public final class SolarGeneration {
    public static final int CAPACITY = 10_000;
    public static final int OUTPUT_PER_TICK = 1_000;
    public static final int MAX_CREDIT = 128;

    public enum Reason {
        GENERATING(0), DISABLED(1), BUFFER_FULL(2), SKY_BLOCKED(3), NOT_DAYLIGHT(4),
        CONTEXT_UNAVAILABLE(5), NO_IRRADIANCE(6), REPAIR_REQUIRED(7);
        private final int code;
        Reason(int code) { this.code = code; }
        public int code() { return code; }
        public static Reason fromCode(int code) {
            for (Reason reason : values()) { if (reason.code == code) { return reason; } }
            return REPAIR_REQUIRED;
        }
    }

    /** Context: 0 unavailable, 1 surface, 2 station, 3 missing-orbit fallback. Day: 0 no, 1 yes, 2 N/A. */
    public record Environment(boolean available, boolean sky, int day, int context,
                              double effectiveIntensity, int weatherPermille) {
        public static Environment unavailable() { return new Environment(false, false, 2, 0, 0, 1_000); }
        public boolean valid() {
            return available && context >= 1 && context <= 3 && day >= 0 && day <= 2
                    && (context == 1 ? day != 2 : day == 2)
                    && Double.isFinite(effectiveIntensity) && effectiveIntensity >= 0 && effectiveIntensity <= 16
                    && weatherPermille >= 250 && weatherPermille <= 1_000;
        }
    }

    public record Step(int energy, int credit, Reason reason, Environment environment) { }

    public static Step tick(int energy, int multiplier, boolean enabled, boolean repair, Environment environment) {
        requireEnergy(energy);
        if (repair) { return stopped(energy, Reason.REPAIR_REQUIRED); }
        if (!enabled) { return stopped(energy, Reason.DISABLED); }
        if (environment == null || !environment.valid() || multiplier < 1 || multiplier > 4) {
            return stopped(energy, Reason.CONTEXT_UNAVAILABLE);
        }
        Reason reason;
        int credit = 0;
        if (!environment.sky()) { reason = Reason.SKY_BLOCKED; }
        else if (environment.day() == 0) { reason = Reason.NOT_DAYLIGHT; }
        else {
            int request = (int) Math.floor(2.0D * multiplier * environment.effectiveIntensity());
            if (request == 0) { reason = Reason.NO_IRRADIANCE; }
            else if (energy == CAPACITY) { reason = Reason.BUFFER_FULL; }
            else { reason = Reason.GENERATING; credit = Math.min(request, CAPACITY - energy); }
        }
        return new Step(energy + credit, credit, reason, environment);
    }

    private static Step stopped(int energy, Reason reason) {
        return new Step(energy, 0, reason, Environment.unavailable());
    }

    public static double attenuation(double rain, double thunder) {
        if (!Double.isFinite(rain) || !Double.isFinite(thunder)) {
            throw new IllegalArgumentException("Invalid solar weather sample");
        }
        return (1.0D - 0.5D * clamp(rain)) * (1.0D - 0.5D * clamp(thunder));
    }

    public static int weatherPermille(double attenuation) {
        if (!Double.isFinite(attenuation) || attenuation < 0.25D || attenuation > 1.0D) {
            throw new IllegalArgumentException("Invalid solar attenuation");
        }
        return (int) Math.floor(1_000.0D * attenuation + 0.5D);
    }

    /** Direct UP reach of the face; this is not a propagated-brightness or motion-heightmap test. */
    public static boolean validCutoff(int cutoff, int minimum, int maximum) {
        return minimum < maximum && (cutoff == Integer.MIN_VALUE || cutoff >= minimum && cutoff <= maximum);
    }
    public static boolean exposed(int y, int cutoff, int minimum, int maximum) {
        if (y < minimum || y >= maximum || !validCutoff(cutoff, minimum, maximum)) {
            throw new IllegalArgumentException("Invalid solar column sample");
        }
        return (long) y + 1 >= (long) cutoff;
    }
    public static void requireEnergy(int energy) {
        if (energy < 0 || energy > CAPACITY) { throw new IllegalArgumentException("Invalid solar energy"); }
    }
    private static double clamp(double value) { return Math.max(0.0D, Math.min(1.0D, value)); }
    private SolarGeneration() { }
}
