package io.github.sunthemoon.advancedrocketrycommunity.atmosphere.scan;

import java.util.List;

/** Loader-independent integer cell used by the pure atmosphere scanner. */
public record VolumePosition(int x, int y, int z) {
    public VolumePosition offset(int deltaX, int deltaY, int deltaZ) {
        return new VolumePosition(
                Math.addExact(x, deltaX),
                Math.addExact(y, deltaY),
                Math.addExact(z, deltaZ)
        );
    }

    public List<VolumePosition> neighbors() {
        return List.of(
                offset(1, 0, 0),
                offset(-1, 0, 0),
                offset(0, 1, 0),
                offset(0, -1, 0),
                offset(0, 0, 1),
                offset(0, 0, -1)
        );
    }
}
