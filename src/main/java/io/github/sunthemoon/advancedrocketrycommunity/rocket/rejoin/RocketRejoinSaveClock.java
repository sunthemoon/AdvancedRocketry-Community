package io.github.sunthemoon.advancedrocketrycommunity.rocket.rejoin;

/** One global prompt-save cadence, not one save-all per passenger. */
final class RocketRejoinSaveClock {
    private long last = Long.MIN_VALUE;
    boolean due(long now, boolean placed, boolean reading) {
        return placed && !reading && (last == Long.MIN_VALUE || now - last >= 100);
    }
    void saved(long now) { last = now; }
}
