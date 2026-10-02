package io.github.sunthemoon.advancedrocketrycommunity.celestial.surface.worldgen;

import net.minecraft.core.BlockPos;
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
 * The single piece of a volcano. The cone is built from the column's filler block (basalt on Venus) on top of the
 * ground; the crater holds a one-block lava pool at a flat level below its rim, and a lava pipe of radius 2 runs from
 * {@link VolcanoShape#CORE_DEPTH} blocks below the base up to the pool. Every write stays inside the chunk being
 * generated.
 */
public final class VolcanoPiece extends StructurePiece {
    private final int baseX;
    private final int baseY;
    private final int baseZ;
    private final VolcanoShape shape;

    public VolcanoPiece(BlockPos base, VolcanoShape shape) {
        super(SurfaceWorldgen.VOLCANO_PIECE.get(), 0, box(base, shape));
        this.baseX = base.getX();
        this.baseY = base.getY();
        this.baseZ = base.getZ();
        this.shape = shape;
    }

    public VolcanoPiece(CompoundTag tag) {
        super(SurfaceWorldgen.VOLCANO_PIECE.get(), tag);
        PieceSchema.require(tag, "volcano");
        this.baseX = tag.getInt("x");
        this.baseY = tag.getInt("y");
        this.baseZ = tag.getInt("z");
        this.shape = new VolcanoShape(tag.getInt("radius"), tag.getInt("height"));
    }

    static BoundingBox box(BlockPos base, VolcanoShape shape) {
        // The cone stands on the ground of each column; on a slope its skirt reaches at most SKIRT below the base.
        return new BoundingBox(base.getX() - shape.radius(), base.getY() - VolcanoShape.SKIRT,
                base.getZ() - shape.radius(), base.getX() + shape.radius(), base.getY() + shape.height() + 1,
                base.getZ() + shape.radius());
    }

    public VolcanoShape shape() {
        return shape;
    }

    @Override
    protected void addAdditionalSaveData(StructurePieceSerializationContext context, CompoundTag tag) {
        PieceSchema.write(tag);
        tag.putInt("x", baseX);
        tag.putInt("y", baseY);
        tag.putInt("z", baseZ);
        tag.putInt("radius", shape.radius());
        tag.putInt("height", shape.height());
    }

    @Override
    public void postProcess(WorldGenLevel level, StructureManager structures, ChunkGenerator generator,
                            RandomSource random, BoundingBox chunkBox, ChunkPos chunkPos, BlockPos pivot) {
        BoundingBox area = PieceArea.overlap(boundingBox, chunkBox).orElse(null);
        if (area == null) {
            return;
        }
        BlockState lava = Blocks.LAVA.defaultBlockState();
        int poolLevel = baseY + shape.coneRise(0) + 1;
        BlockPos.MutableBlockPos position = new BlockPos.MutableBlockPos();
        for (int x = area.minX(); x <= area.maxX(); x++) {
            for (int z = area.minZ(); z <= area.maxZ(); z++) {
                double distance = Math.sqrt((double) (x - baseX) * (x - baseX) + (double) (z - baseZ) * (z - baseZ));
                int rise = shape.coneRise(distance);
                if (rise <= 0 && !shape.inCore(distance)) {
                    continue;
                }
                int ground = level.getHeight(Heightmap.Types.WORLD_SURFACE_WG, x, z) - 1;
                BlockState filler = level.getBlockState(position.set(x, ground - 1, z));
                if (filler.isAir() || !filler.getFluidState().isEmpty()) {
                    filler = Blocks.BASALT.defaultBlockState();
                }
                int coneTop = baseY + rise;
                for (int y = ground + 1; y <= coneTop; y++) {
                    set(level, chunkBox, position.set(x, y, z), filler);
                }
                if (shape.inCore(distance)) {
                    for (int y = baseY - VolcanoShape.CORE_DEPTH; y <= coneTop; y++) {
                        set(level, chunkBox, position.set(x, y, z), lava);
                    }
                }
                if (shape.inCrater(distance)) {
                    for (int y = coneTop + 1; y <= poolLevel; y++) {
                        set(level, chunkBox, position.set(x, y, z), lava);
                    }
                }
            }
        }
    }

    /** Writes only inside the chunk being generated and inside this piece's own box. */
    private void set(WorldGenLevel level, BoundingBox chunkBox, BlockPos position, BlockState state) {
        if (chunkBox.isInside(position) && boundingBox.isInside(position)) {
            level.setBlock(position, state, Block.UPDATE_CLIENTS);
        }
    }
}
