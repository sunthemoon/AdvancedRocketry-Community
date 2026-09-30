package io.github.sunthemoon.advancedrocketrycommunity.rocket.server;

import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFlightLimits;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketTransferRecord;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketStructureSnapshot;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.transaction.RocketRegion;
import java.util.Collection;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Predicate;
import net.minecraft.resources.ResourceLocation;

/**
 * ADR-044 §5: whether a rocket that can still move uses a station region, decided from the
 * transfer journal and the in-memory recovery classification alone (no entity query, no chunk
 * load). Fail closed: an unavailable journal or an unclassified record blocks every warp.
 */
final class RocketStationMotionRule {
    /** Margin after the scheduled landing before a committed arrival counts as landed. */
    static final long LANDED_MARGIN_TICKS = 20L;

    private RocketStationMotionRule() {
    }

    /**
     * @param classified whether recovery or a live launch has classified the record this session
     * @param live whether the record is still being driven (a countdown, launch or descent)
     */
    static boolean blocks(boolean journalOperational, Collection<RocketTransferRecord> records,
                          Predicate<UUID> classified, Predicate<UUID> live, long gameTime, Region region) {
        Objects.requireNonNull(records, "records");
        Objects.requireNonNull(region, "region");
        if (!journalOperational) {
            return true;
        }
        for (RocketTransferRecord record : records) {
            if (!classified.test(record.transferId())) {
                return true;
            }
        }
        for (RocketTransferRecord record : records) {
            if (!region.overlaps(record.sourceSnapshot()) && !region.overlaps(record.destinationSnapshot())) {
                continue;
            }
            boolean moving = switch (record.phase()) {
                case DESTINATION_SPAWNED, PASSENGERS_TRANSFERRED, SOURCE_REMOVED -> true;
                // Settled PREPARED records were returned to a stationary source by recovery.
                case PREPARED -> live.test(record.transferId());
                case COMMITTED -> gameTime < record.destinationFlightData().stateStartedGameTime()
                        + RocketFlightLimits.DESCENT_TICKS + LANDED_MARGIN_TICKS;
            };
            if (moving) {
                return true;
            }
        }
        return false;
    }

    /** A station region: block columns {@code minX..maxX} by {@code minZ..maxZ} in one Level. */
    record Region(ResourceLocation dimension, int minX, int minZ, int maxX, int maxZ) {
        Region {
            Objects.requireNonNull(dimension, "dimension");
            if (minX > maxX || minZ > maxZ) {
                throw new IllegalArgumentException("Region bounds are inverted");
            }
        }

        boolean overlaps(RocketStructureSnapshot snapshot) {
            if (!snapshot.sourceDimension().equals(dimension)) {
                return false;
            }
            RocketRegion rocket = RocketRegion.fromSnapshot(snapshot);
            return minX <= rocket.maximum().x() && maxX >= rocket.minimum().x()
                    && minZ <= rocket.maximum().z() && maxZ >= rocket.minimum().z();
        }
    }
}
