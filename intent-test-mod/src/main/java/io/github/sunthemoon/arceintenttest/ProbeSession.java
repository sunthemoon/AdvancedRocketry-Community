package io.github.sunthemoon.arceintenttest;

/** Per-connection client lifecycle; monotonic time, finite lifetime and no scheduled sends. */
public final class ProbeSession {
    public static final long COOLDOWN_NANOS = 2_000_000_000L;
    public static final long CAPTURE_LIFETIME_NANOS = 120_000_000_000L;
    public static final int MAX_SENDS = 32;
    private boolean enabled;
    private int sent;
    private long last;

    public void enable() { enabled = true; } // Re-enabling never resets the connection budget.
    public void disable() { enabled = false; }
    public int sent() { return sent; }

    public void claim(long now, long capturedAt) {
        if (!enabled) { throw new IllegalStateException("Enable only in a disposable test world"); }
        if (now - capturedAt < 0 || now - capturedAt > CAPTURE_LIFETIME_NANOS) {
            throw new IllegalStateException("Capture expired; recapture the intended menu");
        }
        if (sent >= MAX_SENDS) { throw new IllegalStateException("Connection packet budget exhausted"); }
        if (sent != 0 && (now - last < 0 || now - last < COOLDOWN_NANOS)) {
            throw new IllegalStateException("Two-second fixture cooldown");
        }
        sent++;
        last = now;
    }
}
