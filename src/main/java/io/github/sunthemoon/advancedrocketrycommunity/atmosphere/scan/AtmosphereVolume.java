package io.github.sunthemoon.advancedrocketrycommunity.atmosphere.scan;

import java.util.Objects;
import java.util.Set;

/** Immutable sealed connected component suitable for bounded indexing. */
public record AtmosphereVolume(
        VolumeId id,
        Set<VolumePosition> cells,
        VolumeBounds bounds
) {
    public AtmosphereVolume {
        Objects.requireNonNull(id, "id");
        cells = Set.copyOf(cells);
        Objects.requireNonNull(bounds, "bounds");
        if (cells.isEmpty()) {
            throw new IllegalArgumentException("A sealed volume must contain cells");
        }
        if (!cells.stream().allMatch(bounds::contains)) {
            throw new IllegalArgumentException("Volume bounds do not contain every cell");
        }
        if (!VolumeIdentity.fromCells(cells).equals(id)) {
            throw new IllegalArgumentException("Volume ID does not match its cells");
        }
    }

    public static AtmosphereVolume fromSealedResult(VolumeScanResult result) {
        Objects.requireNonNull(result, "result");
        if (result.outcome() != VolumeScanOutcome.SEALED || result.cells().isEmpty()) {
            throw new IllegalArgumentException("Only a non-empty sealed result can become a volume");
        }
        return new AtmosphereVolume(
                VolumeIdentity.fromCells(result.cells()),
                result.cells(),
                result.bounds().orElseThrow()
        );
    }
}
