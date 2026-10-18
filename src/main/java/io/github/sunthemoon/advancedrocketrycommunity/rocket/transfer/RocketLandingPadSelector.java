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
import io.github.sunthemoon.advancedrocketrycommunity.station.model.StationLimits;
import io.github.sunthemoon.advancedrocketrycommunity.station.model.StationRegion;
import io.github.sunthemoon.advancedrocketrycommunity.station.model.StationState;
import io.github.sunthemoon.advancedrocketrycommunity.station.persistence.StationRegistrySavedData;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;

/** Selects one of eight server-owned surface pads after catalog/Level admission. */
public final class RocketLandingPadSelector {
    /** Audit-only refusal: terrain, bounds, budgets or a transfer reservation. */
    public static final String UNSAFE = "unsafe";
    /** W90 (R21 item 3): a governed destination whose checked margin is not admissible. */
    public static final String CHUNK_BUDGET = "message.advancedrocketrycommunity.pad.reason.chunk_budget";
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
        return select(target, source, transferId, reservations, gameTime, false);
    }

    /**
     * {@code governed}: W90 (R21 item 3) replaces the region chunk count with the checked discovery-margin column set,
     * before any terrain access. Legacy selection is unchanged.
     */
    public RocketLandingPadSelection select(
            ServerLevel target,
            RocketStructureSnapshot source,
            UUID transferId,
            List<RocketRegion> reservations,
            long gameTime,
            boolean governed
    ) {
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(transferId, "transferId");
        Objects.requireNonNull(reservations, "reservations");
        if (target.dimension().equals(CelestialIds.SPACE_LEVEL)) {
            return RocketLandingPadSelection.failure(0, 0, "shared Space requires a committed station pad");
        }
        BlockPos base = target.dimension() == Level.OVERWORLD
                ? target.getSharedSpawnPos()
                : target.dimension().equals(CelestialIds.MOON_LEVEL) ? SafeCelestialTravel.FIXED_FEET_POSITION : BlockPos.ZERO;
        int candidates = 0;
        int chunksLoaded = 0;
        for (int index = 0; index < OFFSETS.length
                && index < RocketFlightLimits.MAX_LANDING_PAD_CANDIDATES; index++) {
            candidates++;
            Candidate candidate = candidate(target, source, base, OFFSETS[index]);
            if (candidate == null) {
                continue;
            }
            if (governed) {
                // The fixed pads are 64 blocks apart, so every candidate shares one chunk alignment and one count:
                // the first failure ends selection before loadChunks and the surface read.
                if (!RocketDiscoveryColumns.of(candidate.originX(), candidate.originZ(), source.bounds()).admissible()) {
                    return RocketLandingPadSelection.failure(candidates, chunksLoaded, CHUNK_BUDGET);
                }
            } else if (candidate.chunkCount() > RocketFlightLimits.MAX_LANDING_CHUNKS) {
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
            if (reservations.stream().anyMatch(region::overlaps)) {
                continue;
            }
            String refusal = unavailable(target, relocated, null, false);
            if (NamedLandingPadOccupancy.ENTITY_BUDGET.equals(refusal)) {
                // The fixed pads share almost the same discovery box, so a later candidate cannot do better.
                return RocketLandingPadSelection.failure(candidates, chunksLoaded, refusal);
            }
            if (refusal != null) {
                // An occupied or unsafe candidate falls through to the next fixed pad.
                continue;
            }
            return RocketLandingPadSelection.success(relocated, candidates, chunksLoaded);
        }
        return RocketLandingPadSelection.failure(candidates, chunksLoaded, "all fixed pads are occupied or unsafe");
    }

    /** Resolves one committed server-owned station pad; no coordinate comes from the client. */
    public RocketLandingPadSelection selectStation(
            ServerLevel target,
            RocketStructureSnapshot source,
            UUID transferId,
            StationState station,
            List<RocketRegion> reservations,
            long gameTime
    ) {
        return selectStation(target, source, transferId, station, reservations, gameTime, false);
    }

    /** {@code governed}: the checked margin replaces the region count before loadChunks (W90, R21 item 3). */
    public RocketLandingPadSelection selectStation(
            ServerLevel target,
            RocketStructureSnapshot source,
            UUID transferId,
            StationState station,
            List<RocketRegion> reservations,
            long gameTime,
            boolean governed
    ) {
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(transferId, "transferId");
        Objects.requireNonNull(station, "station");
        Objects.requireNonNull(reservations, "reservations");
        if (!target.dimension().equals(CelestialIds.SPACE_LEVEL)) {
            return RocketLandingPadSelection.failure(0, 0, "target is not the fixed Space Level");
        }
        RocketBounds bounds = source.bounds();
        long width = (long) bounds.maximum().x() - bounds.minimum().x() + 1L;
        long depth = (long) bounds.maximum().z() - bounds.minimum().z() + 1L;
        int platformWidth = StationLimits.PLATFORM_RADIUS * 2 + 1;
        if (width > platformWidth || depth > platformWidth) {
            return RocketLandingPadSelection.failure(1, 0, "rocket footprint exceeds station pad");
        }
        try {
            int originX = Math.subtractExact(
                    station.landingPad().x(),
                    Math.floorDiv(Math.addExact(bounds.minimum().x(), bounds.maximum().x()), 2)
            );
            int originZ = Math.subtractExact(
                    station.landingPad().z(),
                    Math.floorDiv(Math.addExact(bounds.minimum().z(), bounds.maximum().z()), 2)
            );
            int originY = Math.subtractExact(station.landingPad().y(), bounds.minimum().y());
            RocketPosition origin = new RocketPosition(originX, originY, originZ);
            UUID snapshotId = UUID.nameUUIDFromBytes((
                    transferId + ":" + station.stationId()
            ).getBytes(StandardCharsets.UTF_8));
            RocketStructureSnapshot relocated = source.relocated(
                    snapshotId,
                    target.dimension().location(),
                    origin,
                    gameTime
            );
            RocketRegion region = RocketRegion.fromSnapshot(relocated);
            if (!station.region().contains(region.minimum().x(), region.minimum().z())
                    || !station.region().contains(region.maximum().x(), region.maximum().z())) {
                return RocketLandingPadSelection.failure(1, 0, "rocket leaves station region");
            }
            Set<ChunkPos> chunks = chunks(region);
            if (governed) {
                if (!RocketDiscoveryColumns.of(relocated).admissible()) {
                    return RocketLandingPadSelection.failure(1, 0, CHUNK_BUDGET);
                }
            } else if (chunks.size() > RocketFlightLimits.MAX_LANDING_CHUNKS) {
                return RocketLandingPadSelection.failure(1, 0, "station landing exceeds chunk bound");
            }
            int loaded = loadChunks(target, chunks);
            String refusal = reservations.stream().anyMatch(region::overlaps)
                    ? UNSAFE : unavailable(target, relocated, null, false);
            if (refusal != null) {
                return RocketLandingPadSelection.failure(1, loaded,
                        refusal.equals(UNSAFE) ? "station pad is occupied or unsafe" : refusal);
            }
            return RocketLandingPadSelection.success(relocated, 1, loaded);
        } catch (RuntimeException exception) {
            return RocketLandingPadSelection.failure(1, 0, "station landing arithmetic failed");
        }
    }

    /** Rechecks the exact reserved destination without accepting any new coordinate. */
    public boolean available(
            ServerLevel target,
            RocketStructureSnapshot destination,
            UUID allowedRocketId,
            boolean allowMatchingRocket
    ) {
        return unavailable(target, destination, allowedRocketId, allowMatchingRocket) == null;
    }

    /**
     * Null when the exact destination is available. An occupant or the discovery budget is a
     * translated pad reason; every other refusal is {@link #UNSAFE}, which is audit text only.
     */
    public String unavailable(
            ServerLevel target,
            RocketStructureSnapshot destination,
            UUID allowedRocketId,
            boolean allowMatchingRocket
    ) {
        return unavailable(target, destination, allowedRocketId, allowMatchingRocket, true);
    }

    /** Governed final boundaries cannot load terrain while checking their recorded destination. */
    public boolean availableLoaded(ServerLevel target, RocketStructureSnapshot destination) {
        return unavailable(target, destination, null, false, false) == null;
    }

    private String unavailable(ServerLevel target, RocketStructureSnapshot destination, UUID allowedRocketId,
                               boolean allowMatchingRocket, boolean load) {
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(destination, "destination");
        if (!target.dimension().location().equals(destination.sourceDimension())) {
            return UNSAFE;
        }
        RocketRegion region;
        try {
            region = RocketRegion.fromSnapshot(destination);
        } catch (RuntimeException exception) {
            return UNSAFE;
        }
        if (region.minimum().y() < target.getMinBuildHeight()
                || region.maximum().y() >= target.getMaxBuildHeight()
                || !withinBorder(target, region)) {
            return UNSAFE;
        }
        Set<ChunkPos> chunks = chunks(region);
        if (chunks.size() > RocketFlightLimits.MAX_LANDING_CHUNKS) {
            return UNSAFE;
        }
        if (load) { loadChunks(target, chunks); }
        else if (chunks.stream().anyMatch(chunk -> target.getChunkSource().getChunkNow(chunk.x, chunk.z) == null)) { return UNSAFE; }
        int inspected = 0;
        for (RocketBlock block : destination.blocks()) {
            if (++inspected > RocketFlightLimits.MAX_LANDING_BLOCK_INSPECTIONS) {
                return UNSAFE;
            }
            RocketPosition absolute;
            try {
                absolute = destination.sourceOrigin().add(block.position());
            } catch (RuntimeException exception) {
                return UNSAFE;
            }
            BlockPos position = new BlockPos(absolute.x(), absolute.y(), absolute.z());
            if (!target.getBlockState(position).isAir() || target.getBlockEntity(position) != null) {
                return UNSAFE;
            }
        }
        if (!legacySurface(target) && !target.dimension().equals(CelestialIds.SPACE_LEVEL)
                && !supported(target, destination)) {
            return UNSAFE;
        }
        AABB box = new AABB(
                region.minimum().x(),
                region.minimum().y(),
                region.minimum().z(),
                (double) region.maximum().x() + 1.0D,
                (double) region.maximum().y() + 1.0D,
                (double) region.maximum().z() + 1.0D
        );
        Predicate<RocketEntity> matching = rocket -> allowMatchingRocket
                && allowedRocketId != null
                && allowedRocketId.equals(rocket.getUUID());
        if (!target.getEntitiesOfClass(RocketEntity.class, box).stream().allMatch(matching)) {
            return NamedLandingPadOccupancy.OCCUPIED;
        }
        // A parked rocket's small entity box can miss an arrival that its structure covers (W13 R1).
        return NamedLandingPadOccupancy.unavailable(target, region, stationRegion(target, region), matching);
    }

    /** The committed station owning a Space arrival, so a recheck discovers what selection did. */
    private static StationRegion stationRegion(ServerLevel target, RocketRegion region) {
        if (!target.dimension().equals(CelestialIds.SPACE_LEVEL)) {
            return null;
        }
        return StationRegistrySavedData.get(target.getServer())
                .findAt(region.minimum().x(), region.minimum().z())
                .map(StationState::region)
                .filter(station -> station.contains(region.maximum().x(), region.maximum().z()))
                .orElse(null);
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
            return withinBorder(target, horizontal) ? new Candidate(centerX, centerZ, originX, originZ, chunks) : null;
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

    private static boolean legacySurface(ServerLevel target) {
        return target.dimension() == Level.OVERWORLD || target.dimension().equals(CelestialIds.MOON_LEVEL);
    }

    private static boolean withinBorder(ServerLevel target, RocketRegion region) {
        return target.getWorldBorder().isWithinBounds(new BlockPos(region.minimum().x(), 0, region.minimum().z()))
                && target.getWorldBorder().isWithinBounds(new BlockPos(region.maximum().x(), 0, region.maximum().z()));
    }

    private static boolean supported(ServerLevel target, RocketStructureSnapshot snapshot) {
        boolean supported = false;
        int inspected = 0;
        for (RocketBlock block : snapshot.blocks()) {
            if (block.position().y() != snapshot.bounds().minimum().y()) {
                continue;
            }
            if (++inspected > RocketFlightLimits.MAX_LANDING_BLOCK_INSPECTIONS) {
                return false;
            }
            var absolute = snapshot.sourceOrigin().add(block.position());
            var below = new BlockPos(absolute.x(), absolute.y() - 1, absolute.z());
            var state = target.getBlockState(below);
            if (!state.getFluidState().isEmpty()) {
                return false;
            }
            supported |= state.isFaceSturdy(target, below, Direction.UP);
        }
        return supported;
    }

    private record Candidate(int centerX, int centerZ, int originX, int originZ, Set<ChunkPos> chunks) {
        private int chunkCount() {
            return chunks.size();
        }
    }
}
