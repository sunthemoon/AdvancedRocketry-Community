package io.github.sunthemoon.advancedrocketrycommunity.machine.electrolyzer;

/** Stable status codes used by the pure process model and bounded menu sync. */
public enum ElectrolyzerStatus {
    IDLE(0),
    RUNNING(1),
    REDSTONE_DISABLED(2),
    NO_RECIPE(3),
    NEEDS_WATER(4),
    OUTPUT_BLOCKED(5),
    NEEDS_ENERGY(6),
    INVALID_RECIPE(7),
    UNSUPPORTED_DATA(8);

    private final int networkId;

    ElectrolyzerStatus(int networkId) {
        this.networkId = networkId;
    }

    public int networkId() {
        return networkId;
    }

    public String translationKey() {
        return "status.advancedrocketrycommunity.electrolyzer." + name().toLowerCase(java.util.Locale.ROOT);
    }

    public static ElectrolyzerStatus fromNetworkId(int networkId) {
        for (ElectrolyzerStatus status : values()) {
            if (status.networkId == networkId) {
                return status;
            }
        }
        return INVALID_RECIPE;
    }
}
