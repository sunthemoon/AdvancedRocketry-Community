package io.github.sunthemoon.advancedrocketrycommunity.rocket.transfer;

import io.github.sunthemoon.advancedrocketrycommunity.rocket.entity.RocketEntity;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFlightLimits;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketPosition;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketStructureSnapshot;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.transaction.RocketRegion;
import io.github.sunthemoon.advancedrocketrycommunity.station.model.StationState;
import io.github.sunthemoon.advancedrocketrycommunity.station.pad.LandingPadEntry;
import io.github.sunthemoon.advancedrocketrycommunity.station.pad.LandingPadReference;
import io.github.sunthemoon.advancedrocketrycommunity.station.pad.LandingPadService;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.AABB;

/** No chunk loading. One explicitly chosen candidate, complete rectangular clearance and support. */
public final class NamedLandingPadStaging {
    public RocketLandingPadSelection select(ServerLevel level, RocketStructureSnapshot source, UUID transfer,
            StationState station, LandingPadReference reference, List<RocketRegion> reservations, long gameTime) {
        LandingPadEntry entry = LandingPadService.resolve(level.getServer(), station.stationId(), reference);
        if (entry == null) { return refusal("message.advancedrocketrycommunity.pad.reason.identity"); }
        try {
            var bounds = source.bounds();
            BlockPos position = entry.position(station);
            RocketPosition origin = new RocketPosition(
                    Math.subtractExact(position.getX(), Math.floorDiv(Math.addExact(bounds.minimum().x(), bounds.maximum().x()), 2)),
                    Math.subtractExact(Math.addExact(position.getY(), 1), bounds.minimum().y()),
                    Math.subtractExact(position.getZ(), Math.floorDiv(Math.addExact(bounds.minimum().z(), bounds.maximum().z()), 2)));
            var snapshot = source.relocated(UUID.nameUUIDFromBytes((transfer + ":pad:" + reference.padId())
                    .getBytes(StandardCharsets.UTF_8)), level.dimension().location(), origin, gameTime);
            var region = RocketRegion.fromSnapshot(snapshot);
            String reason = unavailable(level, snapshot, station);
            if (reason != null) { return refusal(reason); }
            if (reservations.stream().anyMatch(region::overlaps)) { return refusal("message.advancedrocketrycommunity.pad.reason.reserved"); }
            if (!LandingPadService.claim(level.getServer(), entry, transfer)) { return refusal("message.advancedrocketrycommunity.pad.reason.claimed"); }
            return RocketLandingPadSelection.success(snapshot, 1, 0);
        } catch (ArithmeticException | IllegalArgumentException invalid) {
            return refusal("message.advancedrocketrycommunity.pad.reason.arithmetic");
        }
    }

    public boolean recheck(ServerLevel level, RocketStructureSnapshot snapshot, UUID stationId,
            LandingPadReference reference, UUID transfer) {
        var station = io.github.sunthemoon.advancedrocketrycommunity.station.persistence.StationRegistrySavedData
                .get(level.getServer()).find(stationId).orElse(null);
        LandingPadEntry entry = LandingPadService.resolve(level.getServer(), stationId, reference);
        return station != null && entry != null && transfer.equals(entry.claim())
                && unavailable(level, snapshot, station) == null;
    }

    /** Counts every inspected clearance/support block before the first world access. */
    public String unavailable(ServerLevel level, RocketStructureSnapshot snapshot, StationState station) {
        var region = RocketRegion.fromSnapshot(snapshot);
        long width = (long) region.maximum().x() - region.minimum().x() + 1;
        long depth = (long) region.maximum().z() - region.minimum().z() + 1;
        long height = (long) region.maximum().y() - region.minimum().y() + 1;
        long footprint = width * depth;
        if (footprint <= 0 || footprint > RocketFlightLimits.MAX_LANDING_BLOCK_INSPECTIONS
                || height > RocketFlightLimits.MAX_LANDING_BLOCK_INSPECTIONS / footprint - 1) {
            return "message.advancedrocketrycommunity.pad.reason.block_budget";
        }
        long chunkWidth = (long) (region.maximum().x() >> 4) - (region.minimum().x() >> 4) + 1;
        long chunkDepth = (long) (region.maximum().z() >> 4) - (region.minimum().z() >> 4) + 1;
        if (chunkWidth * chunkDepth > RocketFlightLimits.MAX_LANDING_CHUNKS) { return "message.advancedrocketrycommunity.pad.reason.chunk_budget"; }
        if (!level.dimension().location().equals(snapshot.sourceDimension())
                || region.minimum().y() <= level.getMinBuildHeight() || region.maximum().y() >= level.getMaxBuildHeight()
                || !station.region().contains(region.minimum().x(), region.minimum().z())
                || !station.region().contains(region.maximum().x(), region.maximum().z())
                || !level.getWorldBorder().isWithinBounds(new BlockPos(region.minimum().x(), 0, region.minimum().z()))
                || !level.getWorldBorder().isWithinBounds(new BlockPos(region.maximum().x(), 0, region.maximum().z()))) {
            return "message.advancedrocketrycommunity.pad.reason.bounds";
        }
        for (int x = region.minimum().x() >> 4; x <= region.maximum().x() >> 4; x++) {
            for (int z = region.minimum().z() >> 4; z <= region.maximum().z() >> 4; z++) {
                if (level.getChunkSource().getChunkNow(x, z) == null) { return "message.advancedrocketrycommunity.pad.reason.unloaded"; }
            }
        }
        for (int x = region.minimum().x(); x <= region.maximum().x(); x++) {
            for (int z = region.minimum().z(); z <= region.maximum().z(); z++) {
                BlockPos support = new BlockPos(x, region.minimum().y() - 1, z);
                var state = level.getBlockState(support);
                if (!state.getFluidState().isEmpty() || !state.isFaceSturdy(level, support, Direction.UP)) {
                    return "message.advancedrocketrycommunity.pad.reason.too_small";
                }
                for (int y = region.minimum().y(); y <= region.maximum().y(); y++) {
                    BlockPos pos = new BlockPos(x, y, z);
                    if (!level.getBlockState(pos).isAir() || level.getBlockEntity(pos) != null) { return "message.advancedrocketrycommunity.pad.reason.obstructed"; }
                }
            }
        }
        var box = new AABB(region.minimum().x(), region.minimum().y(), region.minimum().z(),
                (double) region.maximum().x() + 1, (double) region.maximum().y() + 1, (double) region.maximum().z() + 1);
        return level.getEntitiesOfClass(RocketEntity.class, box).isEmpty() ? null : "message.advancedrocketrycommunity.pad.reason.occupied";
    }

    private static RocketLandingPadSelection refusal(String reason) { return RocketLandingPadSelection.failure(1, 0, reason); }
}
