package io.github.sunthemoon.advancedrocketrycommunity.rocket.flight;

public enum RocketFlightAction {
    LAUNCH(0),
    CANCEL(1);

    private final int networkId;

    RocketFlightAction(int networkId) {
        this.networkId = networkId;
    }

    public int networkId() {
        return networkId;
    }

    public static RocketFlightAction fromNetworkId(int networkId) {
        for (RocketFlightAction action : values()) {
            if (action.networkId == networkId) {
                return action;
            }
        }
        throw new IllegalArgumentException("Unknown rocket flight action id " + networkId);
    }
}
