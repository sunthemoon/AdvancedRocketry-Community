package io.github.sunthemoon.advancedrocketrycommunity.progression.classic.firstevent;

/** Bounded availability at the existing disposition-source seam; never an election or acknowledgement. */
public final class FirstEventBenchmarkHold implements AutoCloseable {
    public static final int DEFER_TICKS = 20;
    private final long armedTick;
    private final long armedNanos = System.nanoTime();
    private long firstTick = -1, lastTick = -1;
    private int visits, ticks;
    private boolean released;

    FirstEventBenchmarkHold(long tick) { armedTick = tick; }

    FirstEventDisposition disposition(FirstEventKind kind, FirstEventDisposition actual) {
        return !released && kind == FirstEventKind.FIRST_MOON_LANDING && actual == FirstEventDisposition.ALLOW
                ? FirstEventDisposition.DEFER : actual;
    }

    void deferred(long tick) {
        if (released) { return; }
        if (firstTick < 0) { firstTick = tick; }
        if (tick != lastTick) { ticks++; lastTick = tick; }
        visits++;
    }

    void tick(long tick) {
        if (released) { return; }
        if (tick - armedTick >= 120 || System.nanoTime() - armedNanos >= 10_000_000_000L) {
            throw new IllegalStateException("Benchmark first-event availability hold exceeded its bound");
        }
        if (!released && firstTick >= 0 && tick - firstTick >= DEFER_TICKS) { close(); }
    }

    public boolean released() { return released; }
    public int deferredVisits() { return visits; }
    public int deferredTicks() { return ticks; }

    @Override public void close() { released = true; }
}
