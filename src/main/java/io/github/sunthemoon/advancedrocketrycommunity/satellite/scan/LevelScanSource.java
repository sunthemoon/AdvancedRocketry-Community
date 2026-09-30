package io.github.sunthemoon.advancedrocketrycommunity.satellite.scan;

import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraftforge.common.Tags;

/**
 * Reads only chunks the server already holds ({@code getChunkNow}); it never loads, generates or tickets a
 * chunk. Created per tick, so a chunk unloaded between ticks is seen as unloaded.
 */
final class LevelScanSource implements ScanColumnSource {
    private final ServerLevel level;
    private final BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
    private LevelChunk chunk;
    private int chunkX = Integer.MIN_VALUE;
    private int chunkZ = Integer.MIN_VALUE;

    LevelScanSource(ServerLevel level) {
        this.level = level;
    }

    @Override
    public int classify(int x, int y, int z) {
        LevelChunk loaded = chunk(x, z);
        if (loaded == null) {
            return UNLOADED;
        }
        BlockState state = loaded.getBlockState(cursor.set(x, y, z));
        if (state.isAir()) {
            return AIR;
        }
        return state.is(Tags.Blocks.ORES) ? ORE : SOLID;
    }

    @Override
    public Optional<ResourceLocation> biome(int x, int y, int z) {
        LevelChunk loaded = chunk(x, z);
        return loaded == null ? Optional.empty()
                : loaded.getNoiseBiome(x >> 2, y >> 2, z >> 2).unwrapKey().map(ResourceKey::location);
    }

    private LevelChunk chunk(int x, int z) {
        int cx = x >> 4;
        int cz = z >> 4;
        if (cx != chunkX || cz != chunkZ) {
            chunk = level.getChunkSource().getChunkNow(cx, cz);
            chunkX = cx;
            chunkZ = cz;
        }
        return chunk;
    }
}
