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
 * The single piece of a crater. In each chunk it is asked to place, it digs the bowl down from the ground (never
 * below the floor limit) and lays the rim on the ground outside the edge (never above the rim limit), reusing the
 * column's own top and filler blocks. Every write stays inside the chunk being generated.
 */
public final class CraterPiece extends StructurePiece {
    private final int centreX;
    private final int centreZ;
    private final int baseY;
    private final CraterShape shape;
    private final int floorMinY;
    private final int rimMaxY;

    public CraterPiece(BlockPos centre, CraterShape shape, int floorMinY, int rimMaxY) {
        super(SurfaceWorldgen.CRATER_PIECE.get(), 0, box(centre, shape, floorMinY, rimMaxY));
        this.centreX = centre.getX();
        this.centreZ = centre.getZ();
        this.baseY = centre.getY();
        this.shape = shape;
        this.floorMinY = floorMinY;
        this.rimMaxY = rimMaxY;
    }

    public CraterPiece(CompoundTag tag) {
        super(SurfaceWorldgen.CRATER_PIECE.get(), tag);
        PieceSchema.require(tag, "crater");
        this.centreX = tag.getInt("x");
        this.centreZ = tag.getInt("z");
        this.baseY = tag.getInt("y");
        this.shape = new CraterShape(tag.getInt("radius"), tag.getInt("depth"), tag.getInt("rim"),
                tag.getIntArray("bulges"));
        this.floorMinY = tag.getInt("floor_min_y");
        this.rimMaxY = tag.getInt("rim_max_y");
    }

    static BoundingBox box(BlockPos centre, CraterShape shape, int floorMinY, int rimMaxY) {
        int reach = shape.reach();
        int bottom = Math.max(floorMinY, centre.getY() - shape.depth());
        return new BoundingBox(centre.getX() - reach, bottom, centre.getZ() - reach,
                centre.getX() + reach, rimMaxY, centre.getZ() + reach);
    }

    public BlockPos centre() {
        return new BlockPos(centreX, baseY, centreZ);
    }

    public int floorMinY() {
        return floorMinY;
    }

    public int rimMaxY() {
        return rimMaxY;
    }

    public CraterShape shape() {
        return shape;
    }

    @Override
    protected void addAdditionalSaveData(StructurePieceSerializationContext context, CompoundTag tag) {
        PieceSchema.write(tag);
        tag.putInt("x", centreX);
        tag.putInt("z", centreZ);
        tag.putInt("y", baseY);
        tag.putInt("radius", shape.radius());
        tag.putInt("depth", shape.depth());
        tag.putInt("rim", shape.rimHeight());
        tag.putIntArray("bulges", shape.bulges());
        tag.putInt("floor_min_y", floorMinY);
        tag.putInt("rim_max_y", rimMaxY);
    }

    @Override
    public void postProcess(WorldGenLevel level, StructureManager structures, ChunkGenerator generator,
                            RandomSource random, BoundingBox chunkBox, ChunkPos chunkPos, BlockPos pivot) {
        BoundingBox area = PieceArea.overlap(boundingBox, chunkBox).orElse(null);
        if (area == null) {
            return;
        }
        BlockPos.MutableBlockPos position = new BlockPos.MutableBlockPos();
        for (int x = area.minX(); x <= area.maxX(); x++) {
            for (int z = area.minZ(); z <= area.maxZ(); z++) {
                int top = level.getHeight(Heightmap.Types.WORLD_SURFACE_WG, x, z) - 1;
                if (top <= level.getMinBuildHeight()) {
                    continue;
                }
                BlockState topState = level.getBlockState(position.set(x, top, z));
                BlockState filler = level.getBlockState(position.set(x, top - 1, z));
                if (filler.isAir()) {
                    filler = topState;
                }
                int dx = x - centreX;
                int dz = z - centreZ;
                if (shape.inBowl(dx, dz)) {
                    int floor = Math.max(floorMinY, baseY - shape.bowlDepth(dx, dz));
                    if (floor >= top) {
                        continue;
                    }
                    for (int y = floor + 1; y <= top; y++) {
                        set(level, chunkBox, position.set(x, y, z), Blocks.AIR.defaultBlockState());
                    }
                    set(level, chunkBox, position.set(x, floor, z), topState);
                } else {
                    int ceiling = Math.min(rimMaxY, top + shape.rimRise(dx, dz));
                    if (ceiling <= top) {
                        continue;
                    }
                    set(level, chunkBox, position.set(x, top, z), filler);
                    for (int y = top + 1; y <= ceiling; y++) {
                        set(level, chunkBox, position.set(x, y, z), y == ceiling ? topState : filler);
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
