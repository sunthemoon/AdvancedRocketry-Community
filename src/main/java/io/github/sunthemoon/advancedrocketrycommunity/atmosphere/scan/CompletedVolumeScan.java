package io.github.sunthemoon.advancedrocketrycommunity.atmosphere.scan;

import java.util.Objects;
import java.util.Optional;
import java.util.Set;

public record CompletedVolumeScan(
        Set<VolumePosition> seeds,
        VolumeScanResult result,
        Optional<AtmosphereVolume> sealedVolume
) {
    public CompletedVolumeScan {
        seeds = Set.copyOf(seeds);
        Objects.requireNonNull(result, "result");
        sealedVolume = Objects.requireNonNull(sealedVolume, "sealedVolume");
        if (seeds.isEmpty()) {
            throw new IllegalArgumentException("A completed scan must retain at least one seed");
        }
        if ((result.outcome() == VolumeScanOutcome.SEALED && !result.cells().isEmpty())
                != sealedVolume.isPresent()) {
            throw new IllegalArgumentException("Only a non-empty sealed result has a volume");
        }
    }
}
