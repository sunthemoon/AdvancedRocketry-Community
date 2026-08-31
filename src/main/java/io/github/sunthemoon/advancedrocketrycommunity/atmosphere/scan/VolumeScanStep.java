package io.github.sunthemoon.advancedrocketrycommunity.atmosphere.scan;

import java.util.Objects;
import java.util.Set;

/** Bounded delta produced by one task advance. */
public record VolumeScanStep(
        int inspections,
        Set<VolumePosition> newCells,
        VolumeScanOutcome outcome
) {
    public VolumeScanStep {
        if (inspections < 0) {
            throw new IllegalArgumentException("Step inspections cannot be negative");
        }
        newCells = Set.copyOf(newCells);
        Objects.requireNonNull(outcome, "outcome");
    }
}
