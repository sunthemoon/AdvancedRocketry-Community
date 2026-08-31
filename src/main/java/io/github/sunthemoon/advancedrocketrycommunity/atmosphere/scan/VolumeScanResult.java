package io.github.sunthemoon.advancedrocketrycommunity.atmosphere.scan;

import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/** Immutable bounded snapshot of task state for indexing or diagnostics. */
public record VolumeScanResult(
        VolumePosition seed,
        VolumeScanOutcome outcome,
        Set<VolumePosition> cells,
        Optional<VolumeBounds> bounds,
        long totalInspections,
        int queuedCells,
        Optional<VolumePosition> pendingPosition
) {
    public VolumeScanResult {
        Objects.requireNonNull(seed, "seed");
        Objects.requireNonNull(outcome, "outcome");
        cells = Set.copyOf(cells);
        bounds = Objects.requireNonNull(bounds, "bounds");
        pendingPosition = Objects.requireNonNull(pendingPosition, "pendingPosition");
        if (totalInspections < 0L || queuedCells < 0) {
            throw new IllegalArgumentException("Scan metrics cannot be negative");
        }
        if (cells.isEmpty() != bounds.isEmpty()) {
            throw new IllegalArgumentException("Bounds must exist exactly when traversable cells exist");
        }
        if (outcome == VolumeScanOutcome.PENDING && pendingPosition.isEmpty()) {
            throw new IllegalArgumentException("A pending scan must identify its unloaded cell");
        }
        if (outcome != VolumeScanOutcome.PENDING && pendingPosition.isPresent()) {
            throw new IllegalArgumentException("Only a pending scan may retain an unloaded cell");
        }
    }
}
