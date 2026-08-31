package io.github.sunthemoon.advancedrocketrycommunity.atmosphere.scan;

/** Finite public state of a sealed-volume scan. */
public enum VolumeScanOutcome {
    SCANNING(false, "scanning"),
    SEALED(true, "sealed"),
    OPEN(true, "open"),
    TOO_LARGE(true, "too_large"),
    PENDING(false, "pending_unloaded_chunk"),
    CANCELLED(true, "cancelled");

    private final boolean terminal;
    private final String diagnosticKey;

    VolumeScanOutcome(boolean terminal, String diagnosticKey) {
        this.terminal = terminal;
        this.diagnosticKey = diagnosticKey;
    }

    public boolean terminal() {
        return terminal;
    }

    public String diagnosticKey() {
        return diagnosticKey;
    }
}
