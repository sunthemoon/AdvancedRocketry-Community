package io.github.sunthemoon.advancedrocketrycommunity.rocket.flight;

public enum RocketFlightState {
    ASSEMBLED(0),
    FUELED(1),
    COUNTDOWN(2),
    ASCENT(3),
    TRANSIT(4),
    DESCENT(5),
    LANDED(6),
    FAILED_RECOVERABLE(7),
    DISASSEMBLED(8);

    private final int networkId;

    RocketFlightState(int networkId) {
        this.networkId = networkId;
    }

    public int networkId() {
        return networkId;
    }

    public boolean inMotion() {
        return this == COUNTDOWN || this == ASCENT || this == TRANSIT || this == DESCENT;
    }

    public boolean acceptsFuel() {
        return this == ASSEMBLED || this == FUELED || this == LANDED;
    }

    public static RocketFlightState fromNetworkId(int networkId) {
        for (RocketFlightState state : values()) {
            if (state.networkId == networkId) {
                return state;
            }
        }
        throw new IllegalArgumentException("Unknown rocket flight state id " + networkId);
    }
}
