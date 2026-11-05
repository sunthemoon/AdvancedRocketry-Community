package io.github.sunthemoon.advancedrocketrycommunity.station.safety;

import io.github.sunthemoon.advancedrocketrycommunity.celestial.service.SafeCelestialTravel.Destination;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.entity.RocketEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;

/** R340: safety-return-only rocket avoidance; at most 81 loaded columns and no chunk tickets. */
public final class SafetyReturnOverworldArrival {
    public static final int RADIUS = 4;

    private SafetyReturnOverworldArrival() { }

    public record Choice(Destination destination, String audit) { }

    public static Choice choose(ServerLevel level, ServerPlayer player, Destination original) {
        if (!rocketAt(level, box(player, original))) { return new Choice(original, "rocket_avoidance=unneeded"); }
        AABB body = player.getBoundingBox();
        // Keep collision/fluid probes bounded even if another mod changes the player's size.
        if (body.getXsize() > 2 || body.getYsize() > 4 || body.getZsize() > 2) {
            return new Choice(original, "rocket_avoidance=no_free_column");
        }
        BlockPos center = BlockPos.containing(original.x(), original.y(), original.z());
        Destination best = null;
        int nearest = Integer.MAX_VALUE;
        // Squared horizontal distance, with stable x/z order for ties.
        for (int dx = -RADIUS; dx <= RADIUS; dx++) {
            for (int dz = -RADIUS; dz <= RADIUS; dz++) {
                int distance = dx * dx + dz * dz;
                if (distance >= nearest) { continue; }
                int x = center.getX() + dx;
                int z = center.getZ() + dz;
                var chunk = level.getChunkSource().getChunkNow(x >> 4, z >> 4);
                if (chunk == null) { continue; }
                int y = chunk.getHeight(Heightmap.Types.MOTION_BLOCKING, x & 15, z & 15) + 1;
                Destination candidate = new Destination(x + 0.5D, y, z + 0.5D);
                BlockPos feet = new BlockPos(x, y, z);
                AABB arrival = box(player, candidate);
                if (y <= level.getMinBuildHeight() || arrival.maxY >= level.getMaxBuildHeight()
                        || !level.getWorldBorder().isWithinBounds(feet)
                        || !loadedWithCollisionMargin(level, arrival)
                        || !level.getFluidState(feet).isEmpty() || !level.getFluidState(feet.below()).isEmpty()
                        || level.getBlockCollisions(player, arrival).iterator().hasNext()
                        || rocketAt(level, arrival)) { continue; }
                best = candidate;
                nearest = distance;
            }
        }
        return best == null ? new Choice(original, "rocket_avoidance=no_free_column")
                : new Choice(best, "rocket_avoidance=nearby");
    }

    private static AABB box(ServerPlayer player, Destination at) {
        return player.getBoundingBox().move(at.x() - player.getX(), at.y() - player.getY(), at.z() - player.getZ());
    }

    private static boolean rocketAt(ServerLevel level, AABB box) {
        return !level.getEntitiesOfClass(RocketEntity.class, box).isEmpty();
    }

    private static boolean loadedWithCollisionMargin(ServerLevel level, AABB box) {
        BlockPos min = BlockPos.containing(box.minX - 1, box.minY - 1, box.minZ - 1);
        BlockPos max = BlockPos.containing(box.maxX + 1, box.maxY + 1, box.maxZ + 1);
        for (int x = min.getX() >> 4; x <= max.getX() >> 4; x++) {
            for (int z = min.getZ() >> 4; z <= max.getZ() >> 4; z++) {
                if (level.getChunkSource().getChunkNow(x, z) == null) { return false; }
            }
        }
        return true;
    }
}
