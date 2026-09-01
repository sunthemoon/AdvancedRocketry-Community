package io.github.sunthemoon.advancedrocketrycommunity.rocket.transfer;

import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketStructureSnapshot;
import java.util.Objects;
import java.util.Optional;

/** Bounded fixed-pad probe result with enough counters for audit logging. */
public record RocketLandingPadSelection(
        RocketStructureSnapshot destinationSnapshot,
        int candidatesChecked,
        int chunksLoaded,
        String detail
) {
    public RocketLandingPadSelection {
        if (candidatesChecked < 0 || chunksLoaded < 0) {
            throw new IllegalArgumentException("Landing pad counters cannot be negative");
        }
        detail = Objects.requireNonNull(detail, "detail");
    }

    public static RocketLandingPadSelection success(
            RocketStructureSnapshot snapshot,
            int candidatesChecked,
            int chunksLoaded
    ) {
        return new RocketLandingPadSelection(
                Objects.requireNonNull(snapshot, "snapshot"),
                candidatesChecked,
                chunksLoaded,
                "selected"
        );
    }

    public static RocketLandingPadSelection failure(
            int candidatesChecked,
            int chunksLoaded,
            String detail
    ) {
        return new RocketLandingPadSelection(null, candidatesChecked, chunksLoaded, detail);
    }

    public Optional<RocketStructureSnapshot> snapshot() {
        return Optional.ofNullable(destinationSnapshot);
    }

    public boolean success() {
        return destinationSnapshot != null;
    }
}
