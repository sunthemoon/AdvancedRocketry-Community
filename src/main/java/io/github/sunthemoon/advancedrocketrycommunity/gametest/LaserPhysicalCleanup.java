package io.github.sunthemoon.advancedrocketrycommunity.gametest;

import io.github.sunthemoon.advancedrocketrycommunity.endgame.laser.LaserTargetBlockEntity;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.GameTestInfo;
import net.minecraft.gametest.framework.GameTestListener;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;

/** Resources owned by one asynchronous physical-laser test, including its off-template target. */
final class LaserPhysicalCleanup implements AutoCloseable, GameTestListener {
    final List<ServerPlayer> joined = new ArrayList<>();
    private final OrbitalLaserDrillGameTests.Fixture fixture;
    private final List<BlockPos> cells = new ArrayList<>();
    private ServerLevel targetLevel;
    private BlockPos marker;
    private boolean ownsForcedChunk;
    private boolean closed;

    LaserPhysicalCleanup(GameTestHelper helper, OrbitalLaserDrillGameTests.Fixture fixture) {
        this.fixture = fixture;
        attach(helper);
    }

    void attach(GameTestHelper helper) {
        try {
            var field = GameTestHelper.class.getDeclaredField("testInfo");
            field.setAccessible(true);
            ((GameTestInfo) field.get(helper)).addListener(this);
        } catch (ReflectiveOperationException failure) {
            fixture.close();
            throw new IllegalStateException("Could not attach physical laser cleanup", failure);
        }
    }

    void target(ServerLevel level, BlockPos position) {
        targetLevel = level;
        marker = position.immutable();
    }

    void layer(List<BlockPos> layer) { cells.addAll(layer); }

    void forceTargetChunk(ServerLevel level, BlockPos position) {
        ChunkPos chunk = new ChunkPos(position);
        // A level-31 forced ticket keeps its two-chunk fringe FULL (level 33), even when the target itself
        // is not forced. Never borrow or remove a template's or another fixture's loading footprint.
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                if (level.getForcedChunks().contains(ChunkPos.asLong(chunk.x + dx, chunk.z + dz))) {
                    throw new IllegalStateException("The laser target overlaps another forced chunk's FULL fringe");
                }
            }
        }
        if (level.getChunkSource().getChunkNow(chunk.x, chunk.z) != null) {
            throw new IllegalStateException("The laser test needs an initially unloaded target chunk");
        }
        target(level, position);
        ownsForcedChunk = targetLevel.setChunkForced(chunk.x, chunk.z, true);
    }

    void releaseTargetChunk() {
        if (ownsForcedChunk) {
            ChunkPos chunk = new ChunkPos(marker);
            targetLevel.setChunkForced(chunk.x, chunk.z, false);
            ownsForcedChunk = false;
        }
    }

    @Override public void close() { close(null); }

    private void close(Throwable primary) {
        if (closed) { return; }
        closed = true;
        Throwable failure = primary;
        for (ServerPlayer player : joined) {
            try {
                if (fixture.server.getPlayerList().getPlayer(player.getUUID()) == player) {
                    fixture.server.getPlayerList().remove(player);
                }
            } catch (RuntimeException | Error error) { failure = retain(failure, error); }
        }
        try {
            if (marker != null) {
                targetLevel.getChunkAt(marker); // Cleanup only, including a successfully unloaded target.
                if (targetLevel.getBlockEntity(marker) instanceof LaserTargetBlockEntity target) {
                    for (int slot = 0; slot < LaserTargetBlockEntity.BUFFER_SLOTS; slot++) {
                        target.buffer().setStackInSlot(slot, ItemStack.EMPTY);
                    }
                }
                targetLevel.setBlockAndUpdate(marker, Blocks.AIR.defaultBlockState());
                for (BlockPos cell : cells) { targetLevel.setBlockAndUpdate(cell, Blocks.AIR.defaultBlockState()); }
            }
        } catch (RuntimeException | Error error) { failure = retain(failure, error); }
        try { releaseTargetChunk(); }
        catch (RuntimeException | Error error) { failure = retain(failure, error); }
        try { fixture.close(); }
        catch (RuntimeException | Error error) { failure = retain(failure, error); }
        if (primary == null) {
            if (failure instanceof RuntimeException error) { throw error; }
            if (failure instanceof Error error) { throw error; }
        }
    }

    private static Throwable retain(Throwable first, Throwable next) {
        if (first == null) { return next; }
        if (first != next) { first.addSuppressed(next); }
        return first;
    }

    @Override public void testStructureLoaded(GameTestInfo info) { }
    @Override public void testPassed(GameTestInfo info) { close(); }
    @Override public void testFailed(GameTestInfo info) { close(info.getError()); }
}
