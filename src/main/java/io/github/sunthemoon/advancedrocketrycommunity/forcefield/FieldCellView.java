package io.github.sunthemoon.advancedrocketrycommunity.forcefield;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.SignalGetter;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;

/** One visit's loaded-only snapshot. Power callbacks cannot read beyond the stock 25-cell stencil. */
final class FieldCellView implements SignalGetter {
    static final int MAX_WORK = 27; // 25 power/source cells, one target and one mutation.
    private final LevelHeightAccessor height;
    private final Function<BlockPos, Cell> loadedReader;
    private final Consumer<BlockPos> readObserver;
    private final Map<BlockPos, Cell> cells = new LinkedHashMap<>();
    private boolean powerQuery;
    private boolean refusedPowerRead;
    private RuntimeException powerFailure;
    private int mutations;
    record Cell(BlockState state, BlockEntity entity) { }
    private static final class Unavailable extends RuntimeException {
        private Unavailable() { super(null, null, false, false); }
    }
    FieldCellView(ServerLevel level, Consumer<BlockPos> readObserver) {
        this(level, pos -> {
            if (!level.isInWorldBounds(pos)) { throw new Unavailable(); }
            var chunk = level.getChunkSource().getChunkNow(pos.getX() >> 4, pos.getZ() >> 4);
            if (chunk == null) { throw new Unavailable(); }
            return new Cell(chunk.getBlockState(pos), chunk.getBlockEntity(pos));
        }, readObserver);
    }
    FieldCellView(LevelHeightAccessor height, Function<BlockPos, Cell> loadedReader, Consumer<BlockPos> readObserver) {
        this.height = height;
        this.loadedReader = loadedReader;
        this.readObserver = readObserver;
    }
    static List<BlockPos> powerPositions(BlockPos source) {
        var positions = new ArrayList<BlockPos>(25);
        for (int x = -2; x <= 2; x++) {
            for (int y = -2; y <= 2; y++) {
                for (int z = -2; z <= 2; z++) {
                    if (Math.abs(x) + Math.abs(y) + Math.abs(z) <= 2) { positions.add(source.offset(x, y, z)); }
                }
            }
        }
        return List.copyOf(positions);
    }
    private Cell cell(BlockPos pos) {
        // Match Level's height boundary without consulting a chunk or charging a world read.
        if (height.isOutsideBuildHeight(pos)) { return new Cell(Blocks.VOID_AIR.defaultBlockState(), null); }
        var old = cells.get(pos);
        if (old != null) { return old; }
        if (powerQuery || cells.size() >= 26) {
            if (powerQuery) { refusedPowerRead = true; }
            throw new Unavailable();
        }
        var value = loadedReader.apply(pos);
        BlockPos key = pos.immutable();
        cells.put(key, value);
        if (readObserver != null) { readObserver.accept(key); }
        return value;
    }
    boolean available(BlockPos pos) {
        try { cell(pos); return true; } catch (Unavailable missing) { return false; }
    }
    Optional<Boolean> powered(BlockPos source) {
        refusedPowerRead = false;
        powerFailure = null;
        try {
            for (BlockPos pos : powerPositions(source)) { cell(pos); }
            powerQuery = true;
            boolean powered = hasNeighborSignal(source);
            // A callback can catch Unavailable itself; its result still cannot authorize an effect.
            if (refusedPowerRead) { powerFailure = new Unavailable(); return Optional.empty(); }
            return Optional.of(powered);
        } catch (Unavailable missing) {
            if (powerQuery) { powerFailure = missing; }
            return Optional.empty();
        } catch (RuntimeException failure) { powerFailure = failure; return Optional.empty(); }
        finally { powerQuery = false; }
    }
    RuntimeException powerFailure() { return powerFailure; }
    void mutated() { mutations++; }
    int inspectedAndMutatedCells() { return cells.size() + mutations; }
    @Override public BlockState getBlockState(BlockPos pos) { return cell(pos).state(); }
    @Override public BlockEntity getBlockEntity(BlockPos pos) { return cell(pos).entity(); }
    @Override public FluidState getFluidState(BlockPos pos) { return getBlockState(pos).getFluidState(); }
    @Override public int getHeight() { return height.getHeight(); }
    @Override public int getMinBuildHeight() { return height.getMinBuildHeight(); }
}
