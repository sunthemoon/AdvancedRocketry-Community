package io.github.sunthemoon.advancedrocketrycommunity.celestial.service;

import io.github.sunthemoon.advancedrocketrycommunity.celestial.CelestialIds;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

/** Fixed-coordinate, bounded platform and teleport policy for developer travel. */
public final class SafeCelestialTravel {
    public static final BlockPos FIXED_FEET_POSITION = new BlockPos(8, 80, 8);
    public static final int PLATFORM_RADIUS = 2;
    public static final int CLEARANCE = 3;
    public static final int MAX_BLOCK_WRITES = 100;
    private static final Set<ResourceLocation> ALLOWED_BODIES = Set.of(
            CelestialIds.EARTH_ID,
            CelestialIds.MOON_ID,
            CelestialIds.SPACE_ID
    );

    public boolean isAllowedBody(ResourceLocation bodyId) {
        return ALLOWED_BODIES.contains(bodyId);
    }

    public Destination prepare(ServerLevel target, ResourceLocation bodyId) {
        if (!isAllowedBody(bodyId)) {
            throw new IllegalArgumentException("Body is not a fixed v0.3 developer destination: " + bodyId);
        }
        if (CelestialIds.EARTH_ID.equals(bodyId)) {
            BlockPos spawn = target.getSharedSpawnPos();
            int y = Mth.clamp(
                    target.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, spawn.getX(), spawn.getZ()),
                    target.getMinBuildHeight() + 1,
                    target.getMaxBuildHeight() - 2
            );
            return Destination.at(new BlockPos(spawn.getX(), y, spawn.getZ()));
        }
        return prepareFixedPlatform(target);
    }

    public Destination prepareFixedPlatform(ServerLevel target) {
        target.getChunkAt(FIXED_FEET_POSITION);
        int writes = 0;
        int floorY = FIXED_FEET_POSITION.getY() - 1;
        for (int xOffset = -PLATFORM_RADIUS; xOffset <= PLATFORM_RADIUS; xOffset++) {
            for (int zOffset = -PLATFORM_RADIUS; zOffset <= PLATFORM_RADIUS; zOffset++) {
                BlockPos floor = new BlockPos(
                        FIXED_FEET_POSITION.getX() + xOffset,
                        floorY,
                        FIXED_FEET_POSITION.getZ() + zOffset
                );
                target.setBlock(
                        floor,
                        xOffset == 0 && zOffset == 0
                                ? Blocks.SEA_LANTERN.defaultBlockState()
                                : Blocks.SMOOTH_STONE.defaultBlockState(),
                        Block.UPDATE_CLIENTS
                );
                writes++;
                for (int height = 0; height < CLEARANCE; height++) {
                    target.setBlock(
                            floor.above(height + 1),
                            Blocks.AIR.defaultBlockState(),
                            Block.UPDATE_CLIENTS
                    );
                    writes++;
                }
            }
        }
        if (writes > MAX_BLOCK_WRITES) {
            throw new IllegalStateException("Safe platform exceeded its block-write budget");
        }
        return Destination.at(FIXED_FEET_POSITION);
    }

    public void teleport(ServerPlayer player, ServerLevel target, Destination destination) {
        player.stopRiding();
        player.setDeltaMovement(Vec3.ZERO);
        player.fallDistance = 0.0F;
        player.teleportTo(
                target,
                destination.x(),
                destination.y(),
                destination.z(),
                player.getYRot(),
                player.getXRot()
        );
        player.setDeltaMovement(Vec3.ZERO);
        player.fallDistance = 0.0F;
    }

    public record Destination(double x, double y, double z) {
        private static Destination at(BlockPos feet) {
            return new Destination(feet.getX() + 0.5D, feet.getY(), feet.getZ() + 0.5D);
        }
    }
}
