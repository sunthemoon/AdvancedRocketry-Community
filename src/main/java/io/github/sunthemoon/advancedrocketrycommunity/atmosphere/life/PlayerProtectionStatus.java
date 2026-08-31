package io.github.sunthemoon.advancedrocketrycommunity.atmosphere.life;

/** Bounded status synchronized to the client for display only. */
public enum PlayerProtectionStatus {
    EXEMPT(true, "exempt"),
    BREATHABLE_ENVIRONMENT(true, "breathable_environment"),
    BREATHABLE_VOLUME(true, "breathable_volume"),
    SUIT_OXYGEN(true, "suit_oxygen"),
    EXPOSED(false, "exposed"),
    PARTIAL_SUIT(false, "partial_suit"),
    OXYGEN_EMPTY(false, "oxygen_empty"),
    VOLUME_PENDING(false, "volume_pending");

    private final boolean protectedFromVacuum;
    private final String diagnosticKey;

    PlayerProtectionStatus(boolean protectedFromVacuum, String diagnosticKey) {
        this.protectedFromVacuum = protectedFromVacuum;
        this.diagnosticKey = diagnosticKey;
    }

    public boolean protectedFromVacuum() {
        return protectedFromVacuum;
    }

    public String diagnosticKey() {
        return diagnosticKey;
    }
}
