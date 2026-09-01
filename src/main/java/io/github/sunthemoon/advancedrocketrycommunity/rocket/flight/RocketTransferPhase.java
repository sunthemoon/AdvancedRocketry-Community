package io.github.sunthemoon.advancedrocketrycommunity.rocket.flight;

public enum RocketTransferPhase {
    PREPARED(false),
    DESTINATION_SPAWNED(true),
    PASSENGERS_TRANSFERRED(true),
    SOURCE_REMOVED(true),
    COMMITTED(true);

    private final boolean destinationAuthoritative;

    RocketTransferPhase(boolean destinationAuthoritative) {
        this.destinationAuthoritative = destinationAuthoritative;
    }

    public boolean destinationAuthoritative() {
        return destinationAuthoritative;
    }
}
