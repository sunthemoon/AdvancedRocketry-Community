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

    // Conservative, phase-independent admission for four captures per Level per twenty ticks.
    // A window opened earlier by another capture can fit 24 within age 100; 21 is policy,
    // not an absolute native limit. Neither the quota nor the inclusive deadline is raised.
    static final int NATIVE_SLOT_LIMIT = RocketOrdinaryRetirement.ATTEMPT_TICKS / 20 * 4 + 1;
    static boolean fitsNativeWindow(int slots) {
        return slots > 0 && slots <= NATIVE_SLOT_LIMIT;
    }

    private SealedProofAdmission() { }
}
