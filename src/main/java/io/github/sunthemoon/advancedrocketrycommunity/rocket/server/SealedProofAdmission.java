package io.github.sunthemoon.advancedrocketrycommunity.rocket.server;

/** Scheduling admission, not a new saved footprint limit or a native quota. */
final class SealedProofAdmission {
    // A sealed census is non-resumable and may need the complete 256-inspection tick.
    // Reserve that next-tick service before starting; other sealed plans remain unstarted.
    static boolean inspectionRoom(int activeSealedAttempts) { return activeSealedAttempts == 0; }
    static boolean canStart(int activeSealedAttempts, int ordinaryAttempts) {
        return inspectionRoom(activeSealedAttempts) && ordinaryAttempts == 0;
    }
    // The full-width sealed plan can need the complete ordinary capture window.
    // Wait for existing ordinary attempts, then reserve against new ones through
    // their existing shared-capacity admission seam. No running attempt is revoked.
    static int nativeReservations(int activeSealedAttempts) {
        return activeSealedAttempts > 0 ? RocketOrdinaryRetirement.MAX_ATTEMPTS : 0;
    }
    static boolean priorityPending(int activeSealedAttempts, long now, long servicedTick) {
        return activeSealedAttempts > 0 && now != servicedTick;
    }

    // Existing ordinary quota: four captures per Level per twenty ticks. Even with
    // immediate IO, a plan starting with capture 0 cannot finish more than 21 slots
    // by its inclusive age-100 deadline. Keep wider plans intact and unstarted.
    static boolean fitsNativeWindow(int slots) {
        return slots > 0 && slots <= RocketOrdinaryRetirement.ATTEMPT_TICKS / 20 * 4 + 1;
    }

    private SealedProofAdmission() { }
}
