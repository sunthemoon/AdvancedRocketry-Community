package io.github.sunthemoon.advancedrocketrycommunity.rocket.transfer;

import io.github.sunthemoon.advancedrocketrycommunity.celestial.CelestialIds;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.service.SafeCelestialTravel;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.entity.RocketEntity;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFlightLimits;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketBlock;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketBounds;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketPosition;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketStructureSnapshot;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.transaction.RocketRegion;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;

/** Selects only one of eight deterministic server-owned Earth/Moon pads. */
public final class RocketLandingPadSelector {
    private static final int PAD_SPACING = 64;
    private static final int[][] OFFSETS = {
            {0, 0},
            {PAD_SPACING, 0},
            {-PAD_SPACING, 0},
            {0, PAD_SPACING},
            {0, -PAD_SPACING},
            {PAD_SPACING, PAD_SPACING},
            {-PAD_SPACING, PAD_SPACING},
            {PAD_SPACING, -PAD_SPACING}
    };

    public RocketLandingPadSelection select(
            ServerLevel target,
            RocketStructureSnapshot source,
            UUID transferId,
            List<RocketRegion> reservations,
            long gameTime
    ) {
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(transferId, "transferId");
        Objects.requireNonNull(reservations, "reservations");
        if (!isFlightDimension(target)) {
            return RocketLandingPadSelection.failure(0, 0, "target is not Earth or Moon");
        }
        BlockPos base = target.dimension() == Level.OVERWORLD
                ? target.getSharedSpawnPos()
                : SafeCelestialTravel.FIXED_FEET_POSITION;
        int candidates = 0;
        int chunksLoaded = 0;
        for (int index = 0; index < OFFSETS.length
                && index < RocketFlightLimits.MAX_LANDING_PAD_CANDIDATES; index++) {
            candidates++;
            Candidate candidate = candidate(target, source, base, OFFSETS[index]);
            if (candidate == null) {
                continue;
            }
            if (candidate.chunkCount() > RocketFlightLimits.MAX_LANDING_CHUNKS) {
                continue;
            }
            chunksLoaded += loadChunks(target, candidate.chunks());
            RocketPosition origin = originAtSurface(target, source, candidate.centerX(), candidate.centerZ());
            if (origin == null) {
                continue;
            }
            UUID snapshotId = UUID.nameUUIDFromBytes((
                    transferId + ":" + target.dimension().location() + ":" + index
            ).getBytes(StandardCharsets.UTF_8));
            RocketStructureSnapshot relocated;
            try {
                relocated = source.relocated(snapshotId, target.dimension().location(), origin, gameTime);
            } catch (RuntimeException exception) {
                continue;
            }
            RocketRegion region = RocketRegion.fromSnapshot(relocated);
            if (reservations.stream().anyMatch(region::overlaps)
                    || !available(target, relocated, null, false)) {
                continue;
            }
            return RocketLandingPadSelection.success(relocated, candidates, chunksLoaded);
        }
        return RocketLandingPadSelection.failure(candidates, chunksLoaded, "all fixed pads are occupied or unsafe");
    }

    /** Rechecks the exact reserved destination without accepting any new coordinate. */
    public boolean available(
            ServerLevel target,
            RocketStructureSnapshot destination,
            UUID allowedRocketId,
            boolean allowMatchingRocket
    ) {
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(destination, "destination");
        if (!target.dimension().location().equals(destination.sourceDimension())) {
            return false;
        }
        RocketRegion region;
        try {
            region = RocketRegion.fromSnapshot(destination);
        } catch (RuntimeException exception) {
            return false;
        }
        if (region.minimum().y() < target.getMinBuildHeight()
                || region.maximum().y() >= target.getMaxBuildHeight()) {
            return false;
        }
        Set<ChunkPos> chunks = chunks(region);
        if (chunks.size() > RocketFlightLimits.MAX_LANDING_CHUNKS) {
            return false;
        }
        loadChunks(target, chunks);
        int inspected = 0;
        for (RocketBlock block : destination.blocks()) {
            if (++inspected > RocketFlightLimits.MAX_LANDING_BLOCK_INSPECTIONS) {
                return false;
            }
            RocketPosition absolute;
            try {
                absolute = destination.sourceOrigin().add(block.position());
            } catch (RuntimeException exception) {
                return false;
            }
            BlockPos position = new BlockPos(absolute.x(), absolute.y(), absolute.z());
            if (!target.getBlockState(position).isAir() || target.getBlockEntity(position) != null) {
                return false;
            }
        }
        AABB box = new AABB(
                region.minimum().x(),
                region.minimum().y(),
                region.minimum().z(),
                (double) region.maximum().x() + 1.0D,
                (double) region.maximum().y() + 1.0D,
                (double) region.maximum().z() + 1.0D
        );
        return target.getEntitiesOfClass(RocketEntity.class, box).stream().allMatch(rocket ->
                allowMatchingRocket
                        && allowedRocketId != null
                        && allowedRocketId.equals(rocket.getUUID())
        );
    }

    private static Candidate candidate(
            ServerLevel target,
            RocketStructureSnapshot source,
            BlockPos base,
            int[] offset
    ) {
        RocketBounds bounds = source.bounds();
        try {
            int centerX = Math.addExact(base.getX(), offset[0]);
            int centerZ = Math.addExact(base.getZ(), offset[1]);
            int originX = Math.subtractExact(
                    centerX,
                    Math.floorDiv(Math.addExact(bounds.minimum().x(), bounds.maximum().x()), 2)
            );
            int originZ = Math.subtractExact(
                    centerZ,
                    Math.floorDiv(Math.addExact(bounds.minimum().z(), bounds.maximum().z()), 2)
            );
            RocketRegion horizontal = new RocketRegion(
                    target.dimension().location(),
                    new RocketPosition(
                            Math.addExact(originX, bounds.minimum().x()),
                            target.getMinBuildHeight(),
                            Math.addExact(originZ, bounds.minimum().z())
                    ),
                    new RocketPosition(
                            Math.addExact(originX, bounds.maximum().x()),
                            target.getMinBuildHeight(),
                            Math.addExact(originZ, bounds.maximum().z())
                    )
            );
            Set<ChunkPos> chunks = chunks(horizontal);
            return new Candidate(centerX, centerZ, chunks);
        } catch (ArithmeticException exception) {
            return null;
        }
    }

    private static RocketPosition originAtSurface(
            ServerLevel target,
            RocketStructureSnapshot source,
            int centerX,
            int centerZ
    ) {
        RocketBounds bounds = source.bounds();
        try {
            int originX = Math.subtractExact(
                    centerX,
                    Math.floorDiv(Math.addExact(bounds.minimum().x(), bounds.maximum().x()), 2)
            );
            int originZ = Math.subtractExact(
                    centerZ,
                    Math.floorDiv(Math.addExact(bounds.minimum().z(), bounds.maximum().z()), 2)
            );
            int surface = target.getMinBuildHeight() + 1;
            Set<Long> columns = new HashSet<>();
            int inspected = 0;
            for (RocketBlock block : source.blocks()) {
                int x = Math.addExact(originX, block.position().x());
                int z = Math.addExact(originZ, block.position().z());
                long column = BlockPos.asLong(x, 0, z);
                if (columns.add(column)) {
                    if (++inspected > RocketFlightLimits.MAX_LANDING_BLOCK_INSPECTIONS) {
                        return null;
                    }
                    surface = Math.max(
                            surface,
                            target.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z)
                    );
                }
            }
            if (target.dimension().equals(CelestialIds.MOON_LEVEL)) {
                surface = Math.max(surface, SafeCelestialTravel.FIXED_FEET_POSITION.getY());
            }
            int originY = Math.subtractExact(surface, bounds.minimum().y());
            RocketPosition origin = new RocketPosition(originX, originY, originZ);
            RocketPosition minimum = origin.add(bounds.minimum());
            RocketPosition maximum = origin.add(bounds.maximum());
            if (minimum.y() < target.getMinBuildHeight()
                    || maximum.y() >= target.getMaxBuildHeight()) {
                return null;
            }
            return origin;
        } catch (RuntimeException exception) {
            return null;
        }
    }

    private static Set<ChunkPos> chunks(RocketRegion region) {
        HashSet<ChunkPos> chunks = new HashSet<>();
        int minimumX = region.minimum().x() >> 4;
        int maximumX = region.maximum().x() >> 4;
        int minimumZ = region.minimum().z() >> 4;
        int maximumZ = region.maximum().z() >> 4;
        for (int x = minimumX; x <= maximumX; x++) {
            for (int z = minimumZ; z <= maximumZ; z++) {
                if (chunks.size() > RocketFlightLimits.MAX_LANDING_CHUNKS) {
                    return chunks;
                }
                chunks.add(new ChunkPos(x, z));
            }
        }
        return chunks;
    }

    private static int loadChunks(ServerLevel target, Set<ChunkPos> chunks) {
        for (ChunkPos chunk : chunks) {
            target.getChunk(chunk.x, chunk.z);
        }
        return chunks.size();
    }

    private static boolean isFlightDimension(ServerLevel target) {
        return target.dimension() == Level.OVERWORLD || target.dimension().equals(CelestialIds.MOON_LEVEL);
    }

    private record Candidate(int centerX, int centerZ, Set<ChunkPos> chunks) {
        private int chunkCount() {
            return chunks.size();
        }
    }
}
