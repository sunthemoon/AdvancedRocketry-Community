package io.github.sunthemoon.advancedrocketrycommunity.celestial.surface.worldgen;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
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
 * An impact crater (ADR-063 section 5): one piece whose bounding box covers the bowl and the raised rim. The bowl
 * floor never goes below {@code floor_min_y} and the rim never above {@code rim_max_y}; a disabled server switch
 * returns no generation point, and starts already saved still finish.
 */
public final class CraterStructure extends Structure {
    public static final Codec<CraterStructure> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            settingsCodec(instance),
            Codec.intRange(CraterShape.MIN_RADIUS, CraterShape.MAX_RADIUS).fieldOf("min_radius")
                    .forGetter(structure -> structure.minRadius),
            Codec.intRange(CraterShape.MIN_RADIUS, CraterShape.MAX_RADIUS).fieldOf("max_radius")
                    .forGetter(structure -> structure.maxRadius),
            Codec.intRange(-64, 320).fieldOf("floor_min_y").forGetter(structure -> structure.floorMinY),
            Codec.intRange(-64, 320).fieldOf("rim_max_y").forGetter(structure -> structure.rimMaxY)
    ).apply(instance, CraterStructure::new));

    private final int minRadius;
    private final int maxRadius;
    private final int floorMinY;
    private final int rimMaxY;

    public CraterStructure(StructureSettings settings, int minRadius, int maxRadius, int floorMinY, int rimMaxY) {
        super(settings);
        if (minRadius > maxRadius || floorMinY >= rimMaxY) {
            throw new IllegalArgumentException("crater ranges are inverted");
        }
        this.minRadius = minRadius;
        this.maxRadius = maxRadius;
        this.floorMinY = floorMinY;
        this.rimMaxY = rimMaxY;
    }

    @Override
    protected Optional<GenerationStub> findGenerationPoint(GenerationContext context) {
        if (!WorldgenSwitches.enabled(WorldgenSwitches.CRATERS)) {
            return Optional.empty();
        }
        WorldgenRandom random = context.random();
        ChunkPos chunk = context.chunkPos();
        int x = chunk.getBlockX(random.nextInt(16));
        int z = chunk.getBlockZ(random.nextInt(16));
        int surface = context.chunkGenerator().getFirstOccupiedHeight(x, z, Heightmap.Types.WORLD_SURFACE_WG,
                context.heightAccessor(), context.randomState());
        if (surface <= floorMinY || surface >= rimMaxY) {
            return Optional.empty();
        }
        CraterShape shape = CraterShape.random(new Random(random.nextLong()), minRadius, maxRadius);
        BlockPos centre = new BlockPos(x, surface, z);
        return Optional.of(new GenerationStub(centre, builder -> builder.addPiece(
                new CraterPiece(centre, shape, floorMinY, rimMaxY))));
    }

    @Override
    public StructureType<?> type() {
        return SurfaceWorldgen.CRATER.get();
    }
}
