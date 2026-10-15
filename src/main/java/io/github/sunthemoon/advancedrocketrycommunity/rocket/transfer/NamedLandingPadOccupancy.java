package io.github.sunthemoon.advancedrocketrycommunity.rocket.transfer;

import io.github.sunthemoon.advancedrocketrycommunity.AdvancedRocketryCommunity;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.RocketLimits;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.entity.RocketEntity;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFlightLimits;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.transaction.RocketRegion;
import io.github.sunthemoon.advancedrocketrycommunity.station.model.StationRegion;
import java.util.function.Predicate;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.AbortableIterationConsumer.Continuation;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.phys.AABB;

/**
 * Loaded entity-section discovery, separate from the bounded terrain inspection. Shared by named
 * pads and the legacy selector; a null station leaves discovery unclipped horizontally.
 */
final class NamedLandingPadOccupancy {
    static final String OCCUPIED = "message.advancedrocketrycommunity.pad.reason.occupied";
    static final String ENTITY_BUDGET = "message.advancedrocketrycommunity.pad.reason.entity_budget";

    private NamedLandingPadOccupancy() { }

    static String unavailable(ServerLevel level, RocketRegion arrival, StationRegion station) {
        return unavailable(level, arrival, station, rocket -> false);
    }

    /** An ignored rocket still counts against the inspection budget. */
    static String unavailable(ServerLevel level, RocketRegion arrival, StationRegion station,
            Predicate<RocketEntity> ignored) {
        AABB discovery = discoveryBox(arrival, station, level.getMinBuildHeight(), level.getMaxBuildHeight());
        int[] inspected = {0};
        String[] refusal = {null};
        RocketEntity[] blocker = {null};
        // This index visits only accessible, already-loaded entity sections. It never requests chunks.
        level.getEntities().get(EntityTypeTest.forClass(RocketEntity.class), discovery, rocket -> {
            if (inspected[0] == NamedLandingPadStaging.MAX_OCCUPANCY_ENTITY_INSPECTIONS) {
                refusal[0] = ENTITY_BUDGET;
                return Continuation.ABORT;
            }
            inspected[0]++;
            if (ignored.test(rocket)) {
                return Continuation.CONTINUE;
            }
            var snapshot = rocket.snapshot().orElse(null);
            // An unreadable nearby rocket cannot establish a safe landing footprint.
            if (snapshot == null || arrival.overlaps(RocketRegion.fromSnapshot(snapshot))) {
                refusal[0] = OCCUPIED;
                blocker[0] = rocket;
                return Continuation.ABORT;
            }
            return Continuation.CONTINUE;
        });
        if (refusal[0] != null) {
            // The search spans the whole station, so name what refused it for operators (W13F review N3).
            AdvancedRocketryCommunity.LOGGER.info("ARCE_PAD_OCCUPANCY_REFUSED reason={} level={} arrival={},{},{} rocket={}"
                            + " rocket_pos={} snapshot={} inspected={}", refusal[0] == OCCUPIED ? "occupied" : "entity_budget",
                    level.dimension().location(), arrival.minimum().x(), arrival.minimum().y(), arrival.minimum().z(),
                    blocker[0] == null ? "none" : blocker[0].getUUID(),
                    blocker[0] == null ? "none" : blocker[0].blockPosition().toShortString(),
                    blocker[0] == null ? "none" : blocker[0].snapshot().isPresent() ? "readable" : "unreadable", inspected[0]);
        }
        return refusal[0];
    }

    static AABB discoveryBox(RocketRegion arrival, StationRegion station, int minimumY, int maximumY) {
        // A scan's origin is a captured block, not necessarily the structure's center. Each axis
        // can span at most MAX_BOUNDING_VOLUME blocks, so expand by the full possible origin reach,
        // not half the width. This also covers asymmetric snapshots such as X=0..15.
        double reach = RocketLimits.MAX_BOUNDING_VOLUME - 1;
        double minimumX = arrival.minimum().x() - reach;
        double minimumZ = arrival.minimum().z() - reach;
        double maximumX = arrival.maximum().x() + 1 + reach;
        double maximumZ = arrival.maximum().z() + 1 + reach;
        if (station != null) {
            minimumX = Math.max(station.minimumX(), minimumX);
            minimumZ = Math.max(station.minimumZ(), minimumZ);
            maximumX = Math.min((double) station.maximumX() + 1, maximumX);
            maximumZ = Math.min((double) station.maximumZ() + 1, maximumZ);
        }
        return new AABB(
                minimumX,
                Math.max(minimumY, arrival.minimum().y() - reach),
                minimumZ,
                maximumX,
                // Flight visuals lift the origin; include the existing four-block entity height.
                Math.min((double) maximumY + RocketFlightLimits.FLIGHT_ALTITUDE_BLOCKS + 4,
                        arrival.maximum().y() + 1 + reach),
                maximumZ);
    }
}
