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
    BUSY("busy"),
    CANCELLED("cancelled"),
    INVALID_DATA("invalid_data"),
    UNSUPPORTED_DATA("unsupported_data");

    private final String diagnosticKey;

    VentOperatingStatus(String diagnosticKey) {
        this.diagnosticKey = diagnosticKey;
    }

    public String diagnosticKey() {
        return diagnosticKey;
    }
}
