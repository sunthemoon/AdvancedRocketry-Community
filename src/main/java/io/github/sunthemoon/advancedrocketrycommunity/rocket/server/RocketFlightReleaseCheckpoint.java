package io.github.sunthemoon.advancedrocketrycommunity.rocket.server;

/** Packaged-server-only checkpoints used to freeze an exact durable flight state. */
public enum RocketFlightReleaseCheckpoint {
    COUNTDOWN,
    ASCENT,
    TRANSIT_PREPARED,
    DESTINATION_SPAWNED,
    DESCENT,
    LANDED
}
