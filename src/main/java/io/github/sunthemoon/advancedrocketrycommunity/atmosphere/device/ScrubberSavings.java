package io.github.sunthemoon.advancedrocketrycommunity.atmosphere.device;

/** Finite scheduled-debit savings; oxygen availability and FE remain the vent's decision. */
public final class ScrubberSavings {
    public record Decision(int nextPhase, boolean skip) { }
    public static Decision debit(int phase, int usableScrubbers) {
        if (phase < 0 || phase > 3 || usableScrubbers < 0 || usableScrubbers > 6) {
            throw new IllegalArgumentException("Invalid scrubber debit input");
        }
        boolean skip = usableScrubbers >= 2 ? phase != 3 : usableScrubbers == 1 && phase % 2 == 0;
        return new Decision((phase + 1) % 4, skip);
    }
    private ScrubberSavings() { }
}
