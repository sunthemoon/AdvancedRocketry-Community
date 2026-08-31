package io.github.sunthemoon.advancedrocketrycommunity.atmosphere.scan;

/** Read-only adapter queried by the pure scanner; implementations must not load chunks. */
@FunctionalInterface
public interface VolumeWorldView {
    CellObservation observe(VolumePosition position);
}
