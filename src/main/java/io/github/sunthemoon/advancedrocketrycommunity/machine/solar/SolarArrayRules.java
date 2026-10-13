package io.github.sunthemoon.advancedrocketrycommunity.machine.solar;

/** ADR-065 section 9 array profile over the shared {@link SolarGeneration} rule; no separate irradiance code. */
public final class SolarArrayRules {
    public static final int CAPACITY = 20_000;
    /** One push/pull allowance per tick, shared by both plugs and all their faces. */
    public static final int OUTPUT_PER_TICK = 1_000;
    /** 2 FE x 63 collectors x multiplier 4 x intensity 16, before buffer room. */
    public static final int MAX_CREDIT = 2 * SolarGeneration.MAX_COLLECTORS * 4 * 16;

    /** Runtime formation state shown in the menu. Unknown codes are UNAVAILABLE. */
    public enum Structure {
        PENDING(0), FORMED(1), UNFORMED(2), UNLOADED(3), NO_COLLECTORS(4), CONFLICT(5), UNAVAILABLE(6);
        private final int code;
        Structure(int code) { this.code = code; }
        public int code() { return code; }
        public static Structure fromCode(int code) {
            for (Structure structure : values()) { if (structure.code == code) { return structure; } }
            return UNAVAILABLE;
        }
        static Structure of(SolarArrayLayout.Status status) {
            return switch (status) {
                case FORMED -> FORMED;
                case UNLOADED -> UNLOADED;
                case MISMATCH -> UNFORMED;
                case NO_COLLECTORS -> NO_COLLECTORS;
            };
        }
    }

    /**
     * An array that is not formed reads no environment and stops with CONTEXT_UNAVAILABLE (or repair/disable first);
     * the structure field says why. A formed array uses the shared rule with its collector count and 20,000 FE buffer.
     */
    public static SolarGeneration.Step step(int energy, Structure structure, int collectors, int multiplier,
                                            boolean enabled, boolean repair, SolarGeneration.Environment environment) {
        boolean formed = structure == Structure.FORMED && collectors >= 1;
        return SolarGeneration.tick(energy, CAPACITY, formed ? collectors : 1, multiplier, enabled, repair,
                formed ? environment : null);
    }

    private SolarArrayRules() { }
}
