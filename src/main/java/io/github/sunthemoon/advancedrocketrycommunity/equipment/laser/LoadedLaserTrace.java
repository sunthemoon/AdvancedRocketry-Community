package io.github.sunthemoon.advancedrocketrycommunity.equipment.laser;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.AbortableIterationConsumer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;

/** Reads each voxel through getChunkNow; shape callbacks receive a non-loading BlockGetter too. */
final class LoadedLaserTrace {
    static final int MAX_ENTITY_VISITS = 256;
    record Hit(BlockPos position, BlockState state, Entity entity) { }
    private LoadedLaserTrace() { }

    static Hit trace(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        Vec3 start = player.getEyePosition();
        Vec3 direction = player.getLookAngle().normalize();
        Vec3 end = start.add(direction.scale(LaserRay.RANGE));
        var cells = LaserRay.cells(point(start), point(direction), LaserRay.RANGE);
        LoadedView view = new LoadedView(level);
        int[] visits = {0};
        for (var cell : cells) {
            BlockPos pos = new BlockPos(cell.x(), cell.y(), cell.z());
            if (level.getChunkSource().getChunkNow(cell.chunkX(), cell.chunkZ()) == null
                    || !level.getWorldBorder().isWithinBounds(pos) || level.isOutsideBuildHeight(pos)) { return null; }
            BlockState state = view.getBlockState(pos);
            var block = state.getShape(view, pos, CollisionContext.of(player)).clip(start, end, pos);
            if (view.unavailable) { return null; }
            double blockDistance = block == null ? Double.POSITIVE_INFINITY : start.distanceTo(block.getLocation());
            Entity[] nearest = {null};
            double[] distance = {Double.POSITIVE_INFINITY};
            Vec3 from = start.add(direction.scale(cell.enter()));
            Vec3 to = start.add(direction.scale(cell.exit()));
            level.getEntities().get(EntityTypeTest.forClass(Entity.class), new AABB(from, to).inflate(1), entity -> {
                if (++visits[0] > MAX_ENTITY_VISITS) { return AbortableIterationConsumer.Continuation.ABORT; }
                if (entity != player && !entity.isSpectator() && entity.isPickable() && !entity.isRemoved()) {
                    AABB box = entity.getBoundingBox().inflate(entity.getPickRadius());
                    var hit = box.contains(start) ? java.util.Optional.of(start) : box.clip(start, end);
                    if (hit.isPresent()) {
                        double at = start.distanceTo(hit.orElseThrow());
                        if (at >= cell.enter() - 1.0E-7 && at <= cell.exit() + 1.0E-7 && at < distance[0]) {
                            nearest[0] = entity; distance[0] = at;
                        }
                    }
                }
                return AbortableIterationConsumer.Continuation.CONTINUE;
            });
            if (visits[0] > MAX_ENTITY_VISITS) { return null; }
            if (nearest[0] != null && distance[0] <= blockDistance) {
                return new Hit(nearest[0].blockPosition(), null, nearest[0]);
            }
            if (blockDistance >= cell.enter() - 1.0E-7 && blockDistance <= cell.exit() + 1.0E-7) {
                return new Hit(pos, state, null);
            }
        }
        return null;
    }

    private static LaserRay.Point point(Vec3 v) { return new LaserRay.Point(v.x, v.y, v.z); }

    private static final class LoadedView implements BlockGetter {
        private final ServerLevel level;
        private boolean unavailable;
        LoadedView(ServerLevel level) { this.level = level; }
        private net.minecraft.world.level.chunk.LevelChunk chunk(BlockPos pos) {
            var chunk = level.getChunkSource().getChunkNow(pos.getX() >> 4, pos.getZ() >> 4);
            if (chunk == null) { unavailable = true; }
            return chunk;
        }
        @Override public BlockState getBlockState(BlockPos pos) {
            var chunk = chunk(pos); return chunk == null ? Blocks.AIR.defaultBlockState() : chunk.getBlockState(pos);
        }
        @Override public BlockEntity getBlockEntity(BlockPos pos) {
            var chunk = chunk(pos); return chunk == null ? null : chunk.getBlockEntity(pos);
        }
        @Override public FluidState getFluidState(BlockPos pos) { return getBlockState(pos).getFluidState(); }
        @Override public int getHeight() { return level.getHeight(); }
        @Override public int getMinBuildHeight() { return level.getMinBuildHeight(); }
    }
}
