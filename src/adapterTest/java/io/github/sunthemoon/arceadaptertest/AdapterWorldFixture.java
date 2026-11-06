package io.github.sunthemoon.arceadaptertest;

import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.GameTestInfo;
import net.minecraft.gametest.framework.GameTestListener;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/** Bounded terrain isolation and terminal restoration of cells outside the GameTest template. */
final class AdapterWorldFixture implements GameTestListener {
    private static final int FLAGS = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE;
    private final GameTestHelper helper;
    private final ServerLevel level;
    private final Map<BlockPos, Cell> before = new LinkedHashMap<>();
    private boolean closed;

    AdapterWorldFixture(GameTestHelper helper) {
        this.helper = helper;
        level = helper.getLevel();
        try {
            var field = GameTestHelper.class.getDeclaredField("testInfo");
            field.setAccessible(true);
            ((GameTestInfo) field.get(helper)).addListener(this);
        } catch (ReflectiveOperationException failure) {
            throw new IllegalStateException("Adapter fixture terminal listener unavailable", failure);
        }
    }

    void isolateTerrain() {
        // All 343 cells are inside rocket_test's 16-cube allocation. Complete the shell
        // before clearing the interior; leave an air gap around every rocket member.
        for (BlockPos at : BlockPos.betweenClosed(0, 0, 0, 6, 6, 6)) {
            if (shell(at)) { level.setBlock(helper.absolutePos(at), Blocks.GLASS.defaultBlockState(), FLAGS); }
        }
        for (BlockPos at : BlockPos.betweenClosed(1, 1, 1, 5, 5, 5)) {
            level.setBlock(helper.absolutePos(at), Blocks.AIR.defaultBlockState(), FLAGS);
        }
    }

    void setOutside(BlockPos at, BlockState state) {
        helper.assertTrue(level.hasChunkAt(at) && !level.isOutsideBuildHeight(at),
                "Adapter fixture needs a loaded cell at " + at);
        before.computeIfAbsent(at.immutable(), key -> {
            var entity = level.getBlockEntity(key);
            return new Cell(level.getBlockState(key), entity == null ? null : entity.saveWithFullMetadata());
        });
        // Preserve any pre-existing inventory in the snapshot instead of invoking its removal drops.
        level.removeBlockEntity(at);
        level.setBlock(at, state, FLAGS);
    }

    static void assertTerrainIntact(GameTestHelper helper) {
        for (BlockPos at : BlockPos.betweenClosed(0, 0, 0, 6, 6, 6)) {
            if (shell(at)) {
                helper.assertTrue(helper.getBlockState(at).is(Blocks.GLASS), "Adapter terrain enclosure opened at " + at);
            }
        }
    }

    private static boolean shell(BlockPos at) {
        return at.getX() == 0 || at.getX() == 6 || at.getY() == 0 || at.getY() == 6
                || at.getZ() == 0 || at.getZ() == 6;
    }

    private void close(Throwable primary) {
        if (closed) { return; }
        closed = true;
        Throwable first = primary;
        for (var entry : before.entrySet()) {
            try {
                BlockPos at = entry.getKey();
                Cell cell = entry.getValue();
                level.setBlock(at, cell.state(), FLAGS);
                if (cell.entity() != null) {
                    var restored = level.getBlockEntity(at);
                    helper.assertTrue(restored != null, "Adapter fixture lost original BlockEntity at " + at);
                    restored.load(cell.entity().copy());
                    restored.setChanged();
                }
                var entity = level.getBlockEntity(at);
                helper.assertTrue(level.getBlockState(at) == cell.state()
                                && (cell.entity() == null ? entity == null
                                : cell.entity().equals(entity.saveWithFullMetadata())),
                        "Adapter fixture did not restore outside cell at " + at);
            } catch (RuntimeException | Error failure) {
                if (first == null) { first = failure; }
                else if (first != failure) { first.addSuppressed(failure); }
            }
        }
        if (primary == null) {
            if (first instanceof RuntimeException failure) { throw failure; }
            if (first instanceof Error failure) { throw failure; }
        }
    }

    private record Cell(BlockState state, CompoundTag entity) { }
    @Override public void testStructureLoaded(GameTestInfo info) { }
    @Override public void testPassed(GameTestInfo info) { close(null); }
    @Override public void testFailed(GameTestInfo info) { close(info.getError()); }
}
