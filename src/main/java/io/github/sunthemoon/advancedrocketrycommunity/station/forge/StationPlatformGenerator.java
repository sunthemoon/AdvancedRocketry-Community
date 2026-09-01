package io.github.sunthemoon.advancedrocketrycommunity.station.forge;

import io.github.sunthemoon.advancedrocketrycommunity.celestial.CelestialIds;
import io.github.sunthemoon.advancedrocketrycommunity.station.model.StationGridCell;
import io.github.sunthemoon.advancedrocketrycommunity.station.model.StationLimits;
import io.github.sunthemoon.advancedrocketrycommunity.station.model.StationReservation;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/** Bounded fixed-template generator; it never writes outside one station pad. */
public final class StationPlatformGenerator {
    public StationPlatformResult generate(ServerLevel level, StationReservation reservation) {
        Objects.requireNonNull(reservation, "reservation");
        return generate(level, reservation.cell());
    }

    public StationPlatformResult generate(ServerLevel level, StationGridCell cell) {
        Objects.requireNonNull(level, "level");
        Objects.requireNonNull(cell, "cell");
        if (!level.dimension().equals(CelestialIds.SPACE_LEVEL)) {
            return StationPlatformResult.failure(0, 0, 0, "target is not the fixed Space Level");
        }
        if (StationLimits.PLATFORM_Y < level.getMinBuildHeight()
                || StationLimits.LANDING_Y >= level.getMaxBuildHeight()) {
            return StationPlatformResult.failure(0, 0, 0, "platform is outside build height");
        }
        Set<ChunkPos> chunks = chunks(cell);
        for (ChunkPos chunk : chunks) {
            level.getChunk(chunk.x, chunk.z);
        }
        List<BlockPos> positions = positions(cell);
        int inspected = 0;
        for (BlockPos position : positions) {
            inspected++;
            if (!level.getBlockState(position).isAir() || level.getBlockEntity(position) != null) {
                return StationPlatformResult.failure(
                        inspected, 0, chunks.size(), "platform footprint is occupied"
                );
            }
        }
        ArrayList<BlockPos> written = new ArrayList<>(positions.size());
        for (BlockPos position : positions) {
            BlockState state = template(cell, position);
            if (!level.setBlock(position, state, Block.UPDATE_ALL)) {
                rollbackWritten(level, cell, written);
                return StationPlatformResult.failure(
                        inspected, written.size(), chunks.size(), "platform block write failed"
                );
            }
            written.add(position);
        }
        return StationPlatformResult.success(inspected, written.size(), chunks.size());
    }

    /** Removes only unchanged template blocks, never arbitrary player construction. */
    public StationPlatformResult removeTemplate(ServerLevel level, StationGridCell cell) {
        Objects.requireNonNull(level, "level");
        Objects.requireNonNull(cell, "cell");
        if (!level.dimension().equals(CelestialIds.SPACE_LEVEL)) {
            return StationPlatformResult.failure(0, 0, 0, "target is not the fixed Space Level");
        }
        Set<ChunkPos> chunks = chunks(cell);
        for (ChunkPos chunk : chunks) {
            level.getChunk(chunk.x, chunk.z);
        }
        int inspected = 0;
        int removed = 0;
        for (BlockPos position : positions(cell)) {
            inspected++;
            if (level.getBlockState(position).equals(template(cell, position))
                    && level.setBlock(position, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL)) {
                removed++;
            }
        }
        return StationPlatformResult.success(inspected, removed, chunks.size());
    }

    public boolean intact(ServerLevel level, StationGridCell cell) {
        int inspected = 0;
        for (BlockPos position : positions(cell)) {
            if (++inspected > StationLimits.PLATFORM_BLOCKS
                    || !level.getBlockState(position).equals(template(cell, position))) {
                return false;
            }
        }
        return inspected == StationLimits.PLATFORM_BLOCKS;
    }

    private static void rollbackWritten(ServerLevel level, StationGridCell cell, List<BlockPos> written) {
        for (int index = written.size() - 1; index >= 0; index--) {
            BlockPos position = written.get(index);
            if (level.getBlockState(position).equals(template(cell, position))) {
                level.setBlock(position, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
            }
        }
    }

    private static List<BlockPos> positions(StationGridCell cell) {
        ArrayList<BlockPos> result = new ArrayList<>(StationLimits.PLATFORM_BLOCKS);
        for (int x = -StationLimits.PLATFORM_RADIUS; x <= StationLimits.PLATFORM_RADIUS; x++) {
            for (int z = -StationLimits.PLATFORM_RADIUS; z <= StationLimits.PLATFORM_RADIUS; z++) {
                result.add(new BlockPos(
                        Math.addExact(cell.centerX(), x),
                        StationLimits.PLATFORM_Y,
                        Math.addExact(cell.centerZ(), z)
                ));
            }
        }
        if (result.size() != StationLimits.PLATFORM_BLOCKS) {
            throw new IllegalStateException("Station platform template exceeded its fixed block count");
        }
        return List.copyOf(result);
    }

    private static Set<ChunkPos> chunks(StationGridCell cell) {
        HashSet<ChunkPos> result = new HashSet<>();
        for (BlockPos position : positions(cell)) {
            result.add(new ChunkPos(position));
        }
        if (result.size() > 4) {
            throw new IllegalStateException("Station platform exceeded its fixed chunk budget");
        }
        return Set.copyOf(result);
    }

    private static BlockState template(StationGridCell cell, BlockPos position) {
        int x = position.getX() - cell.centerX();
        int z = position.getZ() - cell.centerZ();
        if (x == 0 && z == 0) {
            return Blocks.SEA_LANTERN.defaultBlockState();
        }
        if ((Math.abs(x) == StationLimits.PLATFORM_RADIUS && z == 0)
                || (Math.abs(z) == StationLimits.PLATFORM_RADIUS && x == 0)) {
            return Blocks.YELLOW_CONCRETE.defaultBlockState();
        }
        return Blocks.SMOOTH_STONE.defaultBlockState();
    }
}
