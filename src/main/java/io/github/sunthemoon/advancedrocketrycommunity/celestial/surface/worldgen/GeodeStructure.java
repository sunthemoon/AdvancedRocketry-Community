package io.github.sunthemoon.advancedrocketrycommunity.celestial.surface.worldgen;

import com.mojang.serialization.Codec;
import io.github.sunthemoon.advancedrocketrycommunity.config.WorldgenSwitches;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.WorldgenRandom;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureType;

/**
 * An ore geode (ADR-063 section 5): one piece below the lowest ground over its lens, radius 16–24, its ores read from
 * the block tag {@code advancedrocketrycommunity:geode_ores} when it is placed. A disabled server switch returns no
 * generation point; starts already saved still finish.
 */
public final class GeodeStructure extends Structure {
    public static final Codec<GeodeStructure> CODEC = simpleCodec(GeodeStructure::new);
    /** Blocks of cover kept between the geode's roof and the ground. */
    public static final int COVER = 4;
    /** Ground samples on each side of the middle, out to the radius: a 5 x 5 grid. */
    private static final int GRID = 2;

    public GeodeStructure(StructureSettings settings) {
        super(settings);
    }

    @Override
    protected Optional<GenerationStub> findGenerationPoint(GenerationContext context) {
        if (!WorldgenSwitches.enabled(WorldgenSwitches.GEODES)) {
            return Optional.empty();
        }
        WorldgenRandom random = context.random();
        ChunkPos chunk = context.chunkPos();
        int x = chunk.getMiddleBlockX();
        int z = chunk.getMiddleBlockZ();
        GeodeShape shape = new GeodeShape(GeodeShape.MIN_RADIUS
                + random.nextInt(GeodeShape.MAX_RADIUS - GeodeShape.MIN_RADIUS + 1));
        // The lowest ground over the lens, on a 5 x 5 grid (C15bR1-H1): Venus has cliffs, and one sample at the
        // middle could put the lens above the ground of its other columns. Between the samples, each column's roof
        // also comes down to stay COVER blocks under that column's own ground (GeodePiece).
        int surface = Integer.MAX_VALUE;
        for (int i = -GRID; i <= GRID; i++) {
            for (int j = -GRID; j <= GRID; j++) {
                surface = Math.min(surface, context.chunkGenerator().getFirstOccupiedHeight(
                        x + i * shape.radius() / GRID, z + j * shape.radius() / GRID, Heightmap.Types.WORLD_SURFACE_WG,
                        context.heightAccessor(), context.randomState()));
            }
        }
        int centreY = surface - COVER - shape.verticalReach();
        if (centreY - shape.verticalReach() < context.heightAccessor().getMinBuildHeight() + 5) {
            return Optional.empty();
        }
        long salt = random.nextLong();
        BlockPos centre = new BlockPos(x, centreY, z);
        return Optional.of(new GenerationStub(centre, builder -> builder.addPiece(
                new GeodePiece(centre, shape, salt))));
    }

    @Override
    public StructureType<?> type() {
        return SurfaceWorldgen.GEODE.get();
    }
}
