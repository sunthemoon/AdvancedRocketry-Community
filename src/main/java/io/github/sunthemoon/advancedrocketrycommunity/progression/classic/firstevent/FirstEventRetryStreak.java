package io.github.sunthemoon.advancedrocketrycommunity.progression.classic.firstevent;

/**
 * W44 N2: the retry cadence and diagnostics of one automatic retry. The first failure of a streak is logged once with
 * its stack trace, later failures only count, and the first success after failures logs one recovery line. The
 * receipt barrier retries every 200 ticks; the station acknowledgement backs off 200, 400, 800, 1,600, 3,200, then
 * 6,000 ticks. Neither has an expiry, and neither changes authority.
 */
final class FirstEventRetryStreak {
    private final long initial;
    private final long maximum;
    private int failures;
    private long interval;
    private long next;

    FirstEventRetryStreak(long initial, long maximum) {
        if (initial <= 0L || maximum < initial) {
            throw new IllegalArgumentException("Invalid retry interval");
        }
        this.initial = initial;
        this.maximum = maximum;
        this.interval = initial;
    }

    static FirstEventRetryStreak fixed(long ticks) {
        return new FirstEventRetryStreak(ticks, ticks);
    }

    boolean due(long now) {
        return now >= next;
    }

    /** Records one failure; true only for the first failure of a streak, which is logged with its stack trace. */
    boolean failed(long now) {
        failures++;
        next = now + interval;
        interval = Math.min(interval * 2L, maximum);
        return failures == 1;
    }

    /** Ends a streak; the number of failures it held, so a positive value logs one recovery line. */
    int succeeded() {
        int ended = failures;
        failures = 0;
        interval = initial;
        next = 0L;
        return ended;
    }

    /** Makes the next attempt due now, without ending the streak. */
    void wake() {
        next = 0L;
    }

    int failures() {
        return failures;
    }

    /** The tick of the next attempt, or 0 when it is due now. */
    long next() {
        return next;
    }
}
