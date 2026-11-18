package io.github.sunthemoon.advancedrocketrycommunity.gametest;

import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketTransferRecoveryReport;
import java.util.function.Supplier;

/**
 * W381: the transfer-recovery fixture's bounded source-side wait and its failure text. The bounds are the fixture's
 * existing ones; this class adds only what a wait that ran out observed, read once and only then.
 */
final class RocketTransferRecoveryWait {
    /** One retry per tick after the first call: the source-only case's bound and the two-slot case's bound. */
    static final int SOURCE_ONLY_ATTEMPTS = 20, BOTH_ATTEMPTS = 40;

    private final long startedTick;
    private final long startedNanos;
    private int attempts;
    private long lastTick;
    private long lastNanos;

    RocketTransferRecoveryWait(long tick, long nanos) { startedTick = lastTick = tick; startedNanos = lastNanos = nanos; }

    /** Records one recovery call and says whether the fixture retries on the next tick. */
    boolean retry(RocketTransferRecoveryReport.Status status, int attempt, int bound, long tick, long nanos) {
        attempts = attempt; lastTick = tick; lastNanos = nanos;
        return status == RocketTransferRecoveryReport.Status.RETRY_LATER && attempt < bound;
    }

    /** Failure text only: {@code observation} is not read on a passing wait. */
    String describe(int bound, Supplier<String> observation) {
        return "attempts=" + attempts + "/" + bound + " ticks=" + (lastTick - startedTick)
                + " wall_ms=" + (lastNanos - startedNanos) / 1_000_000L + " " + observation.get();
    }
}
