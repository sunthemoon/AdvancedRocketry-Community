package io.github.sunthemoon.advancedrocketrycommunity.rocket.fuel;

public enum FuelLoaderStatus {
    UNCLAIMED("unclaimed"),
    IDLE("idle"),
    WAITING_FOR_ROCKET("waiting_for_rocket"),
    TRANSFERRING("transferring"),
    OUTPUT_READY("output_ready"),
    UNSUPPORTED_DATA("unsupported_data"),
    INVALID_DATA("invalid_data");

    private final String diagnosticKey;

    FuelLoaderStatus(String diagnosticKey) {
        this.diagnosticKey = diagnosticKey;
    }

    public String diagnosticKey() {
        return diagnosticKey;
    }
}
