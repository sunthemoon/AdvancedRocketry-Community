package io.github.sunthemoon.advancedrocketrycommunity.rocket.transfer;

import io.github.sunthemoon.advancedrocketrycommunity.rocket.RocketLimits;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.entity.RocketEntity;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFlightLimits;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.transaction.RocketRegion;
import io.github.sunthemoon.advancedrocketrycommunity.station.model.StationRegion;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.AbortableIterationConsumer.Continuation;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.phys.AABB;

/** Loaded entity-section discovery, separate from the bounded terrain inspection. */
final class NamedLandingPadOccupancy {
    private NamedLandingPadOccupancy() { }

    static String unavailable(ServerLevel level, RocketRegion arrival, StationRegion station) {
        AABB discovery = discoveryBox(arrival, station, level.getMinBuildHeight(), level.getMaxBuildHeight());
        int[] inspected = {0};
        String[] refusal = {null};
        // This index visits only accessible, already-loaded entity sections. It never requests chunks.
        level.getEntities().get(EntityTypeTest.forClass(RocketEntity.class), discovery, rocket -> {
            if (inspected[0] == NamedLandingPadStaging.MAX_OCCUPANCY_ENTITY_INSPECTIONS) {
                refusal[0] = "message.advancedrocketrycommunity.pad.reason.entity_budget";
                return Continuation.ABORT;
            }
            inspected[0]++;
            var snapshot = rocket.snapshot().orElse(null);
            // An unreadable nearby rocket cannot establish a safe landing footprint.
            if (snapshot == null || arrival.overlaps(RocketRegion.fromSnapshot(snapshot))) {
                refusal[0] = "message.advancedrocketrycommunity.pad.reason.occupied";
                return Continuation.ABORT;
            }
            return Continuation.CONTINUE;
        });
        return refusal[0];
    }

    static AABB discoveryBox(RocketRegion arrival, StationRegion station, int minimumY, int maximumY) {
        // A scan's origin is a captured block, not necessarily the structure's center. Each axis
        // can span at most MAX_BOUNDING_VOLUME blocks, so expand by the full possible origin reach,
        // not half the width. This also covers asymmetric snapshots such as X=0..15.
        double reach = RocketLimits.MAX_BOUNDING_VOLUME - 1;
        return new AABB(
                Math.max(station.minimumX(), arrival.minimum().x() - reach),
                Math.max(minimumY, arrival.minimum().y() - reach),
                Math.max(station.minimumZ(), arrival.minimum().z() - reach),
                Math.min((double) station.maximumX() + 1, arrival.maximum().x() + 1 + reach),
                // Flight visuals lift the origin; include the existing four-block entity height.
                Math.min((double) maximumY + RocketFlightLimits.FLIGHT_ALTITUDE_BLOCKS + 4,
                        arrival.maximum().y() + 1 + reach),
                Math.min((double) station.maximumZ() + 1, arrival.maximum().z() + 1 + reach));
    }
}
