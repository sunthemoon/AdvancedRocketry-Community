package io.github.sunthemoon.advancedrocketrycommunity.celestial.surface.worldgen;

import io.github.sunthemoon.advancedrocketrycommunity.celestial.surface.SurfaceContent;
import java.util.Comparator;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;

/**
 * The single piece of a geode: a lens-shaped hollow lined with geode shell, with ore clusters from the
 * {@code geode_ores} block tag (at most {@link #MAX_ORES} kinds, in registry-name order) on the roof and the floor.
 * No geode block comes within {@link GeodeStructure#COVER} blocks of the ground of its column, so a geode never opens
 * to the surface under low ground. Every write stays inside the chunk being generated.
 */
public final class GeodePiece extends StructurePiece {
    public static final int MAX_ORES = 32;

    private final int centreX;
    private final int centreY;
    private final int centreZ;
    private final GeodeShape shape;
    private final long salt;

    public GeodePiece(BlockPos centre, GeodeShape shape, long salt) {
        super(SurfaceWorldgen.GEODE_PIECE.get(), 0, box(centre, shape));
        this.centreX = centre.getX();
        this.centreY = centre.getY();
        this.centreZ = centre.getZ();
        this.shape = shape;
        this.salt = salt;
    }

    public GeodePiece(CompoundTag tag) {
        super(SurfaceWorldgen.GEODE_PIECE.get(), tag);
        PieceSchema.require(tag, "geode");
        this.centreX = tag.getInt("x");
        this.centreY = tag.getInt("y");
        this.centreZ = tag.getInt("z");
        this.shape = new GeodeShape(tag.getInt("radius"));
        this.salt = tag.getLong("salt");
    }

    static BoundingBox box(BlockPos centre, GeodeShape shape) {
        int reach = shape.verticalReach();
        return new BoundingBox(centre.getX() - shape.radius(), centre.getY() - reach, centre.getZ() - shape.radius(),
                centre.getX() + shape.radius(), centre.getY() + reach, centre.getZ() + shape.radius());
    }

    public BlockPos centre() {
        return new BlockPos(centreX, centreY, centreZ);
    }

    public long salt() {
        return salt;
    }

    public GeodeShape shape() {
        return shape;
    }

    @Override
    protected void addAdditionalSaveData(StructurePieceSerializationContext context, CompoundTag tag) {
        PieceSchema.write(tag);
        tag.putInt("x", centreX);
        tag.putInt("y", centreY);
        tag.putInt("z", centreZ);
        tag.putInt("radius", shape.radius());
        tag.putLong("salt", salt);
    }

    @Override
    public void postProcess(WorldGenLevel level, StructureManager structures, ChunkGenerator generator,
                            RandomSource random, BoundingBox chunkBox, ChunkPos chunkPos, BlockPos pivot) {
        BoundingBox area = PieceArea.overlap(boundingBox, chunkBox).orElse(null);
        if (area == null) {
            return;
        }
        List<BlockState> ores = ores(level);
        BlockState shell = SurfaceContent.GEODE_SHELL.get().defaultBlockState();
        BlockState air = Blocks.CAVE_AIR.defaultBlockState();
        BlockPos.MutableBlockPos position = new BlockPos.MutableBlockPos();
        for (int x = area.minX(); x <= area.maxX(); x++) {
            for (int z = area.minZ(); z <= area.maxZ(); z++) {
                int dx = x - centreX;
                int dz = z - centreZ;
                int half = shape.halfHeight(dx, dz);
                if (half < 0) {
                    continue;
                }
                // The start keeps COVER blocks of rock above the centre column; where the ground of another column
                // lies lower, the roof comes down to stay COVER blocks under that column's own ground.
                int ground = level.getHeight(Heightmap.Types.WORLD_SURFACE_WG, x, z) - 1;
                int top = Math.min(centreY + half, ground - GeodeStructure.COVER);
                int bottom = centreY - half;
                if (top < bottom) {
                    continue;
                }
                for (int y = bottom + 1; y < top; y++) {
                    set(level, chunkBox, position.set(x, y, z), air);
                }
                set(level, chunkBox, position.set(x, top, z), shell);
                set(level, chunkBox, position.set(x, bottom, z), shell);
                if (ores.isEmpty()) {
                    continue;
                }
                BlockState ore = ores.get(Math.floorMod(Math.floorDiv(dx, 4) + Math.floorDiv(dz, 4), ores.size()));
                int length = shape.clusterLength(dx, dz, salt);
                for (int i = 1; i <= length; i++) {
                    if (GeodeShape.roofCluster(dx, dz) && top - i > bottom) {
                        set(level, chunkBox, position.set(x, top - i, z), ore);
                    }
                    if (GeodeShape.floorCluster(dx, dz) && bottom + i < top) {
                        set(level, chunkBox, position.set(x, bottom + i, z), ore);
                    }
                }
            }
        }
    }

    private static List<BlockState> ores(WorldGenLevel level) {
        return level.registryAccess().registryOrThrow(Registries.BLOCK).getTag(SurfaceWorldgen.GEODE_ORES)
                .map(tag -> tag.stream()
                        .sorted(Comparator.comparing(holder -> holder.unwrapKey().orElseThrow().location()))
                        .limit(MAX_ORES).map(Holder::value).map(Block::defaultBlockState).toList())
                .orElse(List.of());
    }

    /** Writes only inside the chunk being generated and inside this piece's own box. */
    private void set(WorldGenLevel level, BoundingBox chunkBox, BlockPos position, BlockState state) {
        if (chunkBox.isInside(position) && boundingBox.isInside(position)) {
            level.setBlock(position, state, Block.UPDATE_CLIENTS);
        }
    }
}
