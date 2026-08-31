package io.github.sunthemoon.advancedrocketrycommunity.atmosphere.life;

public enum VentOperatingStatus {
    SCANNING("scanning"),
    ACTIVE("active"),
    STANDBY("standby_shared_volume"),
    NO_POWER("no_power"),
    NO_OXYGEN("no_oxygen"),
    OPEN("open"),
    TOO_LARGE("too_large"),
    PENDING("pending_unloaded_chunk"),
    CANCELLED("cancelled");

    private final String diagnosticKey;

    VentOperatingStatus(String diagnosticKey) {
        this.diagnosticKey = diagnosticKey;
    }

    public String diagnosticKey() {
        return diagnosticKey;
    }
}
