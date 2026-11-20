package io.github.sunthemoon.advancedrocketrycommunity.gametest;

import java.lang.reflect.Field;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.GameTestInfo;
import net.minecraft.gametest.framework.GameTestListener;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;

/** W416 fixtures: replacement from current native BE tags, and cleanup even on a timeout. */
final class RestartTestSupport {
    private RestartTestSupport() { }

    static BlockEntity replace(ServerLevel level, BlockPos pos, CompoundTag saved) {
        level.removeBlockEntity(pos);
        BlockEntity restored = BlockEntity.loadStatic(pos, level.getBlockState(pos), saved.copy());
        if (restored == null) { throw new IllegalStateException("Saved block entity did not decode at " + pos); }
        level.setBlockEntity(restored);
        return restored;
    }

    static void cleanup(GameTestHelper helper, Runnable action) {
        try {
            Field field = GameTestHelper.class.getDeclaredField("testInfo");
            field.setAccessible(true);
            ((GameTestInfo) field.get(helper)).addListener(new GameTestListener() {
                private boolean closed;
                private void close() { if (!closed) { closed = true; action.run(); } }
                @Override public void testStructureLoaded(GameTestInfo info) { }
                @Override public void testPassed(GameTestInfo info) { close(); }
                @Override public void testFailed(GameTestInfo info) { close(); }
            });
        } catch (ReflectiveOperationException failure) {
            action.run();
            throw new IllegalStateException("Cannot register restart fixture cleanup", failure);
        }
    }
}
