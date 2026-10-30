package io.github.sunthemoon.advancedrocketrycommunity.rocket.transfer;

import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketStructureSnapshot;
import java.util.Objects;
import java.util.Optional;
import java.util.List;
import java.util.Set;
import net.minecraft.world.level.ChunkPos;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFlightLimits;

/** Bounded fixed-pad probe result with enough counters for audit logging. */
public record RocketLandingPadSelection(
        RocketStructureSnapshot destinationSnapshot,
        int candidatesChecked,
        int chunksLoaded,
        String detail,
        List<ChunkPos> terrainColumns,
        int terrainStep
) {
    public RocketLandingPadSelection(RocketStructureSnapshot destinationSnapshot, int candidatesChecked,
            int chunksLoaded, String detail) {
        this(destinationSnapshot, candidatesChecked, chunksLoaded, detail, List.of(), 0);
    }

    public RocketLandingPadSelection {
        if (candidatesChecked < 0 || chunksLoaded < 0) {
            throw new IllegalArgumentException("Landing pad counters cannot be negative");
        }
        detail = Objects.requireNonNull(detail, "detail");
        terrainColumns = List.copyOf(terrainColumns);
        if (terrainColumns.size() > RocketFlightLimits.MAX_LANDING_CHUNKS || terrainStep < -1
                || terrainStep >= RocketFlightLimits.MAX_LANDING_PAD_CANDIDATES
                || !terrainColumns.isEmpty() && (destinationSnapshot != null
                || !RocketLandingPadSelector.TERRAIN_LOADING.equals(detail))) {
            throw new IllegalArgumentException("Invalid terrain demand");
        }
    }

    public static RocketLandingPadSelection awaiting(int candidates, Set<ChunkPos> columns, int step) {
        if (columns.isEmpty()) { throw new IllegalArgumentException("Empty terrain demand"); }
        return new RocketLandingPadSelection(null, candidates, 0, RocketLandingPadSelector.TERRAIN_LOADING,
                columns.stream().sorted(java.util.Comparator.comparingInt((ChunkPos c) -> c.x).thenComparingInt(c -> c.z)).toList(), step);
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

    /** The translated pad reason shown to the player, or null when the detail is audit text only. */
    public String refusalKey() {
        return !success() && detail.startsWith("message.advancedrocketrycommunity.pad.reason.") ? detail : null;
    }
}
