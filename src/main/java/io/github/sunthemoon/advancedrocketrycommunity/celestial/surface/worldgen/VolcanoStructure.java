package io.github.sunthemoon.advancedrocketrycommunity.celestial.surface.worldgen;

import com.mojang.serialization.Codec;
import io.github.sunthemoon.advancedrocketrycommunity.config.WorldgenSwitches;
import java.util.Optional;
import java.util.Random;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.WorldgenRandom;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureType;

/**
 * A Venus volcano (ADR-063 section 5): one piece holding the cone, its crater and the lava core. A disabled server
 * switch returns no generation point; starts already saved still finish.
 */
public final class VolcanoStructure extends Structure {
    public static final Codec<VolcanoStructure> CODEC = simpleCodec(VolcanoStructure::new);

    public VolcanoStructure(StructureSettings settings) {
        super(settings);
    }

    @Override
    protected Optional<GenerationStub> findGenerationPoint(GenerationContext context) {
        if (!WorldgenSwitches.enabled(WorldgenSwitches.VOLCANOES)) {
            return Optional.empty();
        }
        WorldgenRandom random = context.random();
        ChunkPos chunk = context.chunkPos();
        int x = chunk.getMiddleBlockX();
        int z = chunk.getMiddleBlockZ();
        int surface = context.chunkGenerator().getFirstOccupiedHeight(x, z, Heightmap.Types.WORLD_SURFACE_WG,
                context.heightAccessor(), context.randomState());
        VolcanoShape shape = VolcanoShape.random(new Random(random.nextLong()));
        int bottom = context.heightAccessor().getMinBuildHeight() + 5;
        int top = context.heightAccessor().getMaxBuildHeight() - 1;
        if (surface - VolcanoShape.SKIRT < bottom || surface + shape.height() + 1 > top
                || overhangs(context, x, z, surface, shape)) {
            return Optional.empty();
        }
        BlockPos base = new BlockPos(x, surface, z);
        return Optional.of(new GenerationStub(base, builder -> builder.addPiece(new VolcanoPiece(base, shape))));
    }

    /**
     * Whether any column of the cone has its ground more than the skirt below the base, where the cone would hang over
     * a cliff (C15bR1-L1); such a start is refused. Every column is sampled (at most about 3,200, once per 16 x 16
     * chunks): the Venus cliffs are vertical, so a lattice could miss a drop between its points.
     */
    public static boolean overhangs(GenerationContext context, int x, int z, int surface, VolcanoShape shape) {
        int radius = shape.radius();
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                if (dx * dx + dz * dz < radius * radius
                        && context.chunkGenerator().getFirstOccupiedHeight(x + dx, z + dz,
                        Heightmap.Types.WORLD_SURFACE_WG, context.heightAccessor(), context.randomState())
                        < surface - VolcanoShape.SKIRT) {
                    return true;
                }
            }
        }
        return false;
    }

    @Override
    public StructureType<?> type() {
        return SurfaceWorldgen.VOLCANO.get();
    }
}
