package io.github.sunthemoon.advancedrocketrycommunity.rocket.flight;

public enum RocketTransferRecoveryAction {
    KEEP_SOURCE,
    KEEP_DESTINATION,
    REMOVE_DESTINATION_KEEP_SOURCE,
    REMOVE_SOURCE_KEEP_DESTINATION,
    REBUILD_SOURCE,
    REBUILD_DESTINATION
}
