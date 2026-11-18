package io.github.sunthemoon.advancedrocketrycommunity.gametest;

import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketTransferRecoveryReport;
import java.util.function.Supplier;

/**
 * W381: the transfer-recovery fixture's source-side wait and its failure text.
 *
 * <p>A source-side recovery reports RECOVERED only after the survivor's entity slot, and each discarded copy's slot, are
 * forced to disk and read back. Each slot waits for its Level's next force batch (up to 20 ticks, the full 20 when that
 * Level had no batch in the last 20 ticks) and then for the force and the read-back, which run on IO threads in
 * wall-clock time. The fixture's earlier bounds of 20 and 40 retries counted only the batch waits of a Level whose
 * window was already open, so a first batch or a slow disk ran past them while the proof was still in progress.
 *
 * <p>The wait now follows the product: it continues only while the fixture manager's return is on its first attempt
 * with no recorded wait or failure, for at most the product's own allowance for one attempt. Anything else ends the
 * wait at once, and the unchanged RECOVERED assertion then fails with what was observed.
 */
final class RocketTransferRecoveryWait {
    /** The retries that the fixture allowed before W381, kept for the failure text and the pinning test. */
    static final int FORMER_SOURCE_ONLY_ATTEMPTS = 20, FORMER_BOTH_ATTEMPTS = 40;

    private final int attemptTicks;
    private final long startedTick;
    private final long startedNanos;
    private int attempts;
    private long lastTick;
    private long lastNanos;

    /** {@code attemptTicks} is the product's allowance for one proof attempt ({@code RocketOrdinaryRetirement.ATTEMPT_TICKS}). */
    RocketTransferRecoveryWait(int attemptTicks, long tick, long nanos) {
        this.attemptTicks = attemptTicks; startedTick = lastTick = tick; startedNanos = lastNanos = nanos;
    }

    /** The return is still what the fixture waits for: held by the manager, on its first attempt, with no wait or failure recorded. */
    static boolean firstCleanAttempt(boolean pending, int attempts, String reason) {
        // BUSY is the barrier's capture budget asking for the next tick; it starts no attempt and is no proof outcome.
        return pending && attempts <= 1 && (reason == null || reason.equals("BUSY"));
    }

    /**
     * Records one recovery call and says whether the fixture calls again on the next tick. {@code attempt} counts the
     * calls of this wait from 1, one per tick.
     */
    boolean retry(RocketTransferRecoveryReport.Status status, int attempt, boolean firstCleanAttempt, long tick, long nanos) {
        attempts = attempt; lastTick = tick; lastNanos = nanos;
        return status == RocketTransferRecoveryReport.Status.RETRY_LATER && firstCleanAttempt && attempt <= attemptTicks;
    }

    /** Failure text only: {@code observation} is not read on a passing wait. */
    String describe(Supplier<String> observation) {
        return "attempts=" + attempts + "/" + attemptTicks + " ticks=" + (lastTick - startedTick)
                + " wall_ms=" + (lastNanos - startedNanos) / 1_000_000L + " " + observation.get();
    }
}
