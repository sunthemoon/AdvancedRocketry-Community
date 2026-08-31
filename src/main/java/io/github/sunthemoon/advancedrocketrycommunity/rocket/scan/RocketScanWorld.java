package io.github.sunthemoon.advancedrocketrycommunity.rocket.scan;

import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketPosition;

@FunctionalInterface
public interface RocketScanWorld {
    /** Observe one position only when its chunk is already loaded. */
    RocketScanObservation observe(RocketPosition absolutePosition);
}
