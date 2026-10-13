package io.github.sunthemoon.advancedrocketrycommunity.beacon.model;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * The finder's visibility filter (ADR-065 section 7): of the viewer's own records, only those last known active on
 * the viewer's server-resolved body and in the viewer's Level, the nearest {@link BeaconLimits#MAX_MARKERS} sorted by
 * distance and then by canonical UUID. Another owner's record never passes, whatever its position.
 */
public final class BeaconMarkerSelection {
    private BeaconMarkerSelection() {
    }

    /** The viewer: who looks, from which body and Level, and from which block position. */
    public record Viewer(UUID player, String body, String level, int x, int y, int z) {
        public Viewer {
            Objects.requireNonNull(player, "player");
            Objects.requireNonNull(body, "body");
            Objects.requireNonNull(level, "level");
        }
    }

    public static List<BeaconRecord> select(Collection<BeaconRecord> candidates, Viewer viewer) {
        Objects.requireNonNull(viewer, "viewer");
        List<BeaconRecord> visible = new ArrayList<>();
        for (BeaconRecord record : candidates) {
            if (record.owner().equals(viewer.player()) && record.active() && record.body().equals(viewer.body())
                    && record.level().equals(viewer.level())) {
                visible.add(record);
            }
        }
        visible.sort(Comparator.<BeaconRecord>comparingLong(record ->
                        record.distanceSquared(viewer.x(), viewer.y(), viewer.z()))
                .thenComparing(record -> record.beaconId().toString()));
        return List.copyOf(visible.subList(0, Math.min(BeaconLimits.MAX_MARKERS, visible.size())));
    }
}
