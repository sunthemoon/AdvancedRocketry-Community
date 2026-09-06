package io.github.sunthemoon.advancedrocketrycommunity.rocket.server;

import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFlightState;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketTransferPhase;

/** Packaged-server-only checkpoints used to freeze an exact durable flight state. */
public enum RocketFlightReleaseCheckpoint {
    COUNTDOWN,
    ASCENT,
    TRANSIT_PREPARED,
    DESTINATION_SPAWNED,
    PASSENGERS_TRANSFERRED,
    SOURCE_REMOVED,
    DESCENT,
    LANDED;

    boolean reached(RocketTransferPhase phase, RocketFlightState state) {
        return switch (this) {
            case COUNTDOWN -> phase == RocketTransferPhase.PREPARED && state == RocketFlightState.COUNTDOWN;
            case ASCENT -> phase == RocketTransferPhase.PREPARED && state == RocketFlightState.ASCENT;
            case TRANSIT_PREPARED -> phase == RocketTransferPhase.PREPARED && state == RocketFlightState.TRANSIT;
            case DESTINATION_SPAWNED -> phase == RocketTransferPhase.DESTINATION_SPAWNED;
            case PASSENGERS_TRANSFERRED -> phase == RocketTransferPhase.PASSENGERS_TRANSFERRED;
            case SOURCE_REMOVED -> phase == RocketTransferPhase.SOURCE_REMOVED;
            case DESCENT -> phase == RocketTransferPhase.COMMITTED && state == RocketFlightState.DESCENT;
            case LANDED -> phase == RocketTransferPhase.COMMITTED && state == RocketFlightState.LANDED;
        };
    }
}
